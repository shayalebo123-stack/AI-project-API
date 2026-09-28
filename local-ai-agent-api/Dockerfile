# ---------- Stage 1: build the fat jar ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copy only the pom first so the dependency layer is cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests run in CI (and locally with "mvn test"); skip them here to keep the image build fast
RUN mvn -B -q package -DskipTests

# ---------- Stage 2: slim runtime image ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Do not run as root
RUN groupadd --system app && useradd --system --gid app --no-create-home app

COPY --from=build /build/target/local-ai-agent.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
