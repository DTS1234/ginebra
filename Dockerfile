# Build and run the game as a single self-contained image.
#
# The repo pins the Gradle toolchain to Java 17, so the build stage uses a JDK 17
# image and the question of which JDK is installed on the host disappears.

FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Dependencies first, so a source-only change does not re-download them.
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --quiet || true

COPY src src
RUN ./gradlew --no-daemon bootJar -x test --quiet

FROM eclipse-temurin:17-jre
WORKDIR /app

# Do not run the game as root.
RUN useradd --system --create-home --shell /usr/sbin/nologin ginebra
USER ginebra

COPY --from=build /app/build/libs/*.jar app.jar

# $PORT is what Fly, Render and Railway inject; 8080 is the local default.
ENV PORT=8080
EXPOSE 8080

# Games live in memory, so a container restart drops every table in progress.
# Deploy one instance and avoid rolling restarts until Phase 5 lands persistence.
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -jar app.jar"]
