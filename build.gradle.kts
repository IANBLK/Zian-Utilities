plugins {
    kotlin("jvm") version "2.0.21" apply false
    id("net.neoforged.moddev") version "2.0.147" apply false
}

allprojects {
    group = providers.gradleProperty("mod_group_id").get()
    version = providers.gradleProperty("mod_version").get()

    repositories {
        mavenCentral()
    }
}
