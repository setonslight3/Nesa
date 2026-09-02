pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NESA"

// --- Deterministic core (pure Kotlin/JVM, no Android dependencies) -----------
include(":core-model")
include(":core-scheduling")

// --- Android core layers ----------------------------------------------------
include(":core-storage")
include(":core-settings")
include(":core-notifications")
include(":core-alarm")
include(":core-ui")

// --- Feature layers ---------------------------------------------------------
include(":feature-onboarding")
include(":feature-timeline")
include(":feature-alarm")
include(":feature-settings")

// --- Application shell ------------------------------------------------------
include(":app")
