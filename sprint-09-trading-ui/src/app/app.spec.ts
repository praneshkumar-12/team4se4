import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';

import { TokenStore } from './core/auth/token-store';
import { ROUTE_PATHS } from './core/config/routes.const';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('does not show the sign-out control while signed out', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const signOutButton = fixture.nativeElement.querySelector('[data-testid="sign-out"]');
    expect(signOutButton).toBeNull();
  });

  it('signing out clears the session', () => {
    const fixture = TestBed.createComponent(App);
    const tokenStore = TestBed.inject(TokenStore);
    const router = TestBed.inject(Router);
    const navigateByUrl = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    tokenStore.setToken('a.b.c');
    fixture.detectChanges();

    const signOutButton = fixture.nativeElement.querySelector(
      '[data-testid="sign-out"]',
    ) as HTMLElement;
    expect(signOutButton).not.toBeNull();
    signOutButton.click();
    fixture.detectChanges();

    expect(tokenStore.isAuthenticated()).toBe(false);
    expect(navigateByUrl).toHaveBeenCalledWith(ROUTE_PATHS.signIn);
  });
});
