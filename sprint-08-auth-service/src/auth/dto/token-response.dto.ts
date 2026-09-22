import { ApiProperty } from "@nestjs/swagger";

export class TokenResponseDto {
  @ApiProperty({ description: "Signed JWT carrying the claims contract." })
  accessToken!: string;

  @ApiProperty({ description: "Opaque, stored server-side, revocable, rotated on every use." })
  refreshToken!: string;

  @ApiProperty({ enum: ["Bearer"] })
  tokenType!: "Bearer";

  @ApiProperty({ description: "Access token lifetime in seconds.", example: 900 })
  expiresIn!: number;
}
