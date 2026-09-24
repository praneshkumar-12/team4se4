import { Global, Module } from "@nestjs/common";
import { PG_POOL, pgPoolProvider } from "./pool.provider";

/**
 * Provides the Postgres pool only. The schema is managed by Liquibase
 * (db/changelog) and applied by the auth-service-migrate job in
 * docker-compose, not by this process at startup.
 */
@Global()
@Module({
  providers: [pgPoolProvider],
  exports: [PG_POOL],
})
export class DbModule {}
