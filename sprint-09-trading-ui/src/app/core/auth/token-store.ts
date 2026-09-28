import { Injectable, computed, signal } from '@angular/core';

/**
 * Where the bearer token lives, and the only place it lives. Person 2's
 * sign-in flow (SEC4-635) calls `setToken`/`clearToken`; Person 3's
 * interceptor (SEC4-636) and `AccountContext` read `token()`.
 *
 * In-memory only: a page refresh signs the user out. That's an intentional
 * simplicity call for this sprint's scope, not an oversight — flag it if
 * SEC4-635 wants persistence (e.g. `sessionStorage`) instead.
 */
@Injectable({ providedIn: 'root' })
export class TokenStore {
  private readonly _token = signal<string | null>(null);

  readonly token = this._token.asReadonly();
  readonly isAuthenticated = computed(() => this._token() !== null);

  setToken(token: string): void {
    this._token.set(token);
  }

  clearToken(): void {
    this._token.set(null);
  }
}
