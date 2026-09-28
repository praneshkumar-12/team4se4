import { TestBed } from '@angular/core/testing';
import { AccountContext } from './account-context';
import { TokenStore } from './token-store';

function fakeJwt(payload: object): string {
  const base64url = (s: string) => btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const header = base64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = base64url(JSON.stringify(payload));
  return `${header}.${body}.fake-signature`;
}

describe('AccountContext', () => {
  let tokenStore: TokenStore;
  let context: AccountContext;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    tokenStore = TestBed.inject(TokenStore);
    context = TestBed.inject(AccountContext);
  });

  it('returns null accountId when signed out', () => {
    expect(context.accountId()).toBeNull();
    expect(context.roles()).toEqual([]);
  });

  it('decodes accountId and roles off a real-shaped token', () => {
    tokenStore.setToken(
      fakeJwt({
        sub: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f',
        accountId: 1,
        roles: ['CUSTOMER'],
        iat: 1790000000,
        exp: 1790000900,
        iss: 'auth-service',
      }),
    );
    expect(context.accountId()).toBe(1);
    expect(context.roles()).toEqual(['CUSTOMER']);
  });

  it('returns null rather than throwing on a malformed token', () => {
    tokenStore.setToken('not-a-jwt');
    expect(context.accountId()).toBeNull();
  });
});
