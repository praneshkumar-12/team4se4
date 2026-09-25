import "reflect-metadata";
import { plainToInstance } from "class-transformer";
import { validate } from "class-validator";
import { RegisterDto } from "./register.dto";
import { toValidationMessages } from "../../common/validation-errors";

async function passwordErrors(password: string): Promise<string[]> {
  const dto = plainToInstance(RegisterDto, { username: "priya.menon", password, accountId: 1 });
  return toValidationMessages(await validate(dto));
}

describe("RegisterDto password rules", () => {
  it("accepts a password meeting every rule", async () => {
    expect(await passwordErrors("Correct-Horse-Battery-9")).toEqual([]);
  });

  it("rejects a password with no uppercase letter", async () => {
    expect(await passwordErrors("correct-horse-battery-9")).toEqual(["password must contain at least one uppercase letter"]);
  });

  it("rejects a password with no lowercase letter", async () => {
    expect(await passwordErrors("CORRECT-HORSE-BATTERY-9")).toEqual(["password must contain at least one lowercase letter"]);
  });

  it("rejects a password with no digit", async () => {
    expect(await passwordErrors("Correct-Horse-Battery-x")).toEqual(["password must contain at least one digit"]);
  });

  it("rejects a password with no symbol", async () => {
    expect(await passwordErrors("CorrectHorseBattery9")).toEqual(["password must contain at least one symbol"]);
  });

  it("does not count whitespace as a symbol", async () => {
    expect(await passwordErrors("Correct Horse Battery 9")).toEqual(["password must contain at least one symbol"]);
  });

  it("rejects a password shorter than 12 characters", async () => {
    expect(await passwordErrors("Ab-1")).toEqual(["password must be longer than or equal to 12 characters"]);
  });

  it("reports every failed rule together, not one at a time", async () => {
    const messages = await passwordErrors("short");
    expect(messages).toHaveLength(4);
    expect(messages).toEqual(
      expect.arrayContaining([
        "password must be longer than or equal to 12 characters",
        "password must contain at least one uppercase letter",
        "password must contain at least one digit",
        "password must contain at least one symbol",
      ]),
    );
  });

  it("reports failures from several fields together", async () => {
    const dto = plainToInstance(RegisterDto, { username: "a", password: "short", accountId: 0 });
    const messages = toValidationMessages(await validate(dto));
    expect(messages).toEqual(
      expect.arrayContaining([
        expect.stringContaining("username"),
        expect.stringContaining("password"),
        expect.stringContaining("accountId"),
      ]),
    );
  });
});
