FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S threadtrace && adduser -S threadtrace -G threadtrace && mkdir -p /app/storage/evidence && chown -R threadtrace:threadtrace /app
USER threadtrace
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/app.jar"]
