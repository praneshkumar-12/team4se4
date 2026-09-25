import { applyDecorators } from "@nestjs/common";
import { ValidateBy } from "class-validator";

function mustContain(name: string, pattern: RegExp, message: string): PropertyDecorator {
  return ValidateBy({
    name,
    validator: {
      validate: (value: unknown) => typeof value === "string" && pattern.test(value),
      defaultMessage: () => message,
    },
  });
}

/**
 * Character-class rules for a new password. Each rule is registered under its
 * own constraint name: class-validator keeps one result per name, so reusing
 * @Matches for every rule would report only one of the failures.
 */
export const PasswordComplexity = (): PropertyDecorator =>
  applyDecorators(
    mustContain("hasUppercase", /[A-Z]/, "password must contain at least one uppercase letter"),
    mustContain("hasLowercase", /[a-z]/, "password must contain at least one lowercase letter"),
    mustContain("hasDigit", /\d/, "password must contain at least one digit"),
    mustContain("hasSymbol", /[^A-Za-z0-9\s]/, "password must contain at least one symbol"),
  );
