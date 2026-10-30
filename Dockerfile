# ------------------------------------------------------------------------------
# Stage 1: Build React Native Web static SPA bundle (webapp/)
# ------------------------------------------------------------------------------
FROM node:22-alpine AS webapp-build
WORKDIR /webapp
COPY webapp/package*.json ./
RUN npm ci
COPY webapp/ ./
ENV EXPO_PUBLIC_API_URL=""
ENV EXPO_NO_TELEMETRY="1"
RUN npm run build:web

# ------------------------------------------------------------------------------
# Stage 2: Build Kotlin + Ktor server distribution (backend/)
# ------------------------------------------------------------------------------
FROM gradle:8.12-jdk17 AS backend-build
WORKDIR /backend
COPY backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/config ./config
COPY backend/src ./src
RUN gradle installDist --no-daemon

# ------------------------------------------------------------------------------
# Stage 3: Final Cloud Run container (Ktor API + Bundled Web SPA)
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=backend-build /backend/build/install/wheres-my-bus-backend /app
COPY --from=webapp-build /webapp/dist /app/static
ENV PORT=8080
ENV STATIC_ASSETS_DIR=/app/static
EXPOSE 8080
CMD ["/app/bin/wheres-my-bus-backend"]
