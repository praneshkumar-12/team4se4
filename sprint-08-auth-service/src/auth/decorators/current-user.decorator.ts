import { createParamDecorator, ExecutionContext } from "@nestjs/common";
import type { RequestWithUser, VerifiedUser } from "../guards/jwt-auth.guard";

/**
 * Reads the identity JwtAuthGuard already verified and attached to the
 * request. Never a query parameter or a header the client controls -
 * accepting either here is the OWASP A01 finding contracts/auth-api.yaml
 * warns /auth/me about by name.
 */
export const CurrentUser = createParamDecorator((_data: unknown, ctx: ExecutionContext): VerifiedUser => {
  const request = ctx.switchToHttp().getRequest<RequestWithUser>();
  return request.user;
});
