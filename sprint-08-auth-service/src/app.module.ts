import { Module } from "@nestjs/common";
import { ConfigModule } from "@nestjs/config";
import { HealthController } from "./health/health.controller";
import { TokensModule } from "./tokens/tokens.module";

@Module({
  imports: [
    // isGlobal so every module reads config through ConfigService instead
    // of process.env directly. No envFilePath: in a container the values
    // come from docker-compose's environment block (root .env), and on a
    // laptop a developer copies .env.example to .env in this folder.
    ConfigModule.forRoot({ isGlobal: true }),
    TokensModule,
  ],
  controllers: [HealthController],
  providers: [],
})
export class AppModule {}
