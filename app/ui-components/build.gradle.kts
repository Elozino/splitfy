plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/**
 * The design system: theme, reusable composables, icons and fonts. It knows
 * nothing about navigation, networking or any screen, so anything in here can be
 * previewed and reused without dragging the rest of the app along.
 */
compose.resources {
    // `UiRes` rather than `Res` so consumers can tell design-system assets apart
    // from any resources a feature module generates for itself.
    publicResClass = true
    nameOfResClass = "UiRes"
    packageOfResClass = "com.elozino.splitify.ui.generated.resources"
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    androidLibrary {
        namespace = "com.elozino.splitify.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        // The KMP Android library plugin keeps the resource pipeline off by
        // default, and Compose ships drawables and fonts as Android assets. Left
        // off, the APK silently contains neither and the app falls back to the
        // system font — which is what it did before this module existed.
        androidResources.enable = true
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "uiComponents"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api` throughout: :app:shared builds its screens out of these types,
            // so they belong to this module's public surface.
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.materialIconsExtended)
            api(libs.compose.ui)
            api(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
        }
    }
}
