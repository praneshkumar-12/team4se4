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
   * Creates a user and links it to an existing trading account. Issues no
   * tokens: an unauthenticated route that mints a session is an
   * authentication bypass as soon as it has its first defect, so the
   * client logs in separately. Creates no trading account either -
   * accounts are owned by the Sprint 3 schema, and registering against an
   * unknown accountId fails validation (VAL-422), not creates one.
   */
  async register(dto: RegisterDto): Promise<UserResponseDto> {
    const passwordHash = await this.passwordHasher.hash(dto.password);

    try {
      const user = await this.users.create({
        username: dto.username,
        passwordHash,
        accountId: dto.accountId,
        // dto.roles is never read here - see the note on RegisterDto.
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
   * An unknown username and a wrong password get the same status, the
   * same body (PlatformExceptionFilter's single AUTH-401 envelope) and
   * comparable timing. The throttle answers before either branch does any
   * hashing, so a caller already over the limit doesn't get the oracle at
   * all; below the limit, every failure does one argon2id verification -
   * against the real hash for a known user, against the fixed dummy hash
   * (same algorithm, same cost) for an unknown one - so an unknown
   * username costs the same wall-clock time as a wrong password.
   */
  async login(dto: LoginDto, callerId: string): Promise<TokenResponseDto> {
    if (this.throttle.isThrottled(callerId)) {
      // No username here: which account someone is failing against is not
      // information the throttle log needs, and it keeps this line honest
      // that it's a rate observation, not an authentication attempt.
      this.logger.warn({ event: "login_throttled", callerId });
      throw new UnauthorizedException();
    }

    const user = await this.users.findByUsername(dto.username);
    const hashToVerify = user ? user.passwordHash : await getDummyPasswordHash();
    const matches = await this.passwordHasher.verify(hashToVerify, dto.password);

    if (!user || !matches) {
      this.throttle.registerFailure(callerId);
      // Username, not "unknown user" vs "wrong password" - that
      // distinction is exactly what the uniform-failure response must not
      // leak, and a log a support engineer can grep is still useful
      // without it.
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

  /** Reads identity from the verified token (see JwtAuthGuard), never from a client-supplied parameter. */
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
