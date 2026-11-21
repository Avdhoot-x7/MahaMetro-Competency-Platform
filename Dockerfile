# Multi-stage Docker build for Maha Metro Competency Assessment Platform
# Stage 1: Build with Maven
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build

# Copy project definition and source
COPY competency-platform/pom.xml .
COPY competency-platform/src ./src

# Build production executable artifact
RUN mvn clean package -DskipTests

# Stage 2: Minimal Production JRE Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install curl for healthcheck
RUN apk add --no-cache curl tzdata

# Set timezone to Asia/Kolkata
ENV TZ=Asia/Kolkata

# Copy compiled artifact and media assets
COPY --from=builder /build/target/competency.war app.war
COPY data/media /app/data/media

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

# Launch Spring Boot with dynamic port binding for cloud platforms
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.war"]
