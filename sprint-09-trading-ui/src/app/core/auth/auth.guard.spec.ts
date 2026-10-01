import { TestBed } from '@angular/core/testing';
import type { RouterStateSnapshot, UrlTree } from '@angular/router';
import { Router } from '@angular/router';

import { authGuard } from './auth.guard';
import { ROUTE_PATHS } from '../config/routes.const';
import { TokenStore } from './token-store';

describe('authGuard', () => {
  let tokenStore: TokenStore;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    tokenStore = TestBed.inject(TokenStore);
    router = TestBed.inject(Router);
  });

  function runGuard(url: string) {
    return TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url } as RouterStateSnapshot),
    );
  }

  it('blocks unauthenticated navigation and redirects to sign-in with a returnUrl', () => {
    const result = runGuard('/orders/new');

    expect(result).not.toBe(true);
    const tree = result as UrlTree;
    expect(router.serializeUrl(tree)).toBe(`${ROUTE_PATHS.signIn}?returnUrl=%2Forders%2Fnew`);
  });

  it('allows authenticated navigation', () => {
    tokenStore.setToken('a.b.c');

    const result = runGuard('/orders/new');

    expect(result).toBe(true);
  });
});
