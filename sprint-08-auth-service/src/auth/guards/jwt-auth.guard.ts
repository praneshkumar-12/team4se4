import { CanActivate, ExecutionContext, Injectable, UnauthorizedException } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";
import * as jwt from "jsonwebtoken";
import type { Request } from "express";

/** The subset of verified claims a route handler is allowed to trust. */
export interface VerifiedUser {
  sub: string;
  accountId: number;
  roles: string[];
}

export type RequestWithUser = Request & { user: VerifiedUser };

const BEARER_PREFIX = "Bearer ";

/**
 * Verifies the bearer token (signature, algorithm, expiry, issuer) before
 * any claim is read.
 */
@Injectable()
export class JwtAuthGuard implements CanActivate {
  private readonly secret: string;
  private readonly issuer: string;

  constructor(config: ConfigService) {
    this.secret = config.getOrThrow<string>("JWT_SECRET");
    this.issuer = config.get<string>("JWT_ISSUER", "auth-service");
  }

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<Request>();
    const header = request.headers.authorization;

    if (!header || !header.startsWith(BEARER_PREFIX) || header.length <= BEARER_PREFIX.length) {
      throw new UnauthorizedException();
    }

    const token = header.slice(BEARER_PREFIX.length);

    let payload: jwt.JwtPayload;
    try {
      // Algorithm is pinned to HS256, so "none" or other algorithms are rejected.
      payload = jwt.verify(token, this.secret, {
        algorithms: ["HS256"],
        issuer: this.issuer,
      }) as jwt.JwtPayload;
    } catch {
      // Every token failure maps to a single AUTH-401 response.
      throw new UnauthorizedException();
    }

    if (typeof payload.sub !== "string" || typeof payload.accountId !== "number" || !Array.isArray(payload.roles)) {
      throw new UnauthorizedException();
    }

    (request as RequestWithUser).user = {
      sub: payload.sub,
      accountId: payload.accountId,
      roles: payload.roles as string[],
    };

    return true;
  }
}
