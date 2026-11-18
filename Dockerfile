# 1. build: Maven and the JDK are needed only here and don't get into the final image
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# dependencies first: this layer is rebuilt only when pom.xml changes
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# tests need Docker for Testcontainers, so they run outside of the image: ./mvnw test
RUN mvn -B -ntp package -DskipTests

# 2. run: only the JRE and the jar
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system app
USER app

COPY --from=build /build/target/*.jar app.jar

# let the JVM use up to 75% of the container's memory instead of the default 25%
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
