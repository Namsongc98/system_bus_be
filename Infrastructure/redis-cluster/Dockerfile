# ⚠ FILE CŨ, KHÔNG DÙNG (B29): KHÔNG phải image Redis.
# Đây là Dockerfile Spring Boot cũ (Java 17, cổng 8082 = manage-revenue-ticket) nằm nhầm thư mục.
# Không compose/CI nào tham chiếu: Redis dùng image redis:7-alpine (docker-compose.yml cùng thư mục);
# Dockerfile thật của 2 service: ../../manage-revenue-ticket/Dockerfile, ../../booking_ticket/Dockerfile
# (Java 21, build context ticket-system/). Chú thích bên dưới mô tả file như đang viết.

# ===============================
# STAGE 1: BUILD APP
# ===============================
# image có Maven + JDK 17 để build (dự án hiện dùng Java 21)
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml trước để cache dependency
COPY pom.xml .

RUN mvn -B -q -e -DskipTests dependency:go-offline

# Copy source code
COPY src ./src

# Build jar
RUN mvn clean package -DskipTests


# ===============================
# STAGE 2: RUN APP
# ===============================
# image chạy chỉ có JRE, nhẹ hơn stage build
FROM eclipse-temurin:17-jre

WORKDIR /app

# copy jar từ stage build
COPY --from=build /app/target/*.jar app.jar

# Spring profile: nạp application-prod.properties
ENV SPRING_PROFILES_ACTIVE=prod

# expose port — chỉ là tài liệu; cổng thật do `ports` trong compose quyết định
EXPOSE 8082

# JVM tối ưu container: đọc giới hạn RAM của container, heap tối đa 75% RAM đó, GC G1
ENTRYPOINT ["java", \
"-XX:+UseContainerSupport", \
"-XX:MaxRAMPercentage=75", \
"-XX:+UseG1GC", \
"-jar", "app.jar"]
