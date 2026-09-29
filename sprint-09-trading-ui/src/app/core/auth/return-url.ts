/**
 * A return address travels as a `returnUrl` query param on the sign-in URL,
 * so it's attacker-controlled input even though the guard that first
 * produces it (see `auth.guard.ts`) only ever writes a safe one. An open
 * redirect on a trading sign-in page is a phishing kit somebody else
 * assembles for free — reject anything that isn't a same-origin relative
 * path before it's ever handed to `Router.navigateByUrl`.
 */
export function isSafeReturnUrl(url: string | null | undefined): url is string {
  if (!url) {
    return false;
  }
  if (!url.startsWith('/') || url.startsWith('//')) {
    return false;
  }
  // A backslash is how "/\evil.com"-style addresses smuggle in a
  // protocol-relative host: some URL parsers normalise it to "//evil.com".
  if (url.includes('\\')) {
    return false;
  }
  return true;
}
