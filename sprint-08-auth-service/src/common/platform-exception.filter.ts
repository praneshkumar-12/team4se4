import { ArgumentsHost, Catch, ExceptionFilter, HttpException, HttpStatus, Logger } from "@nestjs/common";
import type { Response } from "express";

interface ErrorEnvelope {
  errorCode: "AUTH-401" | "AUTH-409" | "VAL-422";
  message: string;
}

/**
 * Every failure this service returns leaves in the platform envelope,
 * {"errorCode": ..., "message": ...}, and nothing else - matching the
 * Trade REST API's GlobalExceptionHandler and giving Sprint 9's UI one
 * error handler for the whole platform. Mapped by HTTP status, not by
 * exception class: any handler that throws UnauthorizedException,
 * ConflictException or a 422 gets the right envelope without importing a
 * shared exception type, which is what keeps the guard (SEC4-626) and the
 * controller (SEC4-623) free to be built independently.
 */
@Catch(HttpException)
export class PlatformExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger(PlatformExceptionFilter.name);

  catch(exception: HttpException, host: ArgumentsHost): void {
    const response = host.switchToHttp().getResponse<Response>();
    const status = exception.getStatus();
    const envelope = toEnvelope(status);

    if (!envelope) {
      // Outside the four contract status codes (a route this service
      // doesn't expose, for instance): fall back to Nest's default shape
      // rather than inventing a fifth errorCode the contract never fixed.
      response.status(status).json(exception.getResponse());
      return;
    }

    if (status >= HttpStatus.INTERNAL_SERVER_ERROR) {
      this.logger.error(exception.message, exception.stack);
    }

    response.status(status).json(envelope);
  }
}

function toEnvelope(status: number): ErrorEnvelope | null {
  switch (status) {
    case HttpStatus.UNAUTHORIZED:
      return { errorCode: "AUTH-401", message: "Unauthorised" };
    case HttpStatus.CONFLICT:
      return { errorCode: "AUTH-409", message: "Registration failed" };
    case HttpStatus.UNPROCESSABLE_ENTITY:
      return { errorCode: "VAL-422", message: "Invalid input" };
    default:
      return null;
  }
}
