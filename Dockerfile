# Используем лёгкий JRE 17
FROM eclipse-temurin:17-jre

# Рабочая директория в контейнере
WORKDIR /app

# Копируем собранный JAR в контейнер
COPY build/libs/*.jar app.jar

# Открываем порт 8080
EXPOSE 8080

# Запуск приложения
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

