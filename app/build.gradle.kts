import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.fershibli.megagames"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.fershibli.megagames"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    // Only ship the five languages the app is translated into (libraries bring dozens more).
    androidResources {
        localeFilters += listOf("en", "pt", "es", "fr", "zh")
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
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
