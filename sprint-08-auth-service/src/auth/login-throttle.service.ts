import { Injectable } from "@nestjs/common";

interface Window {
  count: number;
  windowStart: number;
}

/**
 * Fixed-window login throttle keyed by caller IP. Complements the uniform
 * login failure response by limiting guessing rate.
 *
 * State is in-memory and per-instance, so it resets on restart and is not
 * shared across replicas. A shared store would be the proper fix.
 */
@Injectable()
export class LoginThrottleService {
  /** Limits are documented in the README. */
  static readonly MAX_ATTEMPTS = 5;
  static readonly COOLDOWN_MS = 5 * 60 * 1000;

  private readonly windows = new Map<string, Window>();

  isThrottled(callerId: string): boolean {
    const window = this.currentWindow(callerId);
    return window !== null && window.count >= LoginThrottleService.MAX_ATTEMPTS;
  }

  registerFailure(callerId: string): void {
    const window = this.currentWindow(callerId);
    if (window) {
      window.count += 1;
    } else {
      this.windows.set(callerId, { count: 1, windowStart: Date.now() });
    }
  }

  registerSuccess(callerId: string): void {
    this.windows.delete(callerId);
  }

  /** Returns the caller's live window, expiring and dropping a stale one. */
  private currentWindow(callerId: string): Window | null {
    const window = this.windows.get(callerId);
    if (!window) {
      return null;
    }
    if (Date.now() - window.windowStart > LoginThrottleService.COOLDOWN_MS) {
      this.windows.delete(callerId);
      return null;
    }
    return window;
  }
}
