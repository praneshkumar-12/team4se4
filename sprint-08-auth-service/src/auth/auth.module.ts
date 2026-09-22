import { Module } from "@nestjs/common";
import { AuthController } from "./auth.controller";
import { AuthService } from "./auth.service";
import { UsersModule } from "../users/users.module";
import { TokensModule } from "../tokens/tokens.module";
import { LoginThrottleService } from "./login-throttle.service";

@Module({
  imports: [UsersModule, TokensModule],
  controllers: [AuthController],
  providers: [AuthService, LoginThrottleService],
})
export class AuthModule {}
