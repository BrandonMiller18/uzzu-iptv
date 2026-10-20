import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Screenshot goldens in src/test/snapshots: `recordRoborazziDebug` rewrites them,
    // `verifyRoborazziDebug` fails on a visual change (local only; see ui/Snapshots.kt in tests).
    alias(libs.plugins.roborazzi)
}

// Versioning: CI passes -PversionName=1.2.3 (from the git tag) and optionally -PversionCode=N.
// Locally the defaults below apply. versionCode is derived from versionName when not given.
val appVersionName: String = (findProperty("versionName") as String?)?.removePrefix("v") ?: "0.1.0"
val appVersionCode: Int = (findProperty("versionCode") as String?)?.toInt() ?: run {
    val parts = appVersionName.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
    parts[0] * 10_000 + parts[1] * 100 + parts[2]
}

// Release signing: env vars first (CI), then a gitignored signing.properties (local). If neither is
// present, the release build is simply left unsigned.
val signingProps = Properties().apply {
    val f = rootProject.file("signing.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: signingProps.getProperty(prop)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("UZZU_KEYSTORE", "storeFile")
val hasReleaseSigning = releaseStoreFile != null && file(releaseStoreFile).isFile

android {
    namespace = "com.bmiller.uzzu"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bmiller.uzzu"
        minSdk = 28
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = signingValue("UZZU_STORE_PASSWORD", "storePassword")
                keyAlias = signingValue("UZZU_KEY_ALIAS", "keyAlias") ?: "uzzu"
                keyPassword = signingValue("UZZU_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        // Release code (R8, not debuggable) signed with the debug key: for on-device performance
        // checks (`assembleBench`), installable over a debug build without losing its data.
        create("bench") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    // Debug only: the scrubbed playlist + ESPN recordings as assets for fixture mode (FixtureInterceptor).
    sourceSets.getByName("debug").assets.srcDir("src/test/resources/fixtures")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
        }
    }

    lint {
        abortOnError = true
        checkDependencies = false
        disable += listOf(
            // Versions are pinned deliberately (compile/targetSdk 36 per PLAN; newer libs need 37).
            "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi",
            // TV-only app: ARM ABIs only, square banner-style launcher icons, xhdpi banner.
            "ChromeOsAbiSupport", "IconLauncherShape", "IconMissingDensityFolder",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.tv.material)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
