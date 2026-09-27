# ==============================================================================
# Multi-Stage Dockerfile for Spring Cloud Config Server
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY services/common services/common
COPY services/config-server services/config-server

RUN chmod +x mvnw && ./mvnw clean package -pl services/config-server -am -DskipTests

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /workspace/services/config-server/target/*.jar app.jar

USER appuser
EXPOSE 8888

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
