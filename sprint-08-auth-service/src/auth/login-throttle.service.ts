import { Injectable } from "@nestjs/common";

interface Window {
  count: number;
  windowStart: number;
}

/**
 * A fixed-window login throttle, keyed by caller (IP address). Limits how
 * fast an attacker can use the timing/enumeration oracle the uniform
 * failure closes - it does not close an oracle by itself, which is why
 * SEC4-628 builds both.
 *
 * In-memory and per-instance: it does not survive a restart and does not
 * share state across replicas. Acceptable for this training stack; the
 * security review records it as a residual risk rather than a fix, since
 * a shared store (Redis, or a table) is the real fix and out of scope
 * here.
 */
@Injectable()
export class LoginThrottleService {
  /** Recorded in the sprint README per SEC4-628. */
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
