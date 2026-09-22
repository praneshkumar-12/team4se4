import "reflect-metadata";
import { NestFactory } from "@nestjs/core";
import { ValidationPipe } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";
import { AppModule } from "./app.module";

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(AppModule, { bufferLogs: true });

  // The contract's error envelope is the only body a failure is allowed to
  // carry, so unknown fields and mistyped fields are rejected before a
  // handler ever runs, not reported later as a 500.
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );

  const config = app.get(ConfigService);
  const port = config.get<number>("PORT", 3000);

  await app.listen(port);
  // eslint-disable-next-line no-console
  console.log(`Auth service listening on port ${port}`);
}

bootstrap();
