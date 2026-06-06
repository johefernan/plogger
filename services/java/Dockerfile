FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/plogger-java-0.1.0.jar /app/logger.jar

CMD ["java", "-jar", "/app/logger.jar"]

