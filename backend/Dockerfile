FROM eclipse-temurin:27-jdk AS build
WORKDIR /app
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY config ./config
COPY src ./src
RUN chmod +x ./gradlew && ./gradlew installDist --no-daemon

FROM eclipse-temurin:27-jre
WORKDIR /app
COPY --from=build /app/build/install/wheres-my-bus-backend /app
EXPOSE 8080
ENV PORT=8080
CMD ["/app/bin/wheres-my-bus-backend"]
