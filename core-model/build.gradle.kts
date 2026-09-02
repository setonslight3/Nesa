plugins {
    alias(libs.plugins.kotlin.jvm)
}

// core-model is deliberately a plain Kotlin/JVM library: the NESA domain must
// never depend on Android, so that it stays testable on any JVM and reusable by
// a future non-Android client.
kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(false)
    }
}

dependencies {
    testImplementation(libs.junit)
}
