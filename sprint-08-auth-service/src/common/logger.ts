import { ConsoleLogger, LoggerService } from "@nestjs/common";

/**
 * Keys redacted at any depth, regardless of casing. This is the structural
 * defence the ticket asks for: not a search for a log call that names a
 * credential field (which catches the direct case and none of the
 * indirect ones - an error object serialised whole, a DTO printed in a
 * stack trace), but a redaction pass every value goes through on the way
 * out, however it got there.
 */
const REDACTED_KEYS = new Set(["password", "passwordhash", "accesstoken", "refreshtoken", "authorization", "token"]);

const REDACTED = "[REDACTED]";

export function redact(value: unknown, seen = new WeakSet<object>()): unknown {
  if (Array.isArray(value)) {
    return value.map((item) => redact(item, seen));
  }

  if (value instanceof Error) {
    // Serialising an Error whole is exactly the indirect route the ticket
    // names, so its own enumerable properties (which can carry a request
    // body a framework attached) get the same treatment as a plain object.
    return redact({ ...value, name: value.name, message: value.message }, seen);
  }

  if (value !== null && typeof value === "object") {
    if (seen.has(value)) {
      return "[CIRCULAR]";
    }
    seen.add(value);

    const output: Record<string, unknown> = {};
    for (const [key, val] of Object.entries(value)) {
      output[key] = REDACTED_KEYS.has(key.toLowerCase()) ? REDACTED : redact(val, seen);
    }
    return output;
  }

  return value;
}

/**
 * The one logger every part of this service is expected to log through.
 * No caller ever hands it a whole request body - only named, already-safe
 * fields (e.g. a username) - but the redaction pass exists anyway, because
 * "no caller ever does X" is a rule about today's code, not a guarantee
 * about tomorrow's.
 */
export class RedactingLogger extends ConsoleLogger implements LoggerService {
  log(message: unknown, ...optionalParams: unknown[]): void {
    super.log(redact(message), ...optionalParams.map((p) => redact(p)));
  }

  error(message: unknown, ...optionalParams: unknown[]): void {
    super.error(redact(message), ...optionalParams.map((p) => redact(p)));
  }

  warn(message: unknown, ...optionalParams: unknown[]): void {
    super.warn(redact(message), ...optionalParams.map((p) => redact(p)));
  }
}
