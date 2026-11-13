FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:17.0.14_7-jre-jammy AS runtime
RUN apt-get update && apt-get install --no-install-recommends -y curl && rm -rf /var/lib/apt/lists/* \
    && groupadd --system navigation && useradd --system --gid navigation --home-dir /app navigation
WORKDIR /app
COPY --from=build --chown=navigation:navigation /workspace/target/navigation-shell-1.0.0.jar /app/application.jar
USER navigation
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -jar /app/application.jar --prepare-schema && exec java -jar /app/application.jar"]
