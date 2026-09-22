import { ConflictException, Injectable, UnauthorizedException, UnprocessableEntityException } from "@nestjs/common";
import { UsersRepository, UserRecord, FOREIGN_KEY_VIOLATION, UNIQUE_VIOLATION, pgErrorCode } from "../users/users.repository";
import { PasswordHasher } from "../users/password-hasher";
import { TokenService } from "../tokens/token.service";
import { RefreshTokenService } from "../tokens/refresh-token.service";
import { VerifiedUser } from "./guards/jwt-auth.guard";
import { RegisterDto } from "./dto/register.dto";
import { LoginDto } from "./dto/login.dto";
import { RefreshDto } from "./dto/refresh.dto";
import { UserResponseDto } from "./dto/user-response.dto";
import { TokenResponseDto } from "./dto/token-response.dto";
import { Role } from "./dto/role";

@Injectable()
export class AuthService {
  constructor(
    private readonly users: UsersRepository,
    private readonly passwordHasher: PasswordHasher,
    private readonly tokenService: TokenService,
    private readonly refreshTokens: RefreshTokenService,
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
      return toUserResponse(user);
    } catch (err) {
      const code = pgErrorCode(err);
      if (code === UNIQUE_VIOLATION) {
        throw new ConflictException(); // AUTH-409: username already taken
      }
      if (code === FOREIGN_KEY_VIOLATION) {
        throw new UnprocessableEntityException(); // VAL-422: unknown accountId
      }
      throw err;
    }
  }

  async login(dto: LoginDto): Promise<TokenResponseDto> {
    const user = await this.users.findByUsername(dto.username);
    const matches = user ? await this.passwordHasher.verify(user.passwordHash, dto.password) : false;

    if (!user || !matches) {
      throw new UnauthorizedException();
    }

    return this.issueTokenPair(user);
  }

  async refresh(dto: RefreshDto): Promise<TokenResponseDto> {
    const { userId, refreshToken } = await this.refreshTokens.rotate(dto.refreshToken);
    const user = await this.users.findById(userId);

    if (!user) {
      throw new UnauthorizedException();
    }

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
