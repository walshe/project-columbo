
# --- Build stage ---
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /app

# Copy pom first so dependency layer is cached separately
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

# --- Run stage ---
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
# Thin jar + separate lib/ dir (no Spring Boot fat-jar repackaging here - see design.md's
# containerization decision): maven-jar-plugin's Class-Path manifest entries reference lib/*.jar
# relative to the jar, so both must land in the same relative layout as Maven produced them.
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/target/lib ./lib
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
