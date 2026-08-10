plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

/**
 * A deliberately thin Android host: manifest, launcher icons, and a single Activity
 * that hands off to the shared Compose UI in `:app:shared`.
 *
 * AGP 9 no longer supports applying `com.android.application` to a Kotlin
 * Multiplatform module, so the app shell and the shared UI live in separate
 * projects.
 */
android {
    namespace = "com.elozino.splitify"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.elozino.splitify"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = providers.gradleProperty("splitfy.version").getOrElse("1.0.0")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    // Matches `compileOptions` above and the JVM_11 target used by :app:shared and
    // :core, so Java and Kotlin agree on the bytecode level.
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(projects.app.shared)

    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.koin.android)
    implementation(platform(libs.koin.bom))

    debugImplementation(libs.compose.uiTooling)
}
