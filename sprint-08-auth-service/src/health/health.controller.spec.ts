import { HealthController } from "./health.controller";

describe("HealthController", () => {
  it("reports up so the container healthcheck and orchestration depends_on can see readiness", () => {
    const controller = new HealthController();
    expect(controller.health()).toEqual({ status: "up" });
  });
});
