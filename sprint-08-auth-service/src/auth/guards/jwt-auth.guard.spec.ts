import { ConfigService } from "@nestjs/config";
import { ExecutionContext, UnauthorizedException } from "@nestjs/common";
import * as jwt from "jsonwebtoken";
import { JwtAuthGuard, RequestWithUser } from "./jwt-auth.guard";

const SECRET = "guard-test-secret-at-least-32-characters";
const ISSUER = "auth-service";

function buildGuard(): JwtAuthGuard {
  return new JwtAuthGuard(new ConfigService({ JWT_SECRET: SECRET, JWT_ISSUER: ISSUER }));
}

function contextWithAuthHeader(header: string | undefined): { context: ExecutionContext; request: RequestWithUser } {
  const request = { headers: { authorization: header } } as unknown as RequestWithUser;
  const context = {
    switchToHttp: () => ({ getRequest: () => request }),
  } as unknown as ExecutionContext;
  return { context, request };
}

function signValidToken(): string {
  return jwt.sign({ accountId: 1, roles: ["CUSTOMER"] }, SECRET, {
    subject: "8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f",
    issuer: ISSUER,
    algorithm: "HS256",
    expiresIn: 900,
  });
}

describe("JwtAuthGuard", () => {
  it("accepts a valid token and attaches the verified user to the request", () => {
    const guard = buildGuard();
    const { context, request } = contextWithAuthHeader(`Bearer ${signValidToken()}`);

    expect(guard.canActivate(context)).toBe(true);
    expect(request.user).toEqual({
      sub: "8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f",
      accountId: 1,
      roles: ["CUSTOMER"],
    });
  });

  it("refuses an expired token", () => {
    // A genuine token, signed with the real key, whose exp is already in
    // the past - not a corrupted payload, which the signature check would
    // refuse first regardless of whether the guard checks the clock.
    const now = Math.floor(Date.now() / 1000);
    const expired = jwt.sign(
      { sub: "8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f", accountId: 1, roles: ["CUSTOMER"], iat: now - 1000, exp: now - 1 },
      SECRET,
      { issuer: ISSUER, algorithm: "HS256" },
    );
    const guard = buildGuard();
    const { context } = contextWithAuthHeader(`Bearer ${expired}`);

    expect(() => guard.canActivate(context)).toThrow(UnauthorizedException);
  });

  it("refuses a token with the wrong signature before any claim is read", () => {
    // A genuine, unexpired token signed with a DIFFERENT key. Catches a
    // verifier that decodes the payload before checking the signature.
    const wrongKeyToken = jwt.sign({ accountId: 1, roles: ["CUSTOMER"] }, "a-completely-different-signing-key", {
      subject: "8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f",
      issuer: ISSUER,
      algorithm: "HS256",
      expiresIn: 900,
    });
    const guard = buildGuard();
    const { context } = contextWithAuthHeader(`Bearer ${wrongKeyToken}`);

    expect(() => guard.canActivate(context)).toThrow(UnauthorizedException);
  });

  it("refuses a malformed authorization header", () => {
    const guard = buildGuard();

    expect(() => guard.canActivate(contextWithAuthHeader(undefined).context)).toThrow(UnauthorizedException);
    expect(() => guard.canActivate(contextWithAuthHeader("").context)).toThrow(UnauthorizedException);
    expect(() => guard.canActivate(contextWithAuthHeader("Bearer").context)).toThrow(UnauthorizedException);
    expect(() => guard.canActivate(contextWithAuthHeader("Basic dXNlcjpwYXNz").context)).toThrow(UnauthorizedException);
    expect(() => guard.canActivate(contextWithAuthHeader("Bearer not-a-jwt").context)).toThrow(UnauthorizedException);
  });
});
