import { Module } from "@nestjs/common";
import { UsersRepository } from "./users.repository";
import { AccountLookupRepository } from "./account-lookup.repository";
import { PasswordHasher } from "./password-hasher";

@Module({
  providers: [UsersRepository, AccountLookupRepository, PasswordHasher],
  exports: [UsersRepository, AccountLookupRepository, PasswordHasher],
})
export class UsersModule {}
