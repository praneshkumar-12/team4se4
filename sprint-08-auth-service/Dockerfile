# Stage 1: build. The TypeScript compiler and dev dependencies never need
# to exist in the image that actually runs the service.
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json tsconfig.build.json ./
COPY src ./src
RUN npm run build

# Stage 2: run. Only production dependencies and the compiled dist/ cross
# the boundary - no compiler, no *.spec.ts, no dev dependencies.
FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY --from=build /app/dist ./dist

# JWT_SECRET, the database connection and every other value that differs
# between a laptop and a container are supplied at runtime by the
# orchestration environment - see infra/README.md and .env.example. None
# of them are baked into this image.
EXPOSE 3000
CMD ["node", "dist/main.js"]
