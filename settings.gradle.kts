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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // SpeedChecker SDK — public demo credentials (no account required for dev/testing)
        maven {
            url = uri("https://maven.speedcheckerapi.com/artifactory/libs-release")
            credentials {
                username = "demo"
                password = "AP85qiz6wYEsCttWU2ZckEWSwJKuA6mSYcizEY"
            }
        }
        // JitPack — needed for oksse (transitive dep of SpeedChecker SDK)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Internet speed meeter"
include(":app")
