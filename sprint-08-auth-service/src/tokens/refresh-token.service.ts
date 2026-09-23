import { Injectable, Logger, UnauthorizedException } from "@nestjs/common";
import { createHash, randomBytes } from "crypto";
import { RefreshTokenRepository } from "./refresh-token.repository";

/** Seven days, exactly as contracts/auth-api.yaml fixes it. */
export const REFRESH_TOKEN_TTL_SECONDS = 7 * 24 * 60 * 60;

export interface RotatedRefreshToken {
  userId: string;
  refreshToken: string;
}

/**
 * Issues and rotates refresh tokens. Revocation of the presented token is
 * built (the team's SEC4-627 decision, recorded in the security review):
 * every refresh invalidates the token it consumed and, if that token had
 * already been consumed once before, treats the second presentation as
 * theft and revokes every live token for that user.
 */
@Injectable()
export class RefreshTokenService {
  private readonly logger = new Logger(RefreshTokenService.name);

  constructor(private readonly repository: RefreshTokenRepository) {}

  /** Called on login: a freshly authenticated user gets a first refresh token. */
  async issue(userId: string): Promise<string> {
    const token = generateToken();
    await this.repository.create(userId, hashToken(token), expiryFromNow());
    return token;
  }

  /**
   * Consumes the presented refresh token and returns a newly issued one.
   * The presented token is dead in the store before this resolves - a
   * second presentation of the same value fails the `row.revokedAt !==
   * null` check below and answers AUTH-401, exactly as the contract
   * requires.
   */
  async rotate(presentedToken: string): Promise<RotatedRefreshToken> {
    const row = await this.repository.findByHash(hashToken(presentedToken));

    if (!row) {
      throw new UnauthorizedException();
    }

    if (row.revokedAt !== null) {
      // Reuse of an already-rotated token: either a client replayed a
      // request, or a token was stolen and both parties are now refreshing
      // from it. The service cannot tell which, so it treats it as theft:
      // every live refresh token for this user is revoked, ending both
      // sessions rather than leaving one of them live and undetected.
      await this.repository.revokeAllForUser(row.userId);
      // The A09 finding this answers: a replayed refresh token is exactly
      // the question "what happened at the moment someone reports a
      // stolen session", and this is the only record of it.
      this.logger.warn({ event: "refresh_reuse_detected", userId: row.userId });
      throw new UnauthorizedException();
    }

    if (row.expiresAt.getTime() < Date.now()) {
      throw new UnauthorizedException();
    }

    await this.repository.revoke(row.id);
    const nextToken = generateToken();
    await this.repository.create(row.userId, hashToken(nextToken), expiryFromNow());

    return { userId: row.userId, refreshToken: nextToken };
  }
}

function generateToken(): string {
  return randomBytes(32).toString("hex");
}

function expiryFromNow(): Date {
  return new Date(Date.now() + REFRESH_TOKEN_TTL_SECONDS * 1000);
}

/**
 * SHA-256, not argon2/bcrypt: a refresh token is 256 bits of
 * cryptographically random data, not a low-entropy human password, so a
 * fast hash is enough to make a stolen database read not equal a stolen
 * session (the same reason the contract wants a hash stored at all). A
 * slow KDF here would be pure overhead - brute-forcing 256 bits of entropy
 * isn't the threat a slow hash defends against.
 */
function hashToken(token: string): string {
  return createHash("sha256").update(token).digest("hex");
}
