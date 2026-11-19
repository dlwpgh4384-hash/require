FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
# Maven 중앙 저장소가 요청 제한(429)으로 막히면 구글 공식 복사본에서 받는다. /root/.m2는 부품 보관 볼륨이 붙을 수 있어 Maven 전체 설정 자리에 둔다(2026-11-19).
COPY maven-settings.xml /usr/share/maven/conf/settings.xml
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
