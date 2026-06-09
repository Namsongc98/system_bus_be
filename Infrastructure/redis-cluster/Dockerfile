# ===============================
# STAGE 1: BUILD APP
# ===============================
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
FROM eclipse-temurin:17-jre

WORKDIR /app

# copy jar từ stage build
COPY --from=build /app/target/*.jar app.jar

# Spring profile
ENV SPRING_PROFILES_ACTIVE=prod

# expose port
EXPOSE 8082

# JVM tối ưu container
ENTRYPOINT ["java", \
"-XX:+UseContainerSupport", \
"-XX:MaxRAMPercentage=75", \
"-XX:+UseG1GC", \
"-jar", "app.jar"]
