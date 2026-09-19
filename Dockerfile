# Stage 1: Build the Kotlin fat JAR
FROM gradle:8.11.1-jdk17 AS build
WORKDIR /home/gradle/project

COPY --chown=gradle:gradle . .
RUN gradle :telegram-bot-kotlin:jar --no-daemon

# Stage 2: Minimal JRE Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /home/gradle/project/telegram-bot-kotlin/build/libs/*.jar app.jar

ENV TELEGRAM_BOT_TOKEN=""
ENV CARTESIA_API_KEY="sk_car_x62gquQgEdVchAVtPCxcue"

CMD ["java", "-jar", "app.jar"]
