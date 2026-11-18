# Multi-stage Dockerfile for MahaMetro Competency Platform (Java 17 + Spring Boot + Tomcat)
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY competency-platform/pom.xml ./pom.xml
COPY competency-platform/src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
VOLUME /tmp
COPY --from=builder /app/target/competency.war app.war
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=dev
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.war"]
