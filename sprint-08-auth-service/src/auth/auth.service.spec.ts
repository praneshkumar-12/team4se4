import { ConflictException, NotFoundException, UnauthorizedException, UnprocessableEntityException } from "@nestjs/common";
import { AuthService } from "./auth.service";
import { UsersRepository, UserRecord } from "../users/users.repository";
import { AccountLookupRepository } from "../users/account-lookup.repository";
import { PasswordHasher, getDummyPasswordHash } from "../users/password-hasher";
import { TokenService } from "../tokens/token.service";
import { RefreshTokenService } from "../tokens/refresh-token.service";
import { LoginThrottleService } from "./login-throttle.service";

const CALLER_IP = "203.0.113.1";
const EMAIL = "arun.kumar@example.com";

function buildUser(overrides: Partial<UserRecord> = {}): UserRecord {
  return {
    id: "8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f",
    username: EMAIL,
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

  const accountLookup = {
    findAccountIdByEmail: jest.fn().mockResolvedValue(1),
  } as unknown as jest.Mocked<AccountLookupRepository>;

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

  const throttle = new LoginThrottleService();

  const service = new AuthService(users, accountLookup, passwordHasher, tokenService, refreshTokens, throttle);
  return { service, users, accountLookup, passwordHasher, tokenService, refreshTokens, throttle };
}

describe("AuthService", () => {
  describe("register", () => {
    it("looks up the account by email and creates a user linked to it", async () => {
      const { service, users, accountLookup } = buildService();
      accountLookup.findAccountIdByEmail.mockResolvedValue(42);
      users.create.mockResolvedValue(buildUser({ accountId: 42 }));

      await service.register({ email: EMAIL, password: "Correct-Horse-Battery-9" });

      expect(accountLookup.findAccountIdByEmail).toHaveBeenCalledWith(EMAIL);
      expect(users.create).toHaveBeenCalledWith(
        expect.objectContaining({ username: EMAIL, accountId: 42, roles: ["CUSTOMER"] }),
      );
    });

    it("creates a user with CUSTOMER regardless of a caller-supplied roles field", async () => {
      const { service, users } = buildService();
      users.create.mockResolvedValue(buildUser());

      await service.register({ email: EMAIL, password: "Correct-Horse-Battery-9", roles: ["ADMIN"] });

      expect(users.create).toHaveBeenCalledWith(expect.objectContaining({ roles: ["CUSTOMER"] }));
    });

    it("issues no tokens on registration", async () => {
      const { service, users } = buildService();
      users.create.mockResolvedValue(buildUser());

      const result = await service.register({ email: EMAIL, password: "Correct-Horse-Battery-9" });

      expect(result).not.toHaveProperty("accessToken");
      expect(result).not.toHaveProperty("refreshToken");
    });

    it("rejects an email with no matching client with AUTH-404 (NotFoundException), without touching the users table", async () => {
      const { service, users, accountLookup } = buildService();
      accountLookup.findAccountIdByEmail.mockResolvedValue(null);

      await expect(
        service.register({ email: "nobody@example.com", password: "Correct-Horse-Battery-9" }),
      ).rejects.toThrow(NotFoundException);
      expect(users.create).not.toHaveBeenCalled();
    });

    it("maps a duplicate registration (email or account already taken) to AUTH-409 (ConflictException)", async () => {
      const { service, users } = buildService();
      users.create.mockRejectedValue({ code: "23505" });

      await expect(
        service.register({ email: EMAIL, password: "Correct-Horse-Battery-9" }),
      ).rejects.toThrow(ConflictException);
    });

    it("maps the account disappearing between lookup and insert to VAL-422 (UnprocessableEntityException)", async () => {
      const { service, users } = buildService();
      users.create.mockRejectedValue({ code: "23503" });

      await expect(
        service.register({ email: EMAIL, password: "Correct-Horse-Battery-9" }),
      ).rejects.toThrow(UnprocessableEntityException);
    });
  });

  describe("login", () => {
    it("issues an access token and a refresh token for a correct password", async () => {
      const { service, users, passwordHasher, refreshTokens } = buildService();
      users.findByUsername.mockResolvedValue(buildUser());
      passwordHasher.verify.mockResolvedValue(true);

      const result = await service.login({ email: EMAIL, password: "correct horse battery staple" }, CALLER_IP);

      expect(users.findByUsername).toHaveBeenCalledWith(EMAIL);
      expect(result.accessToken).toBe("signed.access.token");
      expect(result.refreshToken).toBe("issued-refresh-token");
      expect(result.tokenType).toBe("Bearer");
      expect(refreshTokens.issue).toHaveBeenCalledWith(buildUser().id);
    });

    it("refuses a wrong password with AUTH-401", async () => {
      const { service, users, passwordHasher } = buildService();
      users.findByUsername.mockResolvedValue(buildUser());
      passwordHasher.verify.mockResolvedValue(false);

      await expect(service.login({ email: EMAIL, password: "wrong" }, CALLER_IP)).rejects.toThrow(
        UnauthorizedException,
      );
    });

    it("refuses an unknown user with AUTH-401", async () => {
      const { service, users } = buildService();
      users.findByUsername.mockResolvedValue(null);

      await expect(service.login({ email: "nobody@example.com", password: "whatever" }, CALLER_IP)).rejects.toThrow(
        UnauthorizedException,
      );
    });

    it("verifies against the fixed dummy hash for an unknown user, not an early return (SEC4-628 uniform failure)", async () => {
      const { service, users, passwordHasher } = buildService();
      users.findByUsername.mockResolvedValue(null);
      passwordHasher.verify.mockResolvedValue(false);

      await expect(service.login({ email: "nobody@example.com", password: "whatever" }, CALLER_IP)).rejects.toThrow(
        UnauthorizedException,
      );

      const dummyHash = await getDummyPasswordHash();
      expect(passwordHasher.verify).toHaveBeenCalledWith(dummyHash, "whatever");
    });

    it("verifies against the real stored hash for a known user", async () => {
      const { service, users, passwordHasher } = buildService();
      const user = buildUser();
      users.findByUsername.mockResolvedValue(user);
      passwordHasher.verify.mockResolvedValue(false);

      await expect(service.login({ email: user.username, password: "wrong" }, CALLER_IP)).rejects.toThrow(
        UnauthorizedException,
      );

      expect(passwordHasher.verify).toHaveBeenCalledWith(user.passwordHash, "wrong");
    });

    it("throttles a caller after repeated failures, without touching the repository", async () => {
      const { service, users, passwordHasher } = buildService();
      users.findByUsername.mockResolvedValue(null);
      passwordHasher.verify.mockResolvedValue(false);

      for (let i = 0; i < LoginThrottleService.MAX_ATTEMPTS; i++) {
        await expect(service.login({ email: "nobody@example.com", password: "whatever" }, CALLER_IP)).rejects.toThrow(
          UnauthorizedException,
        );
      }

      users.findByUsername.mockClear();
      await expect(service.login({ email: "nobody@example.com", password: "whatever" }, CALLER_IP)).rejects.toThrow(
        UnauthorizedException,
      );
      expect(users.findByUsername).not.toHaveBeenCalled();
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

      expect(result.username).toBe(EMAIL);
      expect(users.findById).toHaveBeenCalledWith(buildUser().id);
    });
  });
});
