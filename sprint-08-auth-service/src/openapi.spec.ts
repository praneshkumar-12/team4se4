import { Test } from "@nestjs/testing";
import { ConfigModule } from "@nestjs/config";
import { DocumentBuilder, SwaggerModule } from "@nestjs/swagger";
import { AuthController } from "./auth/auth.controller";
import { AuthService } from "./auth/auth.service";

/**
 * SEC4-630: the OpenAPI document is generated from the code, not
 * maintained by hand. This builds it the same way main.ts does (against a
 * minimal module carrying just the controller whose decorators matter
 * here) and asserts it describes all four contract routes - without
 * booting a database or an HTTP server. ConfigModule is real (not mocked):
 * @Get("me")'s JwtAuthGuard is resolved as a provider at app.init() even
 * though no request is ever made, so it needs a JWT_SECRET to construct.
 */
describe("OpenAPI document", () => {
  it("describes all four contract routes", async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [ConfigModule.forRoot({ isGlobal: true, ignoreEnvFile: true, load: [() => ({ JWT_SECRET: "openapi-spec-secret-at-least-32-characters" })] })],
      controllers: [AuthController],
      providers: [{ provide: AuthService, useValue: {} }],
    }).compile();

    const app = moduleRef.createNestApplication();
    await app.init();

    const document = SwaggerModule.createDocument(app, new DocumentBuilder().setTitle("Auth service").build());

    expect(Object.keys(document.paths).sort()).toEqual(["/auth/login", "/auth/me", "/auth/refresh", "/auth/register"]);
    expect(document.paths["/auth/register"].post).toBeDefined();
    expect(document.paths["/auth/login"].post).toBeDefined();
    expect(document.paths["/auth/refresh"].post).toBeDefined();
    expect(document.paths["/auth/me"].get).toBeDefined();

    await app.close();
  });
});
