import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// core-model is deliberately a plain Kotlin/JVM library: the NESA domain must
// never depend on Android, so that it stays testable on any JVM and reusable by
// a future non-Android client.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Coroutines only, for the Flow type used by the repository contracts. The
    // domain still has no Android and no framework dependency.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
