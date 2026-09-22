import { LoginThrottleService } from "./login-throttle.service";

describe("LoginThrottleService", () => {
  it("does not throttle a caller with no recorded failures", () => {
    const throttle = new LoginThrottleService();
    expect(throttle.isThrottled("203.0.113.1")).toBe(false);
  });

  it("limits repeated failed attempts from one caller", () => {
    const throttle = new LoginThrottleService();
    const caller = "203.0.113.1";

    for (let i = 0; i < LoginThrottleService.MAX_ATTEMPTS; i++) {
      expect(throttle.isThrottled(caller)).toBe(false);
      throttle.registerFailure(caller);
    }

    expect(throttle.isThrottled(caller)).toBe(true);
  });

  it("does not throttle a different caller", () => {
    const throttle = new LoginThrottleService();
    for (let i = 0; i < LoginThrottleService.MAX_ATTEMPTS; i++) {
      throttle.registerFailure("203.0.113.1");
    }
    expect(throttle.isThrottled("203.0.113.2")).toBe(false);
  });

  it("clears the window on a success", () => {
    const throttle = new LoginThrottleService();
    const caller = "203.0.113.1";
    for (let i = 0; i < LoginThrottleService.MAX_ATTEMPTS; i++) {
      throttle.registerFailure(caller);
    }
    throttle.registerSuccess(caller);
    expect(throttle.isThrottled(caller)).toBe(false);
  });

  it("resets the window once the cooldown has elapsed", () => {
    const throttle = new LoginThrottleService();
    const caller = "203.0.113.1";
    const realNow = Date.now;
    let now = realNow();
    jest.spyOn(Date, "now").mockImplementation(() => now);

    for (let i = 0; i < LoginThrottleService.MAX_ATTEMPTS; i++) {
      throttle.registerFailure(caller);
    }
    expect(throttle.isThrottled(caller)).toBe(true);

    now += LoginThrottleService.COOLDOWN_MS + 1;
    expect(throttle.isThrottled(caller)).toBe(false);

    (Date.now as jest.Mock).mockRestore();
  });
});
