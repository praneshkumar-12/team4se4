import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';

import { AuthService } from '@/generated/auth-api/api/auth.service';
import type { LoginRequest } from '@/generated/auth-api/model/loginRequest';

/**
 * SEC4-635's integration test: signs in against the real running
 * auth-service end to end (no mocks). Excluded from `npm test`
 * (`angular.json`'s `test.options.exclude`) — run it explicitly with
 * `npm run test:integration` after `./run-local.sh start`. See this
 * feature's README for the `email`-vs-`username` field note and demo
 * credentials.
 */
const AUTH_SERVICE_BASE_URL = 'http://localhost:3000';
const DEMO_EMAIL = 'arun.kumar@example.com';
const DEMO_PASSWORD = 'Correct-Horse-Battery-9';

async function ensureDemoUserIsRegistered(): Promise<void> {
  const response = await fetch(`${AUTH_SERVICE_BASE_URL}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: DEMO_EMAIL, password: DEMO_PASSWORD }),
  });

  // 201 = freshly registered, 409 = already registered by an earlier run —
  // both leave us with a usable account. Anything else is a real setup
  // problem the test should fail loudly on.
  if (response.status !== 201 && response.status !== 409) {
    throw new Error(
      `Could not seed the demo user before the integration test: ${response.status} ${await response.text()}`,
    );
  }
}

describe('Sign-in against the real Auth service (integration)', () => {
  let authService: AuthService;

  beforeAll(async () => {
    await ensureDemoUserIsRegistered();
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient()],
    });
    authService = TestBed.inject(AuthService);
  });

  it('signs in with valid credentials and receives a real access token', async () => {
    const loginRequest = { email: DEMO_EMAIL, password: DEMO_PASSWORD } as unknown as LoginRequest;

    const response = await new Promise<{ accessToken: string }>((resolve, reject) => {
      authService.login({ loginRequest }).subscribe({ next: resolve, error: reject });
    });

    expect(typeof response.accessToken).toBe('string');
    expect(response.accessToken.split('.')).toHaveLength(3);
  });

  it('rejects invalid credentials with AUTH-401', async () => {
    const loginRequest = {
      email: DEMO_EMAIL,
      password: 'definitely-the-wrong-password',
    } as unknown as LoginRequest;

    await expect(
      new Promise((resolve, reject) => {
        authService.login({ loginRequest }).subscribe({ next: resolve, error: reject });
      }),
    ).rejects.toMatchObject({ status: 401 });
  });
});
