import { Module } from "@nestjs/common";
import { UsersRepository } from "./users.repository";
import { PasswordHasher } from "./password-hasher";

@Module({
  providers: [UsersRepository, PasswordHasher],
  exports: [UsersRepository, PasswordHasher],
})
export class UsersModule {}
