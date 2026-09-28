# Build context = ticket-system/ (Infrastructure/app/docker-compose.yml and CI both use it).
# The jar is built inside the image, so no `mvn package` is needed before `docker compose up --build`.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# All module poms: the reactor reads every <module> even with -pl.
COPY pom.xml .
COPY common-library/pom.xml common-library/
COPY manage-revenue-ticket/pom.xml manage-revenue-ticket/
COPY booking_ticket/pom.xml booking_ticket/

COPY common-library/src common-library/src
COPY manage-revenue-ticket/src manage-revenue-ticket/src

# Tests run in CI (build-test job) and locally before commit; the image only packages.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl manage-revenue-ticket -am package -DskipTests

FROM eclipse-temurin:21-jre
# Non-root: app owns /app so it can create uploads/ (spring.web.resources.static-locations=file:uploads/)
RUN groupadd -r app && useradd -r -g app -u 1001 app
WORKDIR /app

COPY --from=build --chown=app:app /src/manage-revenue-ticket/target/manage-revenue-ticket-1.0.0.jar app.jar
RUN chown app:app /app

USER app

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
