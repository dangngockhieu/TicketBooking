# ==============================================================================
# Multi-Stage Dockerfile for Virtual Waiting Room Queue Service
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY services/common services/common
COPY services/queue services/queue

RUN chmod +x mvnw && ./mvnw clean package -pl services/queue -am -DskipTests

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /workspace/services/queue/target/*.jar app.jar

USER appuser
EXPOSE 8087

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
