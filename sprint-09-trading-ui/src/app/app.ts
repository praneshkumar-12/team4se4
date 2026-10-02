import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { ZardButtonComponent } from '@/shared/components/button/button.component';

import { TokenStore } from './core/auth/token-store';
import { ROUTE_PATHS } from './core/config/routes.const';

/**
 * The one shared shell every route renders inside. The sign-out control
 * lives here (SEC4-635) since Person 1 didn't build app-shell nav. The two
 * nav links are the minimal addition that makes the order ticket actually
 * reachable from the blotter — previously there was no in-app way to get
 * there at all, only a typed URL.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ZardButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (tokenStore.isAuthenticated()) {
      <header class="flex items-center justify-between border-b border-border px-6 py-3">
        <div class="flex items-center gap-6">
          <span class="text-sm font-medium">Trading Platform</span>
          <nav class="flex items-center gap-4">
            <a
              [routerLink]="routePaths.blotter"
              routerLinkActive="text-foreground font-medium"
              class="text-muted-foreground text-sm hover:text-foreground"
              data-testid="nav-blotter"
              >Blotter</a
            >
            <a
              [routerLink]="routePaths.orderTicket"
              routerLinkActive="text-foreground font-medium"
              class="text-muted-foreground text-sm hover:text-foreground"
              data-testid="nav-order-ticket"
              >New order</a
            >
          </nav>
        </div>
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
  protected readonly routePaths = ROUTE_PATHS;
  private readonly router = inject(Router);

  protected signOut(): void {
    this.tokenStore.clearToken();
    this.router.navigateByUrl(ROUTE_PATHS.signIn);
  }
}
