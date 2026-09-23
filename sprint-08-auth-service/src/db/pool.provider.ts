import { ConfigService } from "@nestjs/config";
import { Pool } from "pg";

/** DI token: inject with @Inject(PG_POOL). */
export const PG_POOL = "PG_POOL";

export const pgPoolProvider = {
  provide: PG_POOL,
  inject: [ConfigService],
  useFactory: (config: ConfigService): Pool =>
    new Pool({
      host: config.getOrThrow<string>("DB_HOST"),
      port: config.get<number>("DB_PORT", 5432),
      database: config.getOrThrow<string>("DB_NAME"),
      user: config.getOrThrow<string>("DB_USER"),
      password: config.getOrThrow<string>("DB_PASSWORD"),
    }),
};
