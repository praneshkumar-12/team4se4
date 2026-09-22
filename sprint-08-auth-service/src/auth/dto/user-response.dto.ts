import { ApiProperty } from "@nestjs/swagger";
import { Role } from "./role";

export class UserResponseDto {
  @ApiProperty({ format: "uuid", description: "The value carried in the sub claim." })
  id!: string;

  @ApiProperty()
  username!: string;

  @ApiProperty({ type: Number, description: "The numeric trading account key, ACCOUNTS.account_id." })
  accountId!: number;

  @ApiProperty({ type: [String], enum: ["CUSTOMER", "ADMIN"] })
  roles!: Role[];

  @ApiProperty({ format: "date-time" })
  createdOn!: string;
}
