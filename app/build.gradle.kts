import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.twinndroid"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.twinndroid"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.1.5"
    }

    // Release key: keystore.properties next to the project, in ~/.twinndroid-signing/, or TWINN_KEYSTORE_* env vars.
    // Never committed. Without it, release builds fall back to the debug key.
    val keyProps = Properties().apply {
        listOf(rootProject.file("keystore.properties"), File(System.getProperty("user.home"), ".twinndroid-signing/keystore.properties"))
            .firstOrNull { it.exists() }?.inputStream()?.use { load(it) }
        System.getenv("TWINN_KEYSTORE_FILE")?.let {
            this["storeFile"] = it
            this["storePassword"] = System.getenv("TWINN_KEYSTORE_PASSWORD")
            this["keyAlias"] = System.getenv("TWINN_KEY_ALIAS") ?: "twinndroid"
            this["keyPassword"] = System.getenv("TWINN_KEY_PASSWORD") ?: System.getenv("TWINN_KEYSTORE_PASSWORD")
        }
    }
    signingConfigs {
        if (keyProps["storeFile"] != null) {
            create("release") {
                storeFile = file(keyProps["storeFile"] as String)
                storePassword = keyProps["storePassword"] as String
                keyAlias = keyProps["keyAlias"] as String
                keyPassword = keyProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        disable += setOf("QueryAllPackagesPermission", "RequestInstallPackagesPolicy")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    // Per-app language picker (works back to Android 8 via AppCompat)
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // APK engine: binary manifest/resources editing (ARSCLib, stubs stripped) + Google's apksig signer
    implementation(files("libs/arsclib-1.4.0-android.jar"))
    implementation("com.android.tools.build:apksig:8.13.2")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.82")

    // Optional silent installs
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    // Forward the engine test inputs (see CloneEngineTest)
    listOf("TWINN_TEST_APKS", "TWINN_TEST_PKG").forEach { k -> System.getenv(k)?.let { environment(k, it) } }
    testLogging { showStandardStreams = true; events("passed", "failed", "skipped") }
    outputs.upToDateWhen { false }
}
