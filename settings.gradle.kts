pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases/")
    }
}

rootProject.name = "zian-utilities"

include("core")
include("neoforge")
