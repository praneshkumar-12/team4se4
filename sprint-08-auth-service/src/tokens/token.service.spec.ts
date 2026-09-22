import { ConfigService } from "@nestjs/config";
import * as jwt from "jsonwebtoken";
import { TokenService } from "./token.service";

const SECRET = "test-secret-at-least-32-characters-long";

function buildService(env: Record<string, string> = {}): TokenService {
  const config = new ConfigService({ JWT_SECRET: SECRET, ...env });
  return new TokenService(config);
}

describe("TokenService", () => {
  const subject = { id: "8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f", accountId: 1, roles: ["CUSTOMER"] };

  it("issues a token carrying exactly the contract claims and a 15 minute expiry", () => {
    const service = buildService();
    const token = service.createAccessToken(subject);
    const decoded = jwt.decode(token) as jwt.JwtPayload;

    expect(decoded.sub).toBe(subject.id);
    expect(decoded.accountId).toBe(subject.accountId);
    expect(decoded.roles).toEqual(subject.roles);
    expect(decoded.iss).toBe("auth-service");
    expect(typeof decoded.iat).toBe("number");
    expect(typeof decoded.exp).toBe("number");
    expect(decoded.exp! - decoded.iat!).toBe(TokenService.ACCESS_TOKEN_TTL_SECONDS);
  });

  it("signs with HS256 using the configured secret, so the signature verifies with the service key", () => {
    const service = buildService();
    const token = service.createAccessToken(subject);

    expect(() => jwt.verify(token, SECRET, { algorithms: ["HS256"] })).not.toThrow();
    expect(() => jwt.verify(token, "a-completely-different-signing-key-value")).toThrow();
  });

  it("carries no claim outside sub, accountId, roles, iat, exp and iss", () => {
    const service = buildService();
    const token = service.createAccessToken(subject);
    const decoded = jwt.decode(token) as jwt.JwtPayload;

    expect(Object.keys(decoded).sort()).toEqual(["accountId", "exp", "iat", "iss", "roles", "sub"]);
  });

  it("uses JWT_ISSUER when the environment sets one, without requiring a fixed issuer value", () => {
    const service = buildService({ JWT_ISSUER: "team4-auth-service" });
    const token = service.createAccessToken(subject);
    const decoded = jwt.decode(token) as jwt.JwtPayload;

    expect(decoded.iss).toBe("team4-auth-service");
  });
});
