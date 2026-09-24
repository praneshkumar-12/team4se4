import { Controller, Get } from "@nestjs/common";
import { ApiExcludeController } from "@nestjs/swagger";

// Container healthcheck only; excluded from the OpenAPI document.
@ApiExcludeController()
@Controller()
export class HealthController {
  @Get("health")
  health(): { status: string } {
    return { status: "up" };
  }
}
