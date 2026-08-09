@echo off
rem Запуск Bylins Client. Консольная версия (видно ошибки сборки).
rem Для запуска без окна консоли используйте bylins.vbs

setlocal

rem Переходим в папку проекта (там, где лежит этот файл)
cd /d "%~dp0"

rem --- Поиск JDK 17+ ---
rem Сначала проверяем то, что уже задано в системе: значение бывает битым
rem (например, с лишней кавычкой на конце) — тогда путь к javaw не соберётся
rem и запуск молча провалится. Такое значение отбрасываем и ищем сами.
set "JAVA_HOME=%JAVA_HOME:"=%"
if not exist "%JAVA_HOME%\bin\javaw.exe" set "JAVA_HOME="

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
    if not defined BYLINS_NOPAUSE pause
    exit /b 1
)

rem Доверять системному хранилищу сертификатов Windows (иначе Gradle не скачает зависимости)
set "GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT"

rem --- Сборка свежей версии и снимок ---
rem Ярлык всегда запускает свежесобранную версию. Запуск идёт ИЗ СНИМКА
rem build/app/<версия>, а не из build/classes: иначе следующая пересборка
rem подменит .class-файлы под живой JVM, и клиент упадёт, дойдя до ленивой
rem загрузки изменённого класса. Каждая сборка — своя папка, поэтому
rem обновление не мешает уже запущенному экземпляру.
call "%~dp0gradlew.bat" stageApp --console=plain
if errorlevel 1 (
    echo Сборка не удалась.
    if not defined BYLINS_NOPAUSE pause
    exit /b 1
)

rem Текущая версия снимка
set /p APPVER=<"%~dp0build\app\current.txt"

"%JAVA_HOME%\bin\javaw.exe" ^
    -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 ^
    -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -DCONSOLE_CHARSET=UTF-8 ^
    -Dbylins.plugins.dir=build/app/%APPVER%/plugins ^
    -Dbylins.plugins.data.dir="%USERPROFILE%\.bylins-client\plugins" ^
    -cp "%~dp0build\app\deps\*;%~dp0build\app\%APPVER%\lib\*" com.bylins.client.MainKt
if errorlevel 1 (
    echo.
    echo Запуск завершился с ошибкой.
    rem BYLINS_NOPAUSE выставляет bylins.vbs: окно скрыто, ждать нечего
    if not defined BYLINS_NOPAUSE pause
)

endlocal
