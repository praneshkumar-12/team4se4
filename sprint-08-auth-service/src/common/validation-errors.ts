import type { ValidationError } from "class-validator";

/** Flattens class-validator errors into one list of messages, so a client sees every failure at once. */
export function toValidationMessages(errors: ValidationError[]): string[] {
  return errors.flatMap((error) => [
    ...Object.values(error.constraints ?? {}),
    ...toValidationMessages(error.children ?? []),
  ]);
}
