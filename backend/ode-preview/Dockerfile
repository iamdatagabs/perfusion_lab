FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace/backend/ode-preview
COPY backend/ode-preview/pom.xml ./pom.xml
COPY backend/src/main/java ../src/main/java
COPY backend/ode-preview/src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 appuser
COPY --from=build /workspace/backend/ode-preview/target/perfusionlab-ode-preview-0.2.0.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
