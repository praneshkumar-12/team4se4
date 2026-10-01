import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { bearerTokenInterceptor } from './bearer-token.interceptor';
import { TokenStore } from '../auth/token-store';
import { environment } from '../config/environment';

describe('bearerTokenInterceptor (SEC4-636)', () => {
  const platformOrigin = environment.allowedOrigins[0];
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let tokenStore: TokenStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([bearerTokenInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    tokenStore = TestBed.inject(TokenStore);
    tokenStore.setToken('live-session-token');
  });

  afterEach(() => httpMock.verify());

  it('a request to a platform API carries the bearer token', () => {
    http.get(`${platformOrigin}/trade-api/api/v1/accounts/1`).subscribe();

    const req = httpMock.expectOne(`${platformOrigin}/trade-api/api/v1/accounts/1`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer live-session-token');
  });

  it('a relative platform URL resolves to the page origin and carries the bearer token', () => {
    http.get('/auth-api/auth/me').subscribe();

    const req = httpMock.expectOne('/auth-api/auth/me');
    expect(req.request.headers.get('Authorization')).toBe('Bearer live-session-token');
  });

  it('a request to a third-party origin does not carry the bearer token', () => {
    http.get('https://analytics.example.com/collect').subscribe();

    const req = httpMock.expectOne('https://analytics.example.com/collect');
    expect(req.request.headers.has('Authorization')).toBe(false);
  });

  it('a request to a look-alike of a platform origin does not carry the bearer token', () => {
    http.get(`${platformOrigin}.attacker.example/trade-api/api/v1/accounts/1`).subscribe();

    const req = httpMock.expectOne(`${platformOrigin}.attacker.example/trade-api/api/v1/accounts/1`);
    expect(req.request.headers.has('Authorization')).toBe(false);
  });

  it('the unauthenticated auth routes are called without a header', () => {
    for (const path of ['register', 'login', 'refresh']) {
      http.post(`${platformOrigin}/auth-api/auth/${path}`, {}).subscribe();
      const req = httpMock.expectOne(`${platformOrigin}/auth-api/auth/${path}`);
      expect(req.request.headers.has('Authorization')).toBe(false);
    }
  });

  it('a platform request made while signed out carries no header', () => {
    tokenStore.clearToken();
    http.get(`${platformOrigin}/trade-api/api/v1/accounts/1`).subscribe();

    const req = httpMock.expectOne(`${platformOrigin}/trade-api/api/v1/accounts/1`);
    expect(req.request.headers.has('Authorization')).toBe(false);
  });
});
