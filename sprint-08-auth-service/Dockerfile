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

# node:20-alpine runs as root by default. This is the SEC4-631 security
# review's A05 finding: the built-in, unprivileged `node` user (uid 1000)
# is enough for this image - it never binds a port below 1024 and never
# writes outside /app - so it drops to it before the process starts.
USER node

# JWT_SECRET, the database connection and every other value that differs
# between a laptop and a container are supplied at runtime by the
# orchestration environment - see infra/README.md and .env.example. None
# of them are baked into this image.
EXPOSE 3000
CMD ["node", "dist/main.js"]
