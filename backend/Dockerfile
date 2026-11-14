# 백엔드(Spring Boot) 배포 이미지. 빌드와 실행을 나눠 실행 이미지에는 JRE와 jar만 둔다.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test && cp build/libs/*-SNAPSHOT.jar /app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app.jar app.jar
COPY docker-entrypoint.sh /usr/local/bin/
# Heroku Basic dyno는 메모리 512MB: 힙 · 코드 캐시 · 메타스페이스 · 스레드를 줄여 R14(메모리 초과)를 피한다
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=45 -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=48m -XX:MaxMetaspaceSize=160m -XX:TieredStopAtLevel=1" SERVER_TOMCAT_THREADS_MAX=40
EXPOSE 8080
ENTRYPOINT ["docker-entrypoint.sh"]
