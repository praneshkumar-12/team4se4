import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthService } from '@/generated/auth-api/api/auth.service';
import { TokenStore } from '@/core/auth/token-store';
import { ROUTE_PATHS } from '@/core/config/routes.const';

import { SignInPage } from './sign-in-page';

function activatedRouteStub(queryParams: Record<string, string>) {
  return {
    snapshot: {
      queryParamMap: {
        get: (key: string) => queryParams[key] ?? null,
      },
    },
  };
}

describe('SignInPage', () => {
  let login: ReturnType<typeof vi.fn>;
  let tokenStore: TokenStore;
  let router: Router;

  function setup(queryParams: Record<string, string> = {}) {
    login = vi.fn();

    TestBed.configureTestingModule({
      imports: [SignInPage],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { login } },
        { provide: ActivatedRoute, useValue: activatedRouteStub(queryParams) },
      ],
    });

    tokenStore = TestBed.inject(TokenStore);
    router = TestBed.inject(Router);

    const fixture = TestBed.createComponent(SignInPage);
    fixture.detectChanges();
    return fixture;
  }

  function fillAndSubmit(fixture: ReturnType<typeof setup>, email: string, password: string) {
    const emailInput = fixture.nativeElement.querySelector(
      '[data-testid="sign-in-email"]',
    ) as HTMLInputElement;
    const passwordInput = fixture.nativeElement.querySelector(
      '[data-testid="sign-in-password"]',
    ) as HTMLInputElement;

    emailInput.value = email;
    emailInput.dispatchEvent(new Event('input'));
    passwordInput.value = password;
    passwordInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit', { cancelable: true }));
    fixture.detectChanges();
  }

  it('valid credentials sign in and redirect', () => {
    const fixture = setup({ returnUrl: '/orders/new' });
    const navigateByUrl = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    login.mockReturnValue(
      of({ accessToken: 'token-123', refreshToken: 'r', tokenType: 'Bearer', expiresIn: 900 }),
    );

    fillAndSubmit(fixture, 'arun.kumar@example.com', 'Correct-Horse-Battery-9');

    expect(login).toHaveBeenCalledWith({
      loginRequest: { email: 'arun.kumar@example.com', password: 'Correct-Horse-Battery-9' },
    });
    expect(tokenStore.token()).toBe('token-123');
    expect(navigateByUrl).toHaveBeenCalledWith('/orders/new');
  });

  it('valid credentials fall back to the blotter when returnUrl is not a safe same-origin path', () => {
    const fixture = setup({ returnUrl: 'https://evil.com' });
    const navigateByUrl = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    login.mockReturnValue(
      of({ accessToken: 'token-123', refreshToken: 'r', tokenType: 'Bearer', expiresIn: 900 }),
    );

    fillAndSubmit(fixture, 'arun.kumar@example.com', 'Correct-Horse-Battery-9');

    expect(navigateByUrl).toHaveBeenCalledWith(ROUTE_PATHS.blotter);
  });

  it('invalid credentials show a readable error', () => {
    const fixture = setup();
    login.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            error: { errorCode: 'AUTH-401', message: 'Unauthorised' },
          }),
      ),
    );

    fillAndSubmit(fixture, 'arun.kumar@example.com', 'wrong-password');

    const alert = fixture.nativeElement.querySelector('[data-slot="alert-description"]');
    expect(alert?.textContent?.trim().length).toBeGreaterThan(0);
    expect(tokenStore.isAuthenticated()).toBe(false);
  });

  it('empty fields are blocked by validation', () => {
    const fixture = setup();

    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit', { cancelable: true }));
    fixture.detectChanges();

    expect(login).not.toHaveBeenCalled();
    const errors = fixture.nativeElement.querySelectorAll('[data-slot="field-error"]');
    expect(errors.length).toBeGreaterThan(0);
  });
});
