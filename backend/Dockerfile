# ---- Build stage: compile and package with the Maven wrapper ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Copy the wrapper first and warm the dependency cache for faster rebuilds.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src ./src
RUN ./mvnw -q -B clean package -DskipTests

# ---- Run stage: slim JRE with just the fat jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# Uploaded medical reports live here; mount a volume in production.
VOLUME ["/app/uploads"]
ENTRYPOINT ["java", "-jar", "app.jar"]
