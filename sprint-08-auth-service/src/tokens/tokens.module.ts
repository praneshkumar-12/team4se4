import { Module } from "@nestjs/common";
import { TokenService } from "./token.service";
import { RefreshTokenRepository } from "./refresh-token.repository";
import { RefreshTokenService } from "./refresh-token.service";

@Module({
  providers: [TokenService, RefreshTokenRepository, RefreshTokenService],
  exports: [TokenService, RefreshTokenService],
})
export class TokensModule {}
