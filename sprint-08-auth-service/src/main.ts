import "reflect-metadata";
import { NestFactory } from "@nestjs/core";
import { HttpStatus, UnprocessableEntityException, ValidationPipe } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";
import { DocumentBuilder, SwaggerModule } from "@nestjs/swagger";
import { AppModule } from "./app.module";
import { RedactingLogger } from "./common/logger";

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(AppModule, {
    bufferLogs: true,
    logger: new RedactingLogger(),
  });

  // Reject unknown or mistyped fields before any handler runs. Status is 422
  // (VAL-422) rather than Nest's default 400.
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      errorHttpStatusCode: HttpStatus.UNPROCESSABLE_ENTITY,
      exceptionFactory: () => new UnprocessableEntityException(),
    }),
  );

  // OpenAPI document is generated from controller/DTO decorators.
  // UI at /docs, raw JSON at /docs/json.
  const openApiConfig = new DocumentBuilder()
    .setTitle("Auth service")
    .setDescription("Registration, login, token refresh and current-user lookup for the Enterprise Trading Platform.")
    .setVersion("1.0.0")
    .addBearerAuth()
    .build();
  const openApiDocument = SwaggerModule.createDocument(app, openApiConfig);
  SwaggerModule.setup("docs", app, openApiDocument, { jsonDocumentUrl: "docs/json" });

  const config = app.get(ConfigService);
  const port = config.get<number>("PORT", 3000);

  await app.listen(port);
  // eslint-disable-next-line no-console
  console.log(`Auth service listening on port ${port}`);
}

bootstrap();
