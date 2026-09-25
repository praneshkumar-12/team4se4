import { Injectable } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";
import * as jwt from "jsonwebtoken";

/** The minimal user data needed to sign an access token. */
export interface TokenSubject {
  /** The user identifier, a UUID. Becomes the `sub` claim. */
  id: string;
  /** The numeric trading account key, ACCOUNTS.account_id. */
  accountId: number;
  roles: string[];
}

@Injectable()
export class TokenService {
  /** Access tokens are valid for fifteen minutes. */
  static readonly ACCESS_TOKEN_TTL_SECONDS = 15 * 60;

  private readonly secret: string;
  private readonly issuer: string;

  constructor(config: ConfigService) {
    // No default secret: startup fails if JWT_SECRET is missing.
    this.secret = config.getOrThrow<string>("JWT_SECRET");
    this.issuer = config.get<string>("JWT_ISSUER", "auth-service");
  }

  /** Signs an access token carrying only sub, accountId, roles, iat, exp and iss. */
  createAccessToken(user: TokenSubject): string {
    return jwt.sign({ accountId: user.accountId, roles: user.roles }, this.secret, {
      subject: user.id,
      issuer: this.issuer,
      algorithm: "HS256",
      expiresIn: TokenService.ACCESS_TOKEN_TTL_SECONDS,
    });
  }
}
