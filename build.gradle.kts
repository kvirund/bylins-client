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
    // Assistant plugin is loaded at runtime via PluginManager from plugins/assistant.jar
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
            "-DCONSOLE_CHARSET=UTF-8"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Bylins Client"
            packageVersion = "1.0.0"

            // JRE собирается через jlink, и по умолчанию в неё попадает голый
            // минимум — дистрибутив падал ещё до окна: сначала «java/sql/Driver»,
            // затем «jdk/dynalink/RelinkableCallSite».
            //
            // Список модулей тут не помогает: jdeps видит только статические
            // ссылки, а движки скриптов (Nashorn, Jython, LuaJ) и JDBC-драйверы
            // грузятся рефлексией и через ServiceLoader. Каждый такой модуль
            // всплывал бы у игрока по одному, причём не при запуске, а когда
            // он впервые полезет в скрипты. Полсотни мегабайт дешевле.
            includeAllModules = true

            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

// Только для запуска из Gradle: в дистрибутиве плагины лежат рядом с приложением,
// и зашитый сюда путь увёл бы установленный клиент искать их в build/run/plugins
tasks.matching { it.name == "run" }.configureEach {
    (this as? JavaExec)?.systemProperty("bylins.plugins.dir", "build/run/plugins")
}

// === Prepare Run Directory ===

val prepareRun by tasks.registering(Copy::class) {
    group = "application"
    description = "Prepares run directory with plugins and scripts"

    dependsOn(":plugins:assistant:buildPlugin", ":plugins:ai-control:buildPlugin")

    // Создаём директорию для плагинов
    doFirst {
        layout.buildDirectory.dir("run/plugins").get().asFile.mkdirs()
    }

    // Копируем JAR плагинов
    from(project(":plugins:assistant").layout.buildDirectory.file("libs/assistant.jar"))
    from(project(":plugins:ai-control").layout.buildDirectory.file("libs/ai-control.jar"))
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

// Обновление плагинов в УЖЕ ЗАПУЩЕННОМ клиенте.
//
// Перезагрузка плагина в UI читает JAR из снимка, зафиксированного при старте,
// поэтому свежую сборку туда нужно положить. JAR загруженного плагина занят
// classloader'ом — сначала выгрузите плагин в UI («Выгрузить»), затем эта
// задача, затем «Загрузить».
val deployPlugins by tasks.registering {
    group = "application"
    description = "Кладёт свежие плагины в активный снимок (для #plugin reload)"

    dependsOn(":plugins:assistant:buildPlugin", ":plugins:ai-control:buildPlugin")

    doLast {
        val appDir = layout.buildDirectory.dir("app").get().asFile
        val currentFile = File(appDir, "current.txt")
        if (!currentFile.exists()) {
            println("Снимок не найден — клиент ещё ни разу не запускался через bylins.bat")
            return@doLast
        }
        val target = File(appDir, currentFile.readText().trim() + "/plugins")

        listOf(
            project(":plugins:assistant").layout.buildDirectory.file("libs/assistant.jar"),
            project(":plugins:ai-control").layout.buildDirectory.file("libs/ai-control.jar")
        ).forEach { provider ->
            val jar = provider.get().asFile
            val dest = File(target, jar.name)
            val ok = runCatching { jar.copyTo(dest, overwrite = true) }.isSuccess
            println(if (ok) "обновлён: ${dest.name}" else "занят (выгрузите плагин в UI): ${dest.name}")
        }
    }
}

// Задача для полной подготовки директории запуска
val prepareRunDir by tasks.registering {
    group = "application"
    description = "Prepares complete run directory"

    dependsOn(prepareRun, prepareScripts)
}

// === Изолированный снимок приложения (build/app) ===
//
// Клиент запускается ИЗ СНИМКА, а не из build/classes. Иначе пересборка во
// время работы подменяет .class-файлы под живой JVM, и приложение падает,
// когда доходит до ленивой загрузки изменённого класса (NoClassDefFoundError).
// Снимок обновляется только этой задачей — обычные compileKotlin/buildPlugin
// его не касаются.
// Сторонние зависимости кладём отдельно и один раз: они весят сотни мегабайт
// и от правок кода не меняются, поэтому копировать их в каждый снимок незачем.
val stageDeps by tasks.registering(Sync::class) {
    group = "application"
    description = "Копирует зависимости приложения в build/app/deps"

    from(configurations.named("runtimeClasspath"))
    into(layout.buildDirectory.dir("app/deps"))
}

// Версионированный снимок: каждая сборка кладётся в свою папку, а ярлык
// читает build/app/current.txt. Так снимок можно обновлять ДАЖЕ при
// работающем клиенте — он продолжает читать свою (старую) папку, а
// следующий запуск подхватит новую.
val stageApp by tasks.registering {
    group = "application"
    description = "Собирает версионированный снимок приложения в build/app"

    dependsOn(stageDeps, tasks.named("jar"), ":plugins:assistant:buildPlugin", ":plugins:ai-control:buildPlugin")

    doLast {
        val appDir = layout.buildDirectory.dir("app").get().asFile
        val version = "v${System.currentTimeMillis()}"
        val target = File(appDir, version)

        copy {
            from(tasks.named("jar"))
            into(File(target, "lib"))
        }
        copy {
            from(project(":plugins:assistant").layout.buildDirectory.file("libs/assistant.jar"))
            from(project(":plugins:ai-control").layout.buildDirectory.file("libs/ai-control.jar"))
            into(File(target, "plugins"))
        }
        copy {
            from("scripts") { exclude("*.disabled") }
            into(File(target, "scripts"))
        }

        // Указатель для скрипта запуска
        File(appDir, "current.txt").writeText(version)

        // Чистим старые снимки, кроме текущего. Занятые запущенным клиентом
        // удалить не получится — это нормально, уберутся в следующий раз.
        appDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("v") && it.name != version }
            ?.forEach { runCatching { it.deleteRecursively() } }

        println("Снимок готов: ${target.absolutePath}")
    }
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

// --- Сборка для релиза ---

/** Метка платформы в имени архива: сборка Compose всегда под ОС, где её делали. */
val osTag: String = System.getProperty("os.name").lowercase().let { name ->
    when {
        name.contains("win") -> "windows"
        name.contains("mac") -> "macos"
        else -> "linux"
    }
}

/**
 * Готовый к запуску комплект: приложение с собственной JRE, плагины и скрипты.
 *
 * Отдельно от packageApp, потому что тот не кладёт плагины — а без ассистента и
 * ai-control клиент неполон. Собирается на каждой платформе своя: Compose тянет
 * платформозависимый Skiko, кроссплатформенного архива тут не бывает.
 *
 * Каталогом, а не архивом: GitHub Actions упаковывает артефакт сам, и готовый
 * zip внутри давал бы архив в архиве.
 */
val releaseDist by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Каталог для релиза: приложение, плагины и скрипты"

    dependsOn("createDistributable", ":plugins:assistant:buildPlugin", ":plugins:ai-control:buildPlugin")

    into(layout.buildDirectory.dir("release/bylins-client-$version-$osTag"))

    from(layout.buildDirectory.dir("compose/binaries/main/app/Bylins Client"))

    from(project(":plugins:assistant").layout.buildDirectory.file("libs/assistant.jar")) {
        into("plugins")
    }
    from(project(":plugins:ai-control").layout.buildDirectory.file("libs/ai-control.jar")) {
        into("plugins")
    }

    from(scriptsDir) {
        into("scripts")
        exclude("*.disabled")
    }

    // Мост для ИИ-агентов: чистый Python без зависимостей, полезен рядом с клиентом
    from("plugins/ai-control/mcp") {
        into("mcp")
        include("bylins_mcp.py")
    }
}

/** Тот же комплект архивом — для раздачи вручную, вне CI. */
val releaseBundle by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Архив для релиза: приложение, плагины и скрипты"

    dependsOn(releaseDist)

    archiveFileName.set("bylins-client-$version-$osTag.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(releaseDist.map { it.destinationDir })

    doLast {
        val file = archiveFile.get().asFile
        println("Архив: ${file.absolutePath} (${file.length() / 1024 / 1024} МБ)")
    }
}
