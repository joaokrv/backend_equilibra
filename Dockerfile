# syntax=docker/dockerfile:1

# Build com Java 25 para manter compatibilidade com <java.version>25 do pom.xml
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY . .
RUN chmod +x mvnw && ./mvnw -B -DskipTests clean package

# Runtime enxuto com Java 25
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
