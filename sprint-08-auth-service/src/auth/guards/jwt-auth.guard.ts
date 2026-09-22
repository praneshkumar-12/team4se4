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
 * Protects a route by verifying the bearer token's signature, algorithm,
 * expiry and issuer - in that order, because jsonwebtoken.verify refuses
 * all four before this method reads a single claim. A guard that decodes
 * first and checks the signature after accepts a tampered payload for as
 * long as it takes to notice.
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
      // algorithms: ["HS256"] pins the algorithm to the one the contract
      // fixes, so a token asking for "none" or a different algorithm is
      // refused rather than trusted because *something* looked signed.
      payload = jwt.verify(token, this.secret, {
        algorithms: ["HS256"],
        issuer: this.issuer,
      }) as jwt.JwtPayload;
    } catch {
      // Expired, tampered, wrongly signed and malformed all land here as
      // the same JsonWebTokenError/TokenExpiredError family. The contract
      // wants one AUTH-401 for all of them, so the distinction is not
      // preserved past this point.
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
