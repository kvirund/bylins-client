plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.bylins"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
}

dependencies {
    // Dependency on plugins-core
    implementation(project(":plugins:core"))

    // SQLite для bot database
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")

    // Kotlin coroutines and serialization (needed for bot functionality)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // AI Bot plugin dependencies - compileOnly (not included in client, only for compilation)
    compileOnly("dev.langchain4j:langchain4j:0.35.0")
    compileOnly("dev.langchain4j:langchain4j-ollama:0.35.0")
    compileOnly("com.microsoft.onnxruntime:onnxruntime:1.16.3")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf(
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
        )
    }
}

// === AI Bot Plugin Build (Fat JAR) ===

// Конфигурация для зависимостей AI Bot плагина
val aibotDeps: Configuration by configurations.creating {
    isTransitive = true
}

dependencies {
    // AI Bot plugin dependencies (packaged into fat JAR)
    aibotDeps("dev.langchain4j:langchain4j:0.35.0")
    aibotDeps("dev.langchain4j:langchain4j-ollama:0.35.0")
    aibotDeps("com.microsoft.onnxruntime:onnxruntime:1.16.3")
}

val buildPlugin by tasks.registering(Jar::class) {
    group = "plugins"
    description = "Builds the AI Bot plugin as a fat JAR with all dependencies"

    archiveFileName.set("bot.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))

    // Включаем классы плагина
    from(sourceSets.main.get().output)

    // plugin.yml в корень JAR
    from("src/main/resources") {
        include("plugin.yml")
    }

    // Включаем все зависимости плагина (fat JAR)
    from({
        aibotDeps.filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        exclude("META-INF/MANIFEST.MF")
        exclude("META-INF/LICENSE*", "META-INF/NOTICE*")
        exclude("META-INF/versions/**")  // Исключаем multi-release классы
    }

    // Для предотвращения дублирования
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    dependsOn("classes")

    doLast {
        println("Bot plugin (fat JAR) built: ${archiveFile.get().asFile.absolutePath}")
        println("Size: ${archiveFile.get().asFile.length() / 1024 / 1024} MB")
    }
}
