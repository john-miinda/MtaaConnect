FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY . .

RUN chmod +x gradlew && \
    ./gradlew bootJar --no-daemon && \
    find build/libs -maxdepth 1 -name '*-SNAPSHOT.jar' ! -name '*-plain.jar' -exec cp {} /app/app.jar \;

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]
