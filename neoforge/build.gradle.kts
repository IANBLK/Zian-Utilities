plugins {
    java
    id("net.neoforged.moddev")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://artefacts.cobblemon.com/releases/")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
    maven("https://maven.neoforged.net/releases/")
}

neoForge {
    version = providers.gradleProperty("neo_version").get()

    runs {
        create("server") {
            server()
            programArgument("--nogui")
        }
    }

    mods {
        create(providers.gradleProperty("mod_id").get()) {
            sourceSet(sourceSets.main.get())
        }
    }

    unitTest {
        enable()
        testedMod = mods.getByName(providers.gradleProperty("mod_id").get())
    }
}

dependencies {
    implementation(project(":core"))

    compileOnly("com.cobblemon:neoforge:${providers.gradleProperty("cobblemon_version").get()}") {
        isTransitive = false
    }

    compileOnly("thedarkcolour:kotlinforforge-neoforge:${providers.gradleProperty("kotlin_for_forge_version").get()}")

    testRuntimeOnly("com.cobblemon:neoforge:${providers.gradleProperty("cobblemon_version").get()}") {
        isTransitive = false
    }
    testRuntimeOnly("thedarkcolour:kotlinforforge-neoforge:${providers.gradleProperty("kotlin_for_forge_version").get()}")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val generateModMetadata by tasks.registering(ProcessResources::class) {
    val replaceProperties = mapOf(
        "minecraft_version" to providers.gradleProperty("minecraft_version").get(),
        "minecraft_version_range" to providers.gradleProperty("minecraft_version_range").get(),
        "neo_version" to providers.gradleProperty("neo_version").get(),
        "loader_version_range" to providers.gradleProperty("loader_version_range").get(),
        "mod_id" to providers.gradleProperty("mod_id").get(),
        "mod_name" to providers.gradleProperty("mod_name").get(),
        "mod_license" to providers.gradleProperty("mod_license").get(),
        "mod_version" to providers.gradleProperty("mod_version").get()
    )

    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}

sourceSets.main {
    resources.srcDir(generateModMetadata)
}

neoForge.ideSyncTask(generateModMetadata)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}


tasks.test {
    useJUnitPlatform()
}
