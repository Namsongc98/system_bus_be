# Backend Codex Instructions

Use this file for backend tasks in `ticket-system/`. Shared Codex assets live in
the repository root `.codex/`; do not recreate a nested project `.codex` folder.

## Project Scope

This is a Java 21, Spring Boot 3.3.6, multi-module Maven backend project for a
bus ticket system.

Modules:

- `common-library`: shared security, JWT, exception handling, Redis, Kafka,
  Feign, JPA, DTOs, enums, and utilities.
- `booking_ticket`: booking service on port `8081`; publishes booking events.
- `manage-revenue-ticket`: management and revenue service on port `8082`; owns
  CRUD, reporting, authentication, and event consumers.
- `Infrastructure`: Docker Compose setup for MySQL, Kafka, and Redis Cluster.

The root Maven project is an aggregator with packaging `pom`. Do not run it as a
Spring Boot application.

## Required Context

Before editing backend code, read the relevant files in this order:

1. `../AGENTS.md`
2. `../.codex/references/backend/project-context.md`
3. `../.codex/references/backend/rules/backend.md`
4. `../.codex/references/backend/rules/architecture.md`
5. The specific backend rule files for the task area, such as API, database,
   Kafka, Redis, Docker, testing, security, observability, or clean code.
6. The closest existing source files in the target module.

For review, refactor, bug fixing, or quality work, also read:

- `../.codex/references/backend/rules/clean-code.md`
- `../.codex/skills/backend-code-review/SKILL.md`

## Non-Negotiable Rules

- Keep backend work backend-only: Java, Spring Boot, Maven, Redis, Kafka,
  Docker, API, database, logging, exception handling, testing, and security.
- Do not delete, move, or rewrite documentation outside the requested scope.
- Do not modify runtime Redis data under `Infrastructure/redis-cluster/data`.
- Preserve user changes already present in the workspace.
- Prefer narrow, module-local changes over broad rewrites.
- Use existing package, naming, DTO, response, exception, and service patterns
  unless there is a clear defect.
- Use Flyway or Liquibase migrations for production schema changes; do not rely
  on Hibernate `ddl-auto=update` as the production migration strategy.
- Treat Kafka payloads, keys, topic names, and enum values as cross-service
  contracts.

## Build And Run

Build all modules:

```bash
mvn clean install
```

Build without tests only when tests are not relevant or are currently blocked:

```bash
mvn clean install -DskipTests
```

Run booking service:

```bash
mvn -f booking_ticket/pom.xml spring-boot:run
```

Run manage revenue service:

```bash
mvn -f manage-revenue-ticket/pom.xml spring-boot:run
```

When diagnosing startup failures, use `-e` and inspect the Spring Boot stack
trace above Maven's final `MojoExecutionException`.
