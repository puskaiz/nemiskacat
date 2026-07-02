# syntax=docker/dockerfile:1

# ---- Build stage: compile the backend and build the Refine admin SPA ----
# The frontend-maven-plugin downloads its own pinned Node (see <node.version> in
# pom.xml) during the generate-resources phase, so the build image only needs a
# JDK + Maven. The internal hu.deposoft starters resolve from the in-repo ./libs
# repository (see <repositories> in pom.xml) — no external registry needed.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy the build descriptor + vendored internal artifacts first for layer caching.
COPY pom.xml ./
COPY libs/ libs/

# Copy sources and build. Tests are skipped here on purpose: they use
# Testcontainers, which needs a Docker daemon that is not available in the
# platform build sandbox. Tests run in CI, not in the image build.
COPY src/ src/
COPY admin-ui/ admin-ui/
RUN mvn -B -e -DskipTests clean package

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as a non-root user.
RUN useradd --system --uid 10001 --home-dir /app appuser
COPY --from=build /app/target/*.jar /app/app.jar
USER appuser

# The app binds $PORT (server.port in application.yml); Railway injects it.
# Datasource + secrets come from environment variables (see README / .env.example).
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
