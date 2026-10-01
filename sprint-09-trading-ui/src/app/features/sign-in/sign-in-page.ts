import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { ZardAlertComponent } from '@/shared/components/alert/alert.component';
import { ZardButtonComponent } from '@/shared/components/button/button.component';
import { ZardCardImports } from '@/shared/components/card/card.imports';
import { ZardFieldImports } from '@/shared/components/field/field.imports';
import { ZardInputComponent } from '@/shared/components/input/input.component';

import { isSafeReturnUrl } from '../../core/auth/return-url';
import { TokenStore } from '../../core/auth/token-store';
import { ROUTE_PATHS } from '../../core/config/routes.const';
import type { AuthService } from '../../generated/auth-api/api/auth.service';
import type { LoginRequest } from '../../generated/auth-api/model/loginRequest';
import { AuthService as AuthServiceToken } from '../../generated/auth-api/api/auth.service';

/**
 * SEC4-635 — sign-in against the real Auth service.
 *
 * Test identifiers for Playwright (Person 5, SEC4-641) — see this feature's
 * README: `sign-in-email`, `sign-in-password`, `sign-in-submit`.
 */
@Component({
  selector: 'app-sign-in-page',
  imports: [
    ReactiveFormsModule,
    ZardCardImports,
    ZardFieldImports,
    ZardButtonComponent,
    ZardInputComponent,
    ZardAlertComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex min-h-screen items-center justify-center px-4">
      <z-card class="w-full max-w-sm">
        <z-card-header>
          <h1 z-card-title zTitle="Sign in"></h1>
          <p
            z-card-description
            zDescription="Enter your email and password to access your trading account."
          ></p>
        </z-card-header>
        <z-card-content>
          <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
            <z-field-group>
              <div z-field>
                <label z-field-label for="sign-in-email">Email</label>
                <input
                  z-input
                  id="sign-in-email"
                  type="email"
                  autocomplete="username"
                  formControlName="email"
                  data-testid="sign-in-email"
                  [attr.aria-invalid]="emailInvalid() ? true : null"
                />
                @if (emailInvalid()) {
                  <z-field-error>Enter your email address.</z-field-error>
                }
              </div>
              <div z-field>
                <label z-field-label for="sign-in-password">Password</label>
                <input
                  z-input
                  id="sign-in-password"
                  type="password"
                  autocomplete="current-password"
                  formControlName="password"
                  data-testid="sign-in-password"
                  [attr.aria-invalid]="passwordInvalid() ? true : null"
                />
                @if (passwordInvalid()) {
                  <z-field-error>Enter your password.</z-field-error>
                }
              </div>
            </z-field-group>

            @if (errorMessage(); as message) {
              <z-alert
                zType="destructive"
                zTitle="Sign-in failed"
                [zDescription]="message"
                class="mt-4"
              />
            }

            <button
              z-button
              type="submit"
              class="mt-4 w-full"
              data-testid="sign-in-submit"
              [zLoading]="submitting()"
              [zDisabled]="submitting()"
            >
              Sign in
            </button>
          </form>
        </z-card-content>
      </z-card>
    </div>
  `,
})
export class SignInPage {
  private readonly fb = inject(FormBuilder);
  private readonly authService: AuthService = inject(AuthServiceToken);
  private readonly tokenStore = inject(TokenStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required]],
    password: ['', [Validators.required]],
  });

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected emailInvalid(): boolean {
    const control = this.form.controls.email;
    return control.invalid && control.touched;
  }

  protected passwordInvalid(): boolean {
    const control = this.form.controls.password;
    return control.invalid && control.touched;
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const { email, password } = this.form.getRawValue();

    // The checked-in contract's LoginRequest (and this generated client) is
    // typed `{ username, password }`, but the real running auth-service's
    // LoginDto requires `{ email, password }` and rejects unknown fields
    // (`forbidNonWhitelisted: true`) — a Sprint 8 change (commit
    // 7a95af5, "Register by email instead of accountId") never back-ported
    // to contracts/auth-api.yaml. Send the shape the real service accepts;
    // the mismatch is a contract-fix concern outside this ticket's scope.
    const loginRequest = { email, password } as unknown as LoginRequest;

    this.authService.login({ loginRequest }).subscribe({
      next: (response) => {
        this.tokenStore.setToken(response.accessToken);
        this.submitting.set(false);
        this.router.navigateByUrl(this.resolveReturnUrl());
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(this.messageFor(error));
      },
    });
  }

  private resolveReturnUrl(): string {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    return isSafeReturnUrl(returnUrl) ? returnUrl : ROUTE_PATHS.blotter;
  }

  private messageFor(error: HttpErrorResponse): string {
    if (error.status === 0) {
      return 'Could not reach the trading platform. Check your connection, or the service may be down.';
    }
    if (error.status === 401) {
      return 'Invalid email or password.';
    }
    return 'Something went wrong signing in. Try again.';
  }
}
