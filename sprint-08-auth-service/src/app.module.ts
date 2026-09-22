import { Module } from "@nestjs/common";
import { ConfigModule } from "@nestjs/config";
import { HealthController } from "./health/health.controller";
import { TokensModule } from "./tokens/tokens.module";
import { DbModule } from "./db/db.module";
import { UsersModule } from "./users/users.module";

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
  ],
  controllers: [HealthController],
  providers: [],
})
export class AppModule {}
