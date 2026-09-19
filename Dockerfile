# Stage 1: Build the Kotlin Telegram Bot Fat JAR
FROM gradle:8.11.1-jdk17 AS build
WORKDIR /home/gradle/project

# Copy ONLY the telegram-bot-kotlin directory and build independently
# This prevents Gradle from evaluating Android plugins on Render
COPY --chown=gradle:gradle telegram-bot-kotlin/ .

RUN gradle jar --no-daemon

# Stage 2: Minimal JRE Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /home/gradle/project/build/libs/*.jar app.jar

ENV TELEGRAM_BOT_TOKEN=""
ENV CARTESIA_API_KEY="sk_car_admin_2HeYiVT1N7jzkCHJAc92g8.uYLo8AtFqYXMS3oQo8egeh7xVjSy1HWYU7t3Q6rAypm"

CMD ["java", "-jar", "app.jar"]
