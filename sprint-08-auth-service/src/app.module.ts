import { Module } from "@nestjs/common";
import { APP_FILTER } from "@nestjs/core";
import { ConfigModule } from "@nestjs/config";
import { HealthController } from "./health/health.controller";
import { TokensModule } from "./tokens/tokens.module";
import { DbModule } from "./db/db.module";
import { UsersModule } from "./users/users.module";
import { AuthModule } from "./auth/auth.module";
import { PlatformExceptionFilter } from "./common/platform-exception.filter";

@Module({
  imports: [
    // Global so modules read config via ConfigService, not process.env.
    // No envFilePath: containers get values from docker-compose; local runs
    // use a .env copied from .env.example.
    ConfigModule.forRoot({ isGlobal: true }),
    DbModule,
    TokensModule,
    UsersModule,
    AuthModule,
  ],
  controllers: [HealthController],
  providers: [{ provide: APP_FILTER, useClass: PlatformExceptionFilter }],
})
export class AppModule {}
