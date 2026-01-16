FROM gradle:8.7-jdk17 AS build
WORKDIR /app
COPY . .
RUN ./gradlew clean bootJar -x test

FROM eclipse-temurin:17-jre
WORKDIR /app

# plain.jar 제외하고 실행 jar만 app.jar로 복사
RUN mkdir -p /app
COPY --from=build /app/build/libs /app/build/libs

# 실행 jar 선택 (plain 제외)
RUN set -eux; \
    JAR_PATH="$(ls -1 /app/build/libs/*.jar | grep -v plain | head -n 1)"; \
    echo "Using jar: ${JAR_PATH}"; \
    cp "${JAR_PATH}" /app/app.jar

ENV JAVA_OPTS=""
EXPOSE 8080
CMD ["sh", "-c", "java $JAVA_OPTS -Dserver.port=$PORT -jar /app/app.jar"]
