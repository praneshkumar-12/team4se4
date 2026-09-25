import { ApiProperty } from "@nestjs/swagger";
import { IsArray, IsEmail, IsIn, IsOptional, IsString, MaxLength, MinLength } from "class-validator";
import { PasswordComplexity } from "./password-rules";
import { ROLES } from "./role";

export class RegisterDto {
  @ApiProperty({
    maxLength: 255,
    description: "Must match an existing client's email (clients.email). The trading account to link is inferred from it - accountId is never supplied directly.",
    example: "arun.kumar@example.com",
  })
  @IsEmail()
  @MaxLength(255)
  email!: string;

  @ApiProperty({
    minLength: 12,
    maxLength: 128,
    description: "12-128 characters, with at least one uppercase letter, lowercase letter, digit and symbol.",
    example: "Correct-Horse-Battery-9",
  })
  @IsString()
  @MinLength(12)
  @MaxLength(128)
  @PasswordComplexity()
  password!: string;

  // Accepted because the API schema allows it, but ignored: honouring a
  // self-declared role on a public route would allow privilege escalation.
  // AuthService.register always assigns CUSTOMER.
  @ApiProperty({ required: false, type: [String], enum: ROLES, description: "Ignored on this public route." })
  @IsOptional()
  @IsArray()
  @IsIn(ROLES, { each: true })
  roles?: string[];
}
