import java.text.SimpleDateFormat
import java.util.Date

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

    // SQLite (если понадобится для хранения данных)
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")

    // Kotlin coroutines and serialization
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Assistant plugin dependencies - compileOnly (not included in client, only for compilation)
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

// === Assistant Plugin Build (Fat JAR) ===

// Конфигурация для зависимостей плагина
val assistantDeps: Configuration by configurations.creating {
    isTransitive = true
}

dependencies {
    // Assistant plugin dependencies (packaged into fat JAR)
    assistantDeps("dev.langchain4j:langchain4j:0.35.0")
    assistantDeps("dev.langchain4j:langchain4j-ollama:0.35.0")
    assistantDeps("com.microsoft.onnxruntime:onnxruntime:1.16.3")
}

// Генерация BuildInfo.kt с временем сборки
val generateBuildInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/source/buildinfo")
    outputs.dir(outputDir)

    doLast {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val timestamp = sdf.format(Date())
        val buildInfoFile = outputDir.get().file("com/bylins/client/assistant/BuildInfo.kt").asFile
        buildInfoFile.parentFile.mkdirs()
        buildInfoFile.writeText("""
            package com.bylins.client.assistant

            object BuildInfo {
                const val BUILD_TIME = "$timestamp"
            }
        """.trimIndent())
    }
}

sourceSets {
    main {
        kotlin {
            srcDir(layout.buildDirectory.dir("generated/source/buildinfo"))
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateBuildInfo)
}

val buildPlugin by tasks.registering(Jar::class) {
    group = "plugins"
    description = "Builds the Assistant plugin as a fat JAR with all dependencies"

    archiveFileName.set("assistant.jar")

    // Включаем классы плагина
    from(sourceSets.main.get().output)

    // plugin.yml в корень JAR
    from("src/main/resources") {
        include("plugin.yml")
    }

    // Включаем все зависимости плагина (fat JAR)
    from({
        assistantDeps.filter { it.name.endsWith(".jar") }.map { zipTree(it) }
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
        println("Assistant plugin built: ${archiveFile.get().asFile.absolutePath}")
        println("Size: ${archiveFile.get().asFile.length() / 1024 / 1024} MB")
        println("Ready for #plugin reload assistant")
    }
}
