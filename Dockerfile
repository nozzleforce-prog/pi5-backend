# Backend Dockerfile for demo app with Java 24
FROM eclipse-temurin:24-jdk-alpine

WORKDIR /app

# Copy the Spring Boot jar into the container
COPY target/ticketBackend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
