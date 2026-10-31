# One image, two jars: the Spring Boot control app (with the bundled UI) and the runner it forks per run.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
# The Gradle Node plugin downloads its own Node for the UI build.
RUN ./gradlew --no-daemon :control:bootJar :runner:shadowJar

FROM eclipse-temurin:21-jre
# tini is PID 1: it reaps children and forwards signals, so a dead control JVM takes its runners with it.
RUN apt-get update && apt-get install -y --no-install-recommends tini && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /src/control/build/libs/control.jar /app/control.jar
COPY --from=build /src/runner/build/libs/runner.jar /app/runner.jar
USER 10001
ENV PRODACCESS_RUNNER_JAR=/app/runner.jar
ENTRYPOINT ["tini", "-g", "--", "java", "-XX:MaxRAMPercentage=50", "-jar", "/app/control.jar"]
