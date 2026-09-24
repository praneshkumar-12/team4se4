import { ConflictException, Injectable, Logger, UnauthorizedException, UnprocessableEntityException } from "@nestjs/common";
import { UsersRepository, UserRecord, FOREIGN_KEY_VIOLATION, UNIQUE_VIOLATION, pgErrorCode } from "../users/users.repository";
import { PasswordHasher, getDummyPasswordHash } from "../users/password-hasher";
import { TokenService } from "../tokens/token.service";
import { RefreshTokenService } from "../tokens/refresh-token.service";
import { VerifiedUser } from "./guards/jwt-auth.guard";
import { RegisterDto } from "./dto/register.dto";
import { LoginDto } from "./dto/login.dto";
import { RefreshDto } from "./dto/refresh.dto";
import { UserResponseDto } from "./dto/user-response.dto";
import { TokenResponseDto } from "./dto/token-response.dto";
import { Role } from "./dto/role";
import { LoginThrottleService } from "./login-throttle.service";

@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);

  constructor(
    private readonly users: UsersRepository,
    private readonly passwordHasher: PasswordHasher,
    private readonly tokenService: TokenService,
    private readonly refreshTokens: RefreshTokenService,
    private readonly throttle: LoginThrottleService,
  ) {}

  /**
   * Registers a user against an existing trading account. No tokens are
   * issued here; the client must log in separately. Accounts are owned by
   * the database schema, so an unknown accountId is rejected (VAL-422).
   */
  async register(dto: RegisterDto): Promise<UserResponseDto> {
    const passwordHash = await this.passwordHasher.hash(dto.password);

    try {
      const user = await this.users.create({
        username: dto.username,
        passwordHash,
        accountId: dto.accountId,
        // Roles are never taken from the client (see RegisterDto).
        roles: ["CUSTOMER"],
      });
      this.logger.log({ event: "user_registered", username: user.username, accountId: user.accountId });
      return toUserResponse(user);
    } catch (err) {
      const code = pgErrorCode(err);
      if (code === UNIQUE_VIOLATION) {
        this.logger.warn({ event: "registration_conflict", username: dto.username });
        throw new ConflictException(); // AUTH-409: username already taken
      }
      if (code === FOREIGN_KEY_VIOLATION) {
        this.logger.warn({ event: "registration_unknown_account", accountId: dto.accountId });
        throw new UnprocessableEntityException(); // VAL-422: unknown accountId
      }
      throw err;
    }
  }

  /**
   * Unknown username and wrong password produce the same AUTH-401 response
   * and take comparable time: an unknown user is verified against a dummy
   * argon2id hash of the same cost, so timing does not reveal which case
   * occurred. Throttled callers are rejected before any hashing.
   */
  async login(dto: LoginDto, callerId: string): Promise<TokenResponseDto> {
    if (this.throttle.isThrottled(callerId)) {
      this.logger.warn({ event: "login_throttled", callerId });
      throw new UnauthorizedException();
    }

    const user = await this.users.findByUsername(dto.username);
    const hashToVerify = user ? user.passwordHash : await getDummyPasswordHash();
    const matches = await this.passwordHasher.verify(hashToVerify, dto.password);

    if (!user || !matches) {
      this.throttle.registerFailure(callerId);
      // Log does not distinguish unknown user from wrong password.
      this.logger.warn({ event: "login_failed", username: dto.username, callerId });
      throw new UnauthorizedException();
    }

    this.throttle.registerSuccess(callerId);
    this.logger.log({ event: "login_succeeded", username: user.username, callerId });
    return this.issueTokenPair(user);
  }

  async refresh(dto: RefreshDto): Promise<TokenResponseDto> {
    const { userId, refreshToken } = await this.refreshTokens.rotate(dto.refreshToken);
    const user = await this.users.findById(userId);

    if (!user) {
      throw new UnauthorizedException();
    }

    this.logger.log({ event: "refresh_succeeded", userId: user.id });
    return {
      accessToken: this.tokenService.createAccessToken(toTokenSubject(user)),
      refreshToken,
      tokenType: "Bearer",
      expiresIn: TokenService.ACCESS_TOKEN_TTL_SECONDS,
    };
  }

  /** Identity comes from the verified token (JwtAuthGuard), never from client input. */
  async me(verified: VerifiedUser): Promise<UserResponseDto> {
    const user = await this.users.findById(verified.sub);

    if (!user) {
      throw new UnauthorizedException();
    }

    return toUserResponse(user);
  }

  private async issueTokenPair(user: UserRecord): Promise<TokenResponseDto> {
    const accessToken = this.tokenService.createAccessToken(toTokenSubject(user));
    const refreshToken = await this.refreshTokens.issue(user.id);

    return { accessToken, refreshToken, tokenType: "Bearer", expiresIn: TokenService.ACCESS_TOKEN_TTL_SECONDS };
  }
}

function toTokenSubject(user: UserRecord): { id: string; accountId: number; roles: string[] } {
  return { id: user.id, accountId: user.accountId, roles: user.roles };
}

function toUserResponse(user: UserRecord): UserResponseDto {
  return {
    id: user.id,
    username: user.username,
    accountId: user.accountId,
    roles: user.roles as Role[],
    createdOn: user.createdAt.toISOString(),
  };
}
