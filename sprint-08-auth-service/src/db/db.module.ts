import { Global, Inject, Module, OnModuleInit } from "@nestjs/common";
import { Pool } from "pg";
import { PG_POOL, pgPoolProvider } from "./pool.provider";
import { MIGRATIONS } from "./schema";

@Global()
@Module({
  providers: [pgPoolProvider],
  exports: [PG_POOL],
})
export class DbModule implements OnModuleInit {
  constructor(@Inject(PG_POOL) private readonly pool: Pool) {}

  async onModuleInit(): Promise<void> {
    for (const statement of MIGRATIONS) {
      await this.pool.query(statement);
    }
  }
}
