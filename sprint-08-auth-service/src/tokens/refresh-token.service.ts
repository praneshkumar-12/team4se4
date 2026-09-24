import { Injectable, Logger, UnauthorizedException } from "@nestjs/common";
import { createHash, randomBytes } from "crypto";
import { RefreshTokenRepository } from "./refresh-token.repository";

/** Refresh tokens are valid for seven days. */
export const REFRESH_TOKEN_TTL_SECONDS = 7 * 24 * 60 * 60;

export interface RotatedRefreshToken {
  userId: string;
  refreshToken: string;
}

/**
 * Issues and rotates refresh tokens. Each refresh revokes the token it
 * consumes; presenting an already-revoked token is treated as theft and
 * revokes all of that user's tokens.
 */
@Injectable()
export class RefreshTokenService {
  private readonly logger = new Logger(RefreshTokenService.name);

  constructor(private readonly repository: RefreshTokenRepository) {}

  /** Issues the first refresh token on login. */
  async issue(userId: string): Promise<string> {
    const token = generateToken();
    await this.repository.create(userId, hashToken(token), expiryFromNow());
    return token;
  }

  /** Consumes the presented refresh token and returns a newly issued one. */
  async rotate(presentedToken: string): Promise<RotatedRefreshToken> {
    const row = await this.repository.findByHash(hashToken(presentedToken));

    if (!row) {
      throw new UnauthorizedException();
    }

    if (row.revokedAt !== null) {
      // Reuse of a rotated token cannot be told apart from theft, so all of
      // the user's sessions are ended.
      await this.repository.revokeAllForUser(row.userId);
      // Audit trail for reuse detection (OWASP A09).
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
 * SHA-256 rather than argon2: tokens are 256 bits of random data, so a
 * slow KDF adds cost without adding protection. Only the hash is stored,
 * so a database leak does not expose usable tokens.
 */
function hashToken(token: string): string {
  return createHash("sha256").update(token).digest("hex");
}
