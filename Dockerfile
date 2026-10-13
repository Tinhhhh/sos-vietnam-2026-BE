# ==========================================
# Stage 1: Build JAR package with Maven
# ==========================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# Stage 2: Minimal runtime image with JRE 21
# ==========================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Tạo non-root user và thư mục uploads
RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
    && mkdir -p /app/uploads \
    && chown -R appuser:appgroup /app

# Copy file JAR từ build stage
COPY --from=builder --chown=appuser:appgroup /build/target/*.jar /app/app.jar

USER appuser

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
