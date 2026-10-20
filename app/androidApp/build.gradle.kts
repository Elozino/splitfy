import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

// Firebase reads its project settings from google-services.json, which is
// per-developer and gitignored. Without it the plugin is skipped and the app builds
// and runs with push turned off (SplitfyApplication checks for a FirebaseApp).
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.googleServices.get().pluginId)
} else {
    logger.info("app/androidApp/google-services.json not found; building without push notifications")
}

/**
 * A deliberately thin Android host: manifest, launcher icons, and a single Activity
 * that hands off to the shared Compose UI in `:app:shared`.
 *
 * AGP 9 no longer supports applying `com.android.application` to a Kotlin
 * Multiplatform module, so the app shell and the shared UI live in separate
 * projects.
 */
extensions.configure<ApplicationExtension> {
    // App identity and the API host, read from `config.properties` — the Android
    // counterpart to the xcconfig files in `app/iosApp/Configuration`.
    //
    // `config.local.properties` is loaded second and overwrites anything it
    // repeats, which is how a developer points a build at their own machine
    // without dirtying the tree. Both are read through `providers`, so editing
    // either invalidates the configuration cache rather than being ignored.
    //
    // Declared inside this block on purpose: AGP 9 stops recognising the
    // `compileSdk` below if the script has any top-level declaration above it.
    val appConfig = Properties().apply {
        for (name in listOf("config.properties", "config.local.properties")) {
            providers.fileContents(layout.projectDirectory.file(name)).asText.orNull
                ?.let { load(it.reader()) }
        }
    }
    val config = { key: String ->
        appConfig.getProperty(key)
            ?: error("Missing `$key` in app/androidApp/config.properties")
    }

    // The package BuildConfig and R land in. Distinct from `applicationId`, which
    // identifies the installed app and is configurable below.
    namespace = "com.elozino.splitfy"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = config("app.applicationId")
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // CI overrides both from the tag and run number (-Papp.versionCode=...),
        // so a release never needs a commit just to bump them.
        versionCode = (providers.gradleProperty("app.versionCode").orNull ?: config("app.versionCode")).toInt()
        versionName = providers.gradleProperty("app.versionName").orNull ?: config("app.versionName")

        // Generated rather than kept in res/values/strings.xml, so the launcher
        // label comes from the same file as the rest of the app's identity.
        resValue("string", "app_name", config("app.name"))

        // Optional: an empty value hides the Google button rather than failing the build.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${appConfig.getProperty("google.webClientId").orEmpty()}\"")

        // Crash reporting is off unless a DSN is configured: config.local.properties
        // locally, `-Psentry.dsn=...` from CI.
        val sentryDsn = providers.gradleProperty("sentry.dsn").orNull ?: appConfig.getProperty("sentry.dsn").orEmpty()
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
    }

    // Release signing. Nothing secret is committed: the keystore and its passwords
    // come from environment variables (CI) or a gitignored keystore.properties
    // next to this file (a developer's machine). Without either, release builds
    // are produced unsigned, which is fine for checking that they compile.
    val keystoreProperties = Properties().apply {
        providers.fileContents(layout.projectDirectory.file("keystore.properties")).asText.orNull
            ?.let { load(it.reader()) }
    }
    val signingValue = { env: String, property: String ->
        providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() }
            ?: keystoreProperties.getProperty(property)?.takeIf { it.isNotBlank() }
    }
    val storeFilePath = signingValue("ANDROID_KEYSTORE_PATH", "storeFile")
    val releaseSigning = if (storeFilePath != null) {
        signingConfigs.create("release") {
            storeFile = file(storeFilePath)
            storePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
            keyAlias = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
            keyPassword = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")
        }
    } else {
        null
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"${config("api.baseUrl.debug")}\"")
        }
        release {
            releaseSigning?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "API_BASE_URL", "\"${config("api.baseUrl.release")}\"")
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

    // Push notifications. Inert until google-services.json is present.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    debugImplementation(libs.compose.uiTooling)
}
