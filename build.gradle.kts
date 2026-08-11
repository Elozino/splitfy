plugins {
    // Declared here so each plugin is loaded once for the whole build and
    // applied per-module. `apply false` keeps the root project free of them.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(ktorLibs.plugins.ktor) apply false
}

group = "com.elozino.splitfy"
version = providers.gradleProperty("splitfy.version").getOrElse("1.0.0-SNAPSHOT")
