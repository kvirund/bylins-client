@echo off
rem Запуск Bylins Client. Консольная версия (видно ошибки сборки).
rem Для запуска без окна консоли используйте bylins.vbs

setlocal

rem Переходим в папку проекта (там, где лежит этот файл)
cd /d "%~dp0"

rem --- Поиск JDK 17+ ---
if not defined JAVA_HOME (
    for %%D in (
        "C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
        "C:\Program Files\Eclipse Adoptium\jdk-17"
        "C:\Program Files\Java\jdk-17"
        "C:\Program Files\Microsoft\jdk-17"
    ) do (
        if exist "%%~D\bin\java.exe" (
            set "JAVA_HOME=%%~D"
            goto :java_found
        )
    )
    rem Последняя попытка: любой Adoptium JDK
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-*") do (
        if exist "%%~D\bin\java.exe" set "JAVA_HOME=%%~D"
    )
)
:java_found

if not defined JAVA_HOME (
    echo JDK не найден. Установите JDK 17+ или задайте переменную JAVA_HOME.
    pause
    exit /b 1
)

rem Доверять системному хранилищу сертификатов Windows (иначе Gradle не скачает зависимости)
set "GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT"

call "%~dp0gradlew.bat" run --console=plain
if errorlevel 1 (
    echo.
    echo Запуск завершился с ошибкой.
    pause
)

endlocal
