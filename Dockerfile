# 빌드 스테이지
FROM amazoncorretto:17-alpine-jdk AS builder
WORKDIR /app
COPY . .
RUN ./gradlew clean build -x test

FROM amazoncorretto:17-alpine-jdk
RUN addgroup --system spring && adduser --system spring --ingroup spring
WORKDIR /app
COPY --from=builder /app/build/libs/mcp-weather-starter-webmvc-server-*.jar app.jar
RUN chown spring:spring app.jar
USER spring
ENTRYPOINT ["java", "-Xmx512m", "-Xms512m", "-Dspring.ai.mcp.server.stdio=true", "-Dspring.main.web-application-type=none", "-Dspring.main.banner-mode=off", "-Dlogging.pattern.console=", "-jar", "app.jar"]

