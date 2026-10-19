import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Semantic version of the app. CI passes it with -PappVersion=X.Y.Z (or X.Y.Z-rc.N for test
// builds); local builds use the default. The versionCode is derived from it so every release is
// always higher than the previous one: MAJOR*1_000_000 + MINOR*10_000 + PATCH*100 + (rc number, or 99).
val appVersion: String = (findProperty("appVersion") as String?)?.removePrefix("v") ?: "1.0.0"
val appVersionCode: Int = run {
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-rc\.(\d+))?$""").matchEntire(appVersion)
        ?: error("appVersion must look like 1.2.3 or 1.2.3-rc.4, got '$appVersion'")
    val (major, minor, patch, rc) = match.destructured
    require(minor.toInt() < 100 && patch.toInt() < 100) { "minor and patch must be below 100" }
    val suffix = if (rc.isEmpty()) 99 else rc.toInt().also { require(it in 1..98) { "rc number must be 1..98" } }
    major.toInt() * 1_000_000 + minor.toInt() * 10_000 + patch.toInt() * 100 + suffix
}

android {
    namespace = "io.github.fershibli.megagames"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.fershibli.miligames"
        minSdk = 23
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion
    }

    // Only ship the languages the app is translated into (libraries bring dozens more).
    androidResources {
        localeFilters += listOf("en", "pt", "es", "fr", "it", "de", "ru", "ja", "zh", "ar")
    }

    // Release signing: from keystore.properties (local, never committed) or from environment
    // variables (CI). Without either, the release APK is simply left unsigned.
    val keystoreFile = rootProject.file("keystore.properties")
    val signing = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) } }
    fun secret(key: String, env: String): String? = signing.getProperty(key) ?: System.getenv(env)
    val storePath = secret("storeFile", "MEGAGAMES_KEYSTORE_FILE")
    signingConfigs {
        if (storePath != null) {
            create("release") {
                storeFile = rootProject.file(storePath)
                storePassword = secret("storePassword", "MEGAGAMES_KEYSTORE_PASSWORD")
                keyAlias = secret("keyAlias", "MEGAGAMES_KEY_ALIAS")
                keyPassword = secret("keyPassword", "MEGAGAMES_KEY_PASSWORD")
            }
        }
    }

    // No Google-encrypted dependency blob in the APK: anyone can inspect exactly what ships.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release {
            // Real key when configured; otherwise, only if explicitly asked (CI test builds), the
            // debug key so the APK can still be installed.
            signingConfigs.findByName("release")?.let { signingConfig = it }
                ?: run { if (findProperty("signWithDebugKey") == "true") signingConfig = signingConfigs.getByName("debug") }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "3g"
                // Screenshots are only rendered on request: ./gradlew screenshots
                it.systemProperty("screenshots", project.findProperty("screenshots") ?: "false")
                it.systemProperty("benchmarks", project.findProperty("benchmarks") ?: "false")
                it.systemProperty("screenshotDir", rootProject.layout.projectDirectory.dir("docs/screenshots").asFile.absolutePath)
                // Google Play graphics, also on request: ./gradlew :app:testDebugUnitTest -PplayAssets=true --tests '*PlayAssetsTest*'
                it.systemProperty("playAssets", project.findProperty("playAssets") ?: "false")
                it.systemProperty("playAssetsDir", rootProject.layout.projectDirectory.dir("fastlane/metadata/android").asFile.absolutePath)
            }
        }
    }

    // All languages must stay in the app: the language can be changed inside the app.
    bundle {
        language {
            enableSplit = false
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
