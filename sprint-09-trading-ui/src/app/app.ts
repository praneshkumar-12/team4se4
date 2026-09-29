import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';

import { ZardButtonComponent } from '@/shared/components/button/button.component';

import { TokenStore } from './core/auth/token-store';
import { ROUTE_PATHS } from './core/config/routes.const';

/**
 * The one shared shell every route renders inside. The sign-out control
 * lives here (SEC4-635) since Person 1 didn't build app-shell nav — it's
 * the minimal place a control that isn't scoped to a single feature route
 * makes sense.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ZardButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (tokenStore.isAuthenticated()) {
      <header class="flex items-center justify-between border-b border-border px-6 py-3">
        <span class="text-sm font-medium">Trading Platform</span>
        <button z-button zType="outline" zSize="sm" data-testid="sign-out" (click)="signOut()">
          Sign out
        </button>
      </header>
    }
    <router-outlet />
  `,
})
export class App {
  protected readonly tokenStore = inject(TokenStore);
  private readonly router = inject(Router);

  protected signOut(): void {
    this.tokenStore.clearToken();
    this.router.navigateByUrl(ROUTE_PATHS.signIn);
  }
}
