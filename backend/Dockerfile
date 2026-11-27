# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN groupadd --system meeter \
 && useradd --system --gid meeter --home-dir /app --shell /usr/sbin/nologin meeter
WORKDIR /app
COPY --from=build /build/target/meeter-0.0.1-SNAPSHOT.jar /app/app.jar
RUN mkdir -p /app/data/photos && chown -R meeter:meeter /app/data
USER meeter

ENV SERVER_ADDRESS=0.0.0.0 \
    SERVER_PORT=8091 \
    APP_DEV_BOOTSTRAP=false \
    APP_AUTH_SECURE_COOKIE=true \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8091
VOLUME ["/app/data/photos"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
