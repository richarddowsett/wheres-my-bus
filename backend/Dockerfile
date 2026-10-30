FROM gradle:8.12-jdk17 AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
COPY config ./config
COPY src ./src
RUN gradle installDist --no-daemon

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/build/install/wheres-my-bus-backend /app
EXPOSE 8080
ENV PORT=8080
CMD ["/app/bin/wheres-my-bus-backend"]
