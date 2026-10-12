plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    if (System.getProperty("os.name").lowercase().contains("windows")) systemProperty("junit.jupiter.tempdir.cleanup.mode.default", "NEVER")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

// Private resource: the JDBC library is not a second JPMS/JarJar module.
val sqliteBundled by configurations.creating { isTransitive = false }
dependencies { add(sqliteBundled.name, "org.xerial:sqlite-jdbc:3.53.4.0") }
tasks.named<ProcessResources>("processResources") {
 from(sqliteBundled) { into("META-INF/zian-libraries"); rename { "sqlite-jdbc.jar" } }
}
