FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-24 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:24-jre

WORKDIR /app

COPY --from=build /workspace/target/ticketBackend-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080 2000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
