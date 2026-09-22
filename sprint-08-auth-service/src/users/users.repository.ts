import { Inject, Injectable } from "@nestjs/common";
import { Pool } from "pg";
import { PG_POOL } from "../db/pool.provider";

export interface UserRecord {
  id: string;
  username: string;
  passwordHash: string;
  accountId: number;
  roles: string[];
  createdAt: Date;
}

export interface CreateUserInput {
  username: string;
  passwordHash: string;
  accountId: number;
  roles: string[];
}

/** Postgres error code for a UNIQUE constraint violation. */
export const UNIQUE_VIOLATION = "23505";
/** Postgres error code for a FOREIGN KEY constraint violation. */
export const FOREIGN_KEY_VIOLATION = "23503";

interface PgError {
  code?: string;
}

export function pgErrorCode(err: unknown): string | undefined {
  return (err as PgError).code;
}

interface UserRow {
  id: string;
  username: string;
  password_hash: string;
  account_id: string | number;
  roles: string[];
  created_at: Date;
}

function toUserRecord(row: UserRow): UserRecord {
  return {
    id: row.id,
    username: row.username,
    passwordHash: row.password_hash,
    accountId: Number(row.account_id),
    roles: row.roles,
    createdAt: row.created_at,
  };
}

/** Every statement is parameterised; none is built by string concatenation. */
@Injectable()
export class UsersRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async create(input: CreateUserInput): Promise<UserRecord> {
    const result = await this.pool.query<UserRow>(
      `INSERT INTO users (username, password_hash, account_id, roles)
       VALUES ($1, $2, $3, $4)
       RETURNING id, username, password_hash, account_id, roles, created_at`,
      [input.username, input.passwordHash, input.accountId, input.roles],
    );
    return toUserRecord(result.rows[0]);
  }

  async findByUsername(username: string): Promise<UserRecord | null> {
    const result = await this.pool.query<UserRow>(
      `SELECT id, username, password_hash, account_id, roles, created_at FROM users WHERE username = $1`,
      [username],
    );
    return result.rows[0] ? toUserRecord(result.rows[0]) : null;
  }

  async findById(id: string): Promise<UserRecord | null> {
    const result = await this.pool.query<UserRow>(
      `SELECT id, username, password_hash, account_id, roles, created_at FROM users WHERE id = $1`,
      [id],
    );
    return result.rows[0] ? toUserRecord(result.rows[0]) : null;
  }
}
