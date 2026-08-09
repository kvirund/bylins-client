rootProject.name = "bylins-client"

include(":plugins:core")
include(":plugins:assistant")
include(":plugins:ai-control")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
