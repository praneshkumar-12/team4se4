import { isSafeReturnUrl } from './return-url';

describe('isSafeReturnUrl', () => {
  it('accepts a same-origin relative path', () => {
    expect(isSafeReturnUrl('/orders/new')).toBe(true);
    expect(isSafeReturnUrl('/blotter')).toBe(true);
  });

  it('accepts a same-origin relative path carrying its own query string', () => {
    expect(isSafeReturnUrl('/orders/new?symbol=AAPL')).toBe(true);
  });

  it('refuses an off-origin return address', () => {
    expect(isSafeReturnUrl('https://evil.com')).toBe(false);
    expect(isSafeReturnUrl('http://evil.com/blotter')).toBe(false);
    expect(isSafeReturnUrl('//evil.com')).toBe(false);
    expect(isSafeReturnUrl('/\\evil.com')).toBe(false);
  });

  it('refuses a path that does not start with a single leading slash', () => {
    expect(isSafeReturnUrl('orders/new')).toBe(false);
    expect(isSafeReturnUrl('')).toBe(false);
  });

  it('refuses a missing return address', () => {
    expect(isSafeReturnUrl(null)).toBe(false);
    expect(isSafeReturnUrl(undefined)).toBe(false);
  });
});
