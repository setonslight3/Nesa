plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Like core-model, this stays a plain Kotlin/JVM library. The scheduling engine
// is the piece that most needs heavy unit testing, so it must not require an
// emulator or the Android SDK to run.
kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core-model"))
    testImplementation(libs.junit)
}
