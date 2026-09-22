import { ConflictException, UnauthorizedException, UnprocessableEntityException } from "@nestjs/common";
import { AuthService } from "./auth.service";
import { UsersRepository, UserRecord } from "../users/users.repository";
import { PasswordHasher } from "../users/password-hasher";
import { TokenService } from "../tokens/token.service";
import { RefreshTokenService } from "../tokens/refresh-token.service";

function buildUser(overrides: Partial<UserRecord> = {}): UserRecord {
  return {
    id: "8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f",
    username: "priya.menon",
    passwordHash: "$argon2id$fake",
    accountId: 1,
    roles: ["CUSTOMER"],
    createdAt: new Date("2026-10-05T08:00:00Z"),
    ...overrides,
  };
}

function buildService() {
  const users = {
    create: jest.fn(),
    findByUsername: jest.fn(),
    findById: jest.fn(),
  } as unknown as jest.Mocked<UsersRepository>;

  const passwordHasher = {
    hash: jest.fn().mockResolvedValue("$argon2id$fake"),
    verify: jest.fn(),
  } as unknown as jest.Mocked<PasswordHasher>;

  const tokenService = {
    createAccessToken: jest.fn().mockReturnValue("signed.access.token"),
  } as unknown as jest.Mocked<TokenService>;

  const refreshTokens = {
    issue: jest.fn().mockResolvedValue("issued-refresh-token"),
    rotate: jest.fn(),
  } as unknown as jest.Mocked<RefreshTokenService>;

  const service = new AuthService(users, passwordHasher, tokenService, refreshTokens);
  return { service, users, passwordHasher, tokenService, refreshTokens };
}

describe("AuthService", () => {
  describe("register", () => {
    it("creates a user with CUSTOMER regardless of a caller-supplied roles field", async () => {
      const { service, users } = buildService();
      users.create.mockResolvedValue(buildUser());

      await service.register({ username: "priya.menon", password: "correct horse battery staple", accountId: 1, roles: ["ADMIN"] });

      expect(users.create).toHaveBeenCalledWith(expect.objectContaining({ roles: ["CUSTOMER"] }));
    });

    it("issues no tokens on registration", async () => {
      const { service, users } = buildService();
      users.create.mockResolvedValue(buildUser());

      const result = await service.register({ username: "priya.menon", password: "correct horse battery staple", accountId: 1 });

      expect(result).not.toHaveProperty("accessToken");
      expect(result).not.toHaveProperty("refreshToken");
    });

    it("maps a duplicate username to AUTH-409 (ConflictException)", async () => {
      const { service, users } = buildService();
      users.create.mockRejectedValue({ code: "23505" });

      await expect(
        service.register({ username: "priya.menon", password: "correct horse battery staple", accountId: 1 }),
      ).rejects.toThrow(ConflictException);
    });

    it("maps an unknown accountId to VAL-422 (UnprocessableEntityException)", async () => {
      const { service, users } = buildService();
      users.create.mockRejectedValue({ code: "23503" });

      await expect(
        service.register({ username: "priya.menon", password: "correct horse battery staple", accountId: 999 }),
      ).rejects.toThrow(UnprocessableEntityException);
    });
  });

  describe("login", () => {
    it("issues an access token and a refresh token for a correct password", async () => {
      const { service, users, passwordHasher, refreshTokens } = buildService();
      users.findByUsername.mockResolvedValue(buildUser());
      passwordHasher.verify.mockResolvedValue(true);

      const result = await service.login({ username: "priya.menon", password: "correct horse battery staple" });

      expect(result.accessToken).toBe("signed.access.token");
      expect(result.refreshToken).toBe("issued-refresh-token");
      expect(result.tokenType).toBe("Bearer");
      expect(refreshTokens.issue).toHaveBeenCalledWith(buildUser().id);
    });

    it("refuses a wrong password with AUTH-401", async () => {
      const { service, users, passwordHasher } = buildService();
      users.findByUsername.mockResolvedValue(buildUser());
      passwordHasher.verify.mockResolvedValue(false);

      await expect(service.login({ username: "priya.menon", password: "wrong" })).rejects.toThrow(UnauthorizedException);
    });

    it("refuses an unknown user with AUTH-401", async () => {
      const { service, users } = buildService();
      users.findByUsername.mockResolvedValue(null);

      await expect(service.login({ username: "nobody", password: "whatever" })).rejects.toThrow(UnauthorizedException);
    });
  });

  describe("refresh", () => {
    it("returns a new access token and the rotated refresh token", async () => {
      const { service, users, refreshTokens } = buildService();
      refreshTokens.rotate.mockResolvedValue({ userId: buildUser().id, refreshToken: "new-refresh-token" });
      users.findById.mockResolvedValue(buildUser());

      const result = await service.refresh({ refreshToken: "old-refresh-token" });

      expect(result.accessToken).toBe("signed.access.token");
      expect(result.refreshToken).toBe("new-refresh-token");
    });
  });

  describe("me", () => {
    it("returns the user identified by the verified token's sub claim", async () => {
      const { service, users } = buildService();
      users.findById.mockResolvedValue(buildUser());

      const result = await service.me({ sub: buildUser().id, accountId: 1, roles: ["CUSTOMER"] });

      expect(result.username).toBe("priya.menon");
      expect(users.findById).toHaveBeenCalledWith(buildUser().id);
    });
  });
});
