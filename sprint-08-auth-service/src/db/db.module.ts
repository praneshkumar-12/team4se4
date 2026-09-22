import { Global, Module } from "@nestjs/common";
import { PG_POOL, pgPoolProvider } from "./pool.provider";

/**
 * Just the Postgres pool. This service's schema (the users and
 * refresh_tokens tables) is no longer applied by this process at
 * startup - it's Liquibase-tracked, in this service's own changelog
 * (db/changelog/db.changelog-master.xml), applied by the
 * auth-service-migrate job in the root docker-compose.yml before this
 * service's container starts. One tool (Liquibase, already used by the
 * Trade REST API for its own schema since Sprint 3) and one changelog per
 * service's schema, rather than a second, ad hoc bootstrap mechanism
 * living in application code.
 */
@Global()
@Module({
  providers: [pgPoolProvider],
  exports: [PG_POOL],
})
export class DbModule {}
