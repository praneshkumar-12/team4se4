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
    // isGlobal so every module reads config through ConfigService instead
    // of process.env directly. No envFilePath: in a container the values
    // come from docker-compose's environment block (root .env), and on a
    // laptop a developer copies .env.example to .env in this folder.
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
