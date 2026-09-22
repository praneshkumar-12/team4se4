import { ApiProperty } from "@nestjs/swagger";
import { IsString, MaxLength } from "class-validator";

export class LoginDto {
  @ApiProperty({ maxLength: 64, example: "priya.menon" })
  @IsString()
  @MaxLength(64)
  username!: string;

  @ApiProperty({ maxLength: 128, example: "correct horse battery staple" })
  @IsString()
  @MaxLength(128)
  password!: string;
}
