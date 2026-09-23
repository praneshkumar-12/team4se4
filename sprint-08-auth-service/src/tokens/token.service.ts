import { Injectable } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";
import * as jwt from "jsonwebtoken";

/**
 * What createAccessToken needs to know about the caller. Deliberately
 * narrower than the full user record: this is the contract other modules
 * (registration, login, refresh) build against, published here so they can
 * be developed in parallel with this token service rather than after it.
 */
export interface TokenSubject {
  /** The user identifier, a UUID. Becomes the `sub` claim. */
  id: string;
  /** The numeric trading account key, ACCOUNTS.account_id. */
  accountId: number;
  roles: string[];
}

@Injectable()
export class TokenService {
  /** Fifteen minutes, exactly as contracts/auth-api.yaml fixes it. */
  static readonly ACCESS_TOKEN_TTL_SECONDS = 15 * 60;

  private readonly secret: string;
  private readonly issuer: string;

  constructor(config: ConfigService) {
    // No default: a missing JWT_SECRET fails service startup rather than
    // signing tokens with a value nobody chose.
    this.secret = config.getOrThrow<string>("JWT_SECRET");
    this.issuer = config.get<string>("JWT_ISSUER", "auth-service");
  }

  /**
   * Signs an access token carrying exactly the claim set the contract
   * fixes: sub, accountId, roles, iat, exp, iss. Nothing else - a claim
   * added here for convenience is a claim Sprint 9 can generate a client
   * against, and removing it afterwards is not a configuration change.
   */
  createAccessToken(user: TokenSubject): string {
    return jwt.sign({ accountId: user.accountId, roles: user.roles }, this.secret, {
      subject: user.id,
      issuer: this.issuer,
      algorithm: "HS256",
      expiresIn: TokenService.ACCESS_TOKEN_TTL_SECONDS,
    });
  }
}
