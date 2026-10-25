plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    android {
        namespace = "com.elozino.splitfy"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
            // Exported so Swift code can read domain types (balances, money) directly.
            export(projects.core)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Google's adaptive layout: window size classes, posture (hinges), the navigation
            // suite decision and the list-detail pane scaffold.
            implementation(libs.compose.material3.adaptive)
            implementation(libs.compose.material3.adaptive.layout)
            implementation(libs.compose.material3.adaptive.navigation)
            implementation(libs.compose.material3.adaptive.navigation.suite)
            api(projects.core)
            api(projects.app.uiComponents)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.compose.ui)
            // Back press is swallowed while App lock covers the screen.
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.compose)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)

            // Networking
            implementation(ktorLibs.client.core)
            implementation(ktorLibs.client.contentNegotiation)
            implementation(ktorLibs.client.logging)
            implementation(ktorLibs.client.auth)
            implementation(ktorLibs.serialization.kotlinx.json)

            // Dependency injection
            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Key/value storage for the session
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)

            // Images
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.coil.svg)

            // Camera / image picking
            implementation(libs.peekaboo.ui)
            implementation(libs.peekaboo.image.picker)

            // QR code for authenticator-app enrolment
            implementation(libs.qrose)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(ktorLibs.client.okhttp)
            implementation(libs.koin.android)
            // Google sign-in through the system Credential Manager sheet.
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services)
            implementation(libs.googleid)
            // Crash reporting. Android only for now: on iOS the Kotlin side would
            // need Sentry's Cocoa framework at link time, so the Swift host starts
            // Sentry Cocoa itself (see iOSApp.swift).
            implementation(libs.sentry.kmp)
            // App lock: BiometricPrompt, which needs a FragmentActivity host.
            implementation(libs.androidx.biometric)
            implementation(libs.androidx.fragment)
        }

        iosMain.dependencies {
            implementation(ktorLibs.client.darwin)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(ktorLibs.client.mock)
            implementation(libs.multiplatform.settings.test)
            implementation(libs.turbine)
        }
    }
}
