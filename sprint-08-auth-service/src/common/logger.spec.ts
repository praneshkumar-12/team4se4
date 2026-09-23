import { redact } from "./logger";

describe("redact", () => {
  it("redacts a credential key at the top level", () => {
    expect(redact({ username: "priya", password: "hunter2" })).toEqual({
      username: "priya",
      password: "[REDACTED]",
    });
  });

  it("redacts a credential key at any depth", () => {
    const input = { event: "login_failed", request: { body: { username: "priya", password: "hunter2" } } };
    expect(redact(input)).toEqual({
      event: "login_failed",
      request: { body: { username: "priya", password: "[REDACTED]" } },
    });
  });

  it("redacts inside an array of objects", () => {
    const input = [{ accessToken: "abc" }, { refreshToken: "def" }];
    expect(redact(input)).toEqual([{ accessToken: "[REDACTED]" }, { refreshToken: "[REDACTED]" }]);
  });

  it("redacts an Error object's own properties, not just message/name", () => {
    const err = new Error("failed") as Error & { body?: unknown };
    err.body = { password: "hunter2", username: "priya" };
    const result = redact(err) as Record<string, unknown>;

    expect(result.message).toBe("failed");
    expect(result.body).toEqual({ password: "[REDACTED]", username: "priya" });
  });

  it("is case-insensitive on the key name", () => {
    expect(redact({ Password: "hunter2", ACCESSTOKEN: "abc" })).toEqual({
      Password: "[REDACTED]",
      ACCESSTOKEN: "[REDACTED]",
    });
  });

  it("leaves non-credential values untouched", () => {
    expect(redact({ username: "priya", accountId: 1 })).toEqual({ username: "priya", accountId: 1 });
  });
});
