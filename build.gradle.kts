import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    id("org.jetbrains.compose") version "1.5.12"
}

group = "com.bylins"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Compose for Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Coroutines для асинхронной работы
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")

    // JSON для конфигов
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Логирование
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Apache Commons для telnet (базовая поддержка)
    implementation("commons-net:commons-net:3.10.0")

    // Scripting engines
    // Nashorn для JavaScript скриптов (standalone для JDK 15+)
    implementation("org.openjdk.nashorn:nashorn-core:15.4")
    // Jython для Python скриптов
    implementation("org.python:jython-standalone:2.7.3")
    // LuaJ для Lua скриптов
    implementation("org.luaj:luaj-jse:3.0.1")

    // SQLite для хранения карт
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")

    // YAML для plugin.yml
    implementation("org.yaml:snakeyaml:2.0")

    // Plugin modules
    // Note: Only plugins:core is a compile-time dependency
    // Bot plugin is loaded at runtime via PluginManager from build/run/plugins/bot.jar
    implementation(project(":plugins:core"))

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi"
        )
    }
}

compose.desktop {
    application {
        mainClass = "com.bylins.client.MainKt"

        // Принудительно UTF-8 для всего JVM
        jvmArgs += listOf(
            "-Dfile.encoding=UTF-8",
            "-Dstdout.encoding=UTF-8",
            "-Dstderr.encoding=UTF-8",
            "-Dsun.stdout.encoding=UTF-8",
            "-Dsun.stderr.encoding=UTF-8",
            "-DCONSOLE_CHARSET=UTF-8",
            "-Dbylins.plugins.dir=build/run/plugins"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Bylins Client"
            packageVersion = "1.0.0"

            // Icons (optional - uncomment when files exist)
            // windows { iconFile.set(project.file("src/main/resources/icon.ico")) }
            // linux { iconFile.set(project.file("src/main/resources/icon.png")) }
            // macOS { iconFile.set(project.file("src/main/resources/icon.icns")) }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

// === Prepare Run Directory ===

val prepareRun by tasks.registering(Copy::class) {
    group = "application"
    description = "Prepares run directory with plugins and scripts"

    dependsOn(":plugins:bot:buildPlugin")

    // Создаём директорию для плагинов
    doFirst {
        layout.buildDirectory.dir("run/plugins").get().asFile.mkdirs()
    }

    // Копируем JAR плагина
    from(project(":plugins:bot").layout.buildDirectory.file("libs/bot.jar"))
    into(layout.buildDirectory.dir("run/plugins"))
}

// Копирование скриптов
val prepareScripts by tasks.registering(Copy::class) {
    group = "application"
    description = "Copies scripts to run directory"

    from("scripts") {
        exclude("*.disabled")
    }
    into(layout.buildDirectory.dir("run/scripts"))
}

// Задача для полной подготовки директории запуска
val prepareRunDir by tasks.registering {
    group = "application"
    description = "Prepares complete run directory"

    dependsOn(prepareRun, prepareScripts)
}

// Обновляем run задачу чтобы зависела от prepareRun
afterEvaluate {
    tasks.findByName("run")?.dependsOn(prepareRunDir)
}

// === Packaging Tasks ===

val userHome: String = System.getProperty("user.home")
val userDataDir = file("$userHome/.bylins-client")
val scriptsDir = file("scripts")
val packageDir = layout.buildDirectory.dir("package")

// Task to create install script for Windows
val createInstallScript by tasks.registering {
    val outputFile = packageDir.map { it.file("install-userdata.bat") }
    outputs.file(outputFile)

    doLast {
        outputFile.get().asFile.parentFile.mkdirs()
        outputFile.get().asFile.writeText("""
            @echo off
            chcp 65001 >nul
            echo === Bylins Client Installer ===
            echo.

            set "TARGET=%USERPROFILE%\.bylins-client"

            if exist "userdata\config.json" (
                echo Installing config...
                if not exist "%TARGET%" mkdir "%TARGET%"
                copy /Y "userdata\config.json" "%TARGET%\" >nul
            )

            if exist "userdata\maps\maps.db" (
                echo Installing maps...
                if not exist "%TARGET%\maps" mkdir "%TARGET%\maps"
                copy /Y "userdata\maps\maps.db" "%TARGET%\maps\" >nul
            )

            echo.
            echo Done! User data installed to: %TARGET%
            echo.
            echo Now run "Bylins Client.exe" to start the client.
            pause
        """.trimIndent())
    }
}

// Task to create README
val createReadme by tasks.registering {
    val outputFile = packageDir.map { it.file("README.txt") }
    outputs.file(outputFile)

    doLast {
        outputFile.get().asFile.parentFile.mkdirs()
        outputFile.get().asFile.writeText("""
            ===============================================
                        BYLINS MUD CLIENT
            ===============================================

            БЫСТРЫЙ СТАРТ
            -------------
            1. Запусти install-userdata.bat (установит настройки и карту)
            2. Запусти "Bylins Client.exe"
            3. Готово!


            СОДЕРЖИМОЕ АРХИВА
            -----------------
            Bylins Client.exe    - Главный исполняемый файл
            app/                 - Файлы приложения (не трогать)
            runtime/             - Java Runtime (не трогать)
            scripts/             - Скрипты автоматизации
            userdata/            - Настройки и карта для установки
            install-userdata.bat - Установщик данных пользователя
            README.txt           - Этот файл


            РУЧНАЯ УСТАНОВКА
            ----------------
            Если install-userdata.bat не работает, скопируй вручную:

            ИЗ АРХИВА                      КУДА КОПИРОВАТЬ
            ─────────────────────────────────────────────────────────────
            userdata/config.json      ->   %USERPROFILE%\.bylins-client\config.json
            userdata/maps/maps.db     ->   %USERPROFILE%\.bylins-client\maps\maps.db

            Где %USERPROFILE% - это твоя домашняя папка, например:
            C:\Users\Vasya\.bylins-client\


            СТРУКТУРА ПАПОК
            ---------------
            После установки должно получиться:

            C:\Users\<ИМЯ>\.bylins-client\
            ├── config.json              <- Настройки, триггеры, алиасы, хоткеи
            └── maps\
                └── maps.db              <- База данных карты

            <ПАПКА С КЛИЕНТОМ>\
            ├── Bylins Client.exe        <- Запускай это
            └── scripts\
                └── bylins_msdp.js       <- Скрипт для MSDP/статус-панели


            ЧТО ВКЛЮЧЕНО В НАСТРОЙКИ
            ------------------------
            - Триггеры
            - Алиасы
            - Горячие клавиши
            - Профили подключения
            - Тема оформления
            - Настройки шрифтов


            ГОРЯЧИЕ КЛАВИШИ ПО УМОЛЧАНИЮ
            ----------------------------
            Numpad 8/2/4/6  - Движение (север/юг/запад/восток)
            Numpad 7/9/1/3  - Движение (сз/св/юз/юв)
            Numpad +/-      - Вверх/вниз
            Ctrl+L          - Очистить экран
            Tab             - Автодополнение команд


            ПРОБЛЕМЫ?
            ---------
            - Клиент не запускается: проверь что установлена Java 17+
            - Карта не загрузилась: проверь что maps.db в правильной папке
            - Скрипты не работают: проверь что папка scripts рядом с .exe

        """.trimIndent(), Charsets.UTF_8)
    }
}

// Main packaging task
val packageWithUserData by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Creates a distributable ZIP with app, scripts, and user data"

    dependsOn("createDistributable", createInstallScript, createReadme)

    archiveFileName.set("bylins-client-${version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))

    // App files
    from(layout.buildDirectory.dir("compose/binaries/main/app/Bylins Client")) {
        into("")
    }

    // Scripts (exclude .disabled files)
    from(scriptsDir) {
        into("scripts")
        exclude("*.disabled")
    }

    // User config
    from(file("$userDataDir/config.json")) {
        into("userdata")
    }

    // Maps database
    from(file("$userDataDir/maps/maps.db")) {
        into("userdata/maps")
    }

    // Install script and readme
    from(packageDir) {
        include("install-userdata.bat", "README.txt")
    }

    doLast {
        println("\n=== Package Complete ===")
        println("Output: ${archiveFile.get().asFile.absolutePath}")
        println("Size: ${archiveFile.get().asFile.length() / 1024 / 1024} MB")
    }
}

// Quick package without user data (just app + scripts)
val packageApp by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Creates a distributable ZIP with app and scripts only (no user data)"

    dependsOn("createDistributable")

    archiveFileName.set("bylins-client-${version}-app.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))

    from(layout.buildDirectory.dir("compose/binaries/main/app/Bylins Client")) {
        into("")
    }

    from(scriptsDir) {
        into("scripts")
        exclude("*.disabled")
    }
}

// JAR package (smaller, cross-platform, requires Java)
val packageJar by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Creates a lightweight ZIP with JAR (requires Java 17+)"

    dependsOn("packageUberJarForCurrentOS", createInstallScript, createReadme)

    archiveFileName.set("bylins-client-${version}-jar.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))

    // JAR file
    from(layout.buildDirectory.dir("compose/jars")) {
        include("*.jar")
    }

    // Scripts
    from(scriptsDir) {
        into("scripts")
        exclude("*.disabled")
    }

    // User data
    from(file("$userDataDir/config.json")) {
        into("userdata")
    }
    from(file("$userDataDir/maps/maps.db")) {
        into("userdata/maps")
    }

    // Install script and readme
    from(packageDir) {
        include("install-userdata.bat", "README.txt")
    }

    // Run script for Windows
    doFirst {
        val runScript = packageDir.get().file("run.bat").asFile
        runScript.parentFile.mkdirs()
        runScript.writeText("""
            @echo off
            java -jar bylins-client-windows-x64-${version}.jar
            pause
        """.trimIndent())
    }

    from(packageDir) {
        include("run.bat")
    }

    doLast {
        println("\n=== JAR Package Complete ===")
        println("Output: ${archiveFile.get().asFile.absolutePath}")
        println("Size: ${archiveFile.get().asFile.length() / 1024 / 1024} MB")
        println("Note: Requires Java 17+ installed")
    }
}
