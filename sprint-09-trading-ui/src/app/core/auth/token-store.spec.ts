import { TestBed } from '@angular/core/testing';
import { TokenStore } from './token-store';

describe('TokenStore', () => {
  let store: TokenStore;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    store = TestBed.inject(TokenStore);
  });

  it('starts signed out', () => {
    expect(store.token()).toBeNull();
    expect(store.isAuthenticated()).toBe(false);
  });

  it('setToken stores the token and flips isAuthenticated', () => {
    store.setToken('a.b.c');
    expect(store.token()).toBe('a.b.c');
    expect(store.isAuthenticated()).toBe(true);
  });

  it('clearToken clears the session', () => {
    store.setToken('a.b.c');
    store.clearToken();
    expect(store.token()).toBeNull();
    expect(store.isAuthenticated()).toBe(false);
  });
});
