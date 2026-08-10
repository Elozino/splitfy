pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
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
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
    versionCatalogs {
        // Ktor publishes its own catalog; it keeps client (mobile) and server
        // artifacts on a single, always-consistent version.
        create("ktorLibs").from("io.ktor:ktor-version-catalog:3.5.0")
    }
}

rootProject.name = "splitfy"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Contracts + domain logic shared by the apps and the backend.
include(":shared")

// Compose Multiplatform client UI + data layer (Android + iOS).
include(":composeApp")

// Thin Android application host for :composeApp.
include(":androidApp")

// Ktor backend (JVM).
include(":server")
