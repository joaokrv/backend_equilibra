# syntax=docker/dockerfile:1

# Build com Java 25 para manter compatibilidade com <java.version>25 do pom.xml
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY . .
RUN chmod +x mvnw && ./mvnw -B -DskipTests clean package

# Runtime enxuto com Java 25
FROM eclipse-temurin:25-jre
WORKDIR /app

ENV TZ=America/Sao_Paulo
# Boot rápido em container restrito (Render free: 0.1 vCPU / 512MB):
# - TieredStopAtLevel=1: JIT só C1, acelera muito o startup
# - UseSerialGC: menor footprint e startup que G1 em <2 CPU
# - MaxRAMPercentage=75: usa o heap disponível sem estourar o limite do container
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=America/Sao_Paulo -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:MaxRAMPercentage=75.0"

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
