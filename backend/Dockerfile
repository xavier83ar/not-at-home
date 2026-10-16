# syntax=docker/dockerfile:1

# Builds the backend from source, so nobody needs a JDK. The Makefile's `images` target still uses
# Buildpacks (`./mvnw spring-boot:build-image`); this file is for Compose (see demo/).

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Dependencies first, so a source change doesn't download them again.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
    && cp target/backend-*.jar app.jar

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --create-home app \
    && mkdir -p /var/lib/notathome/uploads \
    && chown app:app /var/lib/notathome/uploads
WORKDIR /app
COPY --from=build --chown=app:app /app/app.jar app.jar
USER app
EXPOSE 8080
# Set APP_UPLOADS_DIR to a volume if uploads must survive the container; a named volume mounted
# here starts out owned by `app`.
ENV APP_UPLOADS_DIR=/var/lib/notathome/uploads
ENTRYPOINT ["java", "-jar", "app.jar"]
