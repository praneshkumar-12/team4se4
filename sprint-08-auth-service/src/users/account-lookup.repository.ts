import { Inject, Injectable } from "@nestjs/common";
import { Pool } from "pg";
import { PG_POOL } from "../db/pool.provider";

/**
 * Resolves the trading account a registering email belongs to, by reading
 * trade-api's own clients/accounts tables (this service does not own or
 * migrate them - see 001-users.sql). clients.email and accounts.client_id
 * are both UNIQUE, so this join is always zero or one row.
 */
@Injectable()
export class AccountLookupRepository {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async findAccountIdByEmail(email: string): Promise<number | null> {
    const result = await this.pool.query<{ account_id: string | number }>(
      `SELECT a.account_id
       FROM clients c
       JOIN accounts a ON a.client_id = c.client_id
       WHERE c.email = $1`,
      [email],
    );
    return result.rows[0] ? Number(result.rows[0].account_id) : null;
  }
}
