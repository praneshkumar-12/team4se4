import { ApiProperty } from "@nestjs/swagger";
import { IsArray, IsIn, IsInt, IsOptional, IsString, Matches, Max, MaxLength, Min, MinLength } from "class-validator";
import { ROLES } from "./role";

export class RegisterDto {
  @ApiProperty({ minLength: 3, maxLength: 64, pattern: "^[a-zA-Z0-9._-]+$", example: "priya.menon" })
  @IsString()
  @MinLength(3)
  @MaxLength(64)
  @Matches(/^[a-zA-Z0-9._-]+$/)
  username!: string;

  @ApiProperty({
    minLength: 12,
    maxLength: 128,
    description: "Twelve characters minimum. Length beats character-class rules.",
    example: "correct horse battery staple",
  })
  @IsString()
  @MinLength(12)
  @MaxLength(128)
  password!: string;

  @ApiProperty({ type: Number, minimum: 1, description: "The numeric trading account key, ACCOUNTS.account_id.", example: 1 })
  @IsInt()
  @Min(1)
  @Max(9223372036854775807)
  accountId!: number;

  // Accepted because the API schema allows it, but ignored: honouring a
  // self-declared role on a public route would allow privilege escalation.
  // AuthService.register always assigns CUSTOMER.
  @ApiProperty({ required: false, type: [String], enum: ROLES, description: "Ignored on this public route." })
  @IsOptional()
  @IsArray()
  @IsIn(ROLES, { each: true })
  roles?: string[];
}
