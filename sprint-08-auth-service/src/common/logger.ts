import { ConsoleLogger, LoggerService } from "@nestjs/common";

/**
 * Keys redacted at any depth, case-insensitively. Redaction is applied to
 * every logged value rather than by auditing call sites, so credentials
 * are also caught inside serialised errors and DTOs.
 */
const REDACTED_KEYS = new Set(["password", "passwordhash", "accesstoken", "refreshtoken", "authorization", "token"]);

const REDACTED = "[REDACTED]";

export function redact(value: unknown, seen = new WeakSet<object>()): unknown {
  if (Array.isArray(value)) {
    return value.map((item) => redact(item, seen));
  }

  if (value instanceof Error) {
    // Enumerable properties may carry request data attached by the framework.
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

/** Application logger; redacts sensitive keys from all output as a safeguard against future misuse. */
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
