import { Body, Controller, Get, HttpCode, HttpStatus, Post, UseGuards } from "@nestjs/common";
import { ApiBearerAuth, ApiOperation, ApiResponse, ApiTags } from "@nestjs/swagger";
import { AuthService } from "./auth.service";
import { RegisterDto } from "./dto/register.dto";
import { LoginDto } from "./dto/login.dto";
import { RefreshDto } from "./dto/refresh.dto";
import { UserResponseDto } from "./dto/user-response.dto";
import { TokenResponseDto } from "./dto/token-response.dto";
import { JwtAuthGuard, VerifiedUser } from "./guards/jwt-auth.guard";
import { CurrentUser } from "./decorators/current-user.decorator";

@ApiTags("Auth")
@Controller("auth")
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Post("register")
  @ApiOperation({ summary: "Register a user" })
  @ApiResponse({ status: 201, type: UserResponseDto })
  @ApiResponse({ status: 409, description: "AUTH-409: username already taken" })
  @ApiResponse({ status: 422, description: "VAL-422: invalid input" })
  register(@Body() body: RegisterDto): Promise<UserResponseDto> {
    return this.authService.register(body);
  }

  @Post("login")
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: "Log in and receive tokens" })
  @ApiResponse({ status: 200, type: TokenResponseDto })
  @ApiResponse({ status: 401, description: "AUTH-401: unauthorised" })
  @ApiResponse({ status: 422, description: "VAL-422: invalid input" })
  login(@Body() body: LoginDto): Promise<TokenResponseDto> {
    return this.authService.login(body);
  }

  @Post("refresh")
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: "Exchange a refresh token for a new token pair" })
  @ApiResponse({ status: 200, type: TokenResponseDto })
  @ApiResponse({ status: 401, description: "AUTH-401: unauthorised" })
  @ApiResponse({ status: 422, description: "VAL-422: invalid input" })
  refresh(@Body() body: RefreshDto): Promise<TokenResponseDto> {
    return this.authService.refresh(body);
  }

  @Get("me")
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({ summary: "Get the authenticated user" })
  @ApiResponse({ status: 200, type: UserResponseDto })
  @ApiResponse({ status: 401, description: "AUTH-401: unauthorised" })
  me(@CurrentUser() user: VerifiedUser): Promise<UserResponseDto> {
    return this.authService.me(user);
  }
}
