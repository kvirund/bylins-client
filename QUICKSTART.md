# Быстрый старт

## 1. Установка Java

Убедитесь, что у вас установлена Java 17 или выше:

```bash
java -version
```

Если Java не установлена, скачайте:
- Windows: https://adoptium.net/temurin/releases/
- Или используйте любой другой дистрибутив JDK 17+

## 2. Скачивание Gradle Wrapper JAR

Gradle wrapper jar нужно скачать вручную (из-за ограничений Git):

```bash
# Windows (PowerShell)
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar" -OutFile "gradle/wrapper/gradle-wrapper.jar"

# Linux/Mac
curl -o gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar
```

Или скачайте файл напрямую и поместите в `gradle/wrapper/gradle-wrapper.jar`

## 3. Запуск

```bash
# Windows
gradlew.bat run

# Linux/Mac
chmod +x gradlew
./gradlew run
```

При первом запуске Gradle автоматически скачает все зависимости.

## 4. Подключение к Былинам

1. В открывшемся окне в поле Host оставьте `mud.bylins.su`
2. В поле Port оставьте `4000`
3. Нажмите "Подключиться"

## Возможные проблемы

### Gradle не находит Java
Установите переменную окружения JAVA_HOME:
```bash
# Windows
set JAVA_HOME=C:\Program Files\Java\jdk-17

# Linux/Mac
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
```

### Ошибка при сборке
Убедитесь что gradle-wrapper.jar скачан в `gradle/wrapper/gradle-wrapper.jar`

## Следующие шаги

После успешного запуска можно:
- Подключиться к серверу Былин
- Начать разрабатывать триггеры и алиасы
- Настроить автомаппер
- Добавить плагины
