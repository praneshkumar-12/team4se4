import { ArgumentsHost, Catch, ExceptionFilter, HttpException, HttpStatus, Logger } from "@nestjs/common";
import type { Response } from "express";

interface ErrorEnvelope {
  errorCode: "AUTH-401" | "AUTH-409" | "VAL-422";
  message: string;
}

/**
 * Returns every failure in the platform envelope {"errorCode", "message"},
 * consistent with the Trade REST API's GlobalExceptionHandler. Mapping is
 * by HTTP status rather than exception class, so the guard and controller
 * need no shared exception type.
 */
@Catch(HttpException)
export class PlatformExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger(PlatformExceptionFilter.name);

  catch(exception: HttpException, host: ArgumentsHost): void {
    const response = host.switchToHttp().getResponse<Response>();
    const status = exception.getStatus();
    const envelope = toEnvelope(status);

    if (!envelope) {
      // Unmapped status: keep Nest's default body instead of inventing an error code.
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
