import { createParamDecorator, ExecutionContext } from "@nestjs/common";
import type { RequestWithUser, VerifiedUser } from "../guards/jwt-auth.guard";

/**
 * Returns the identity verified by JwtAuthGuard. Deliberately not read from
 * query parameters or client-controlled headers (OWASP A01).
 */
export const CurrentUser = createParamDecorator((_data: unknown, ctx: ExecutionContext): VerifiedUser => {
  const request = ctx.switchToHttp().getRequest<RequestWithUser>();
  return request.user;
});
