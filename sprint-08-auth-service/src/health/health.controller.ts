import { Controller, Get } from "@nestjs/common";
import { ApiExcludeController } from "@nestjs/swagger";

// Operational plumbing for the container healthcheck (docker-compose), not
// part of contracts/auth-api.yaml, so it stays out of the served OpenAPI
// document.
@ApiExcludeController()
@Controller()
export class HealthController {
  @Get("health")
  health(): { status: string } {
    return { status: "up" };
  }
}
