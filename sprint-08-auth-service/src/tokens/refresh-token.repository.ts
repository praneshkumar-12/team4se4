import { Inject, Injectable } from "@nestjs/common";
import { Pool } from "pg";
import { PG_POOL } from "../db/pool.provider";

export interface RefreshTokenRow {
  id: string;
  userId: string;
  revokedAt: Date | null;
  expiresAt: Date;
}

/**
 * Every statement here is parameterised ($1, $2, ...). None is ever built
 * by concatenating a caller-supplied value into the SQL text.
 */
@Injectable()
export class RefreshTokenRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async create(userId: string, tokenHash: string, expiresAt: Date): Promise<void> {
    await this.pool.query(`INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES ($1, $2, $3)`, [
      userId,
      tokenHash,
      expiresAt,
    ]);
  }

  async findByHash(tokenHash: string): Promise<RefreshTokenRow | null> {
    const result = await this.pool.query<{ id: string; user_id: string; revoked_at: Date | null; expires_at: Date }>(
      `SELECT id, user_id, revoked_at, expires_at FROM refresh_tokens WHERE token_hash = $1`,
      [tokenHash],
    );
    const row = result.rows[0];
    if (!row) {
      return null;
    }
    return { id: row.id, userId: row.user_id, revokedAt: row.revoked_at, expiresAt: row.expires_at };
  }

  async revoke(id: string): Promise<void> {
    await this.pool.query(`UPDATE refresh_tokens SET revoked_at = now() WHERE id = $1 AND revoked_at IS NULL`, [id]);
  }

  async revokeAllForUser(userId: string): Promise<void> {
    await this.pool.query(`UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = $1 AND revoked_at IS NULL`, [
      userId,
    ]);
  }
}
