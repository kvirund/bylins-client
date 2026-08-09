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
    implementation(project(":plugins:core"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

// === Сборка плагина в JAR ===
// HTTP-сервер берём из JDK (com.sun.net.httpserver), поэтому fat JAR не нужен:
// собственных внешних зависимостей у плагина нет.
val buildPlugin by tasks.registering(Jar::class) {
    group = "plugins"
    description = "Builds the AI control plugin JAR"

    archiveFileName.set("ai-control.jar")

    from(sourceSets.main.get().output)
    from("src/main/resources") {
        include("plugin.yml")
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn("classes")

    doLast {
        println("AI control plugin built: ${archiveFile.get().asFile.absolutePath}")
    }
}
