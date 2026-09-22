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

  // The contract's error envelope is the only body a failure is allowed to
  // carry, so unknown fields and mistyped fields are rejected before a
  // handler ever runs, not reported later as a 500. errorHttpStatusCode:
  // 422 so a validation failure lands on VAL-422 (the contract's code),
  // not Nest's 400 default - PlatformExceptionFilter maps by status.
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      errorHttpStatusCode: HttpStatus.UNPROCESSABLE_ENTITY,
      exceptionFactory: () => new UnprocessableEntityException(),
    }),
  );

  // Generated from the @ApiTags/@ApiOperation/@ApiResponse decorators on
  // AuthController and the @ApiProperty decorators on the DTOs (SEC4-623),
  // not maintained by hand - the document served here is evidence that the
  // running code still matches contracts/auth-api.yaml, not a replacement
  // for it. Two paths, chosen and recorded in this service's README: the
  // human page at /docs, the JSON document at /docs/json.
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
