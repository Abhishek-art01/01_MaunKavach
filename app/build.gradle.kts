plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.util.Properties
import java.net.URI

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun secureProperty(name: String): String? =
    providers.gradleProperty(name).orNull
        ?: localProps.getProperty(name)
        ?: System.getenv(name)

val releaseBuildRequested = gradle.startParameter.taskNames.any {
    it.contains("Release", ignoreCase = true)
}

val releaseServerBaseUrl = secureProperty("MAUNKAVACH_SERVER_BASE_URL")
    ?: if (releaseBuildRequested) {
        throw GradleException("MAUNKAVACH_SERVER_BASE_URL must be set for release builds.")
    } else {
        "https://invalid.local"
    }

if (releaseBuildRequested && (
    releaseServerBaseUrl.contains("your-maunkavach-server") ||
        releaseServerBaseUrl.contains("example") ||
        releaseServerBaseUrl.contains("invalid.local") ||
        releaseServerBaseUrl.startsWith("http://")
    )) {
    throw GradleException("MAUNKAVACH_SERVER_BASE_URL must be a real HTTPS URL, not a placeholder or cleartext URL.")
}

if (releaseBuildRequested) {
    val uri = runCatching { URI(releaseServerBaseUrl) }.getOrNull()
    if (uri?.scheme != "https" || uri.host.isNullOrBlank()) {
        throw GradleException("MAUNKAVACH_SERVER_BASE_URL must be a valid HTTPS URL with a real host.")
    }
}

val releaseStoreFile = secureProperty("MAUNKAVACH_RELEASE_STORE_FILE")
val releaseStorePassword = secureProperty("MAUNKAVACH_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = secureProperty("MAUNKAVACH_RELEASE_KEY_ALIAS")
val releaseKeyPassword = secureProperty("MAUNKAVACH_RELEASE_KEY_PASSWORD")

if (releaseBuildRequested) {
    val missing = listOf(
        "MAUNKAVACH_RELEASE_STORE_FILE" to releaseStoreFile,
        "MAUNKAVACH_RELEASE_STORE_PASSWORD" to releaseStorePassword,
        "MAUNKAVACH_RELEASE_KEY_ALIAS" to releaseKeyAlias,
        "MAUNKAVACH_RELEASE_KEY_PASSWORD" to releaseKeyPassword
    ).filter { it.second.isNullOrBlank() }.map { it.first }

    if (missing.isNotEmpty()) {
        throw GradleException("Release signing config is missing: ${missing.joinToString()}")
    }

    val keyStore = file(releaseStoreFile!!)
    if (!keyStore.exists()) {
        throw GradleException("MAUNKAVACH_RELEASE_STORE_FILE does not exist: ${keyStore.absolutePath}")
    }
}

fun quotedBuildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.maunkavach"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.maunkavach"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.0.2"

        buildConfigField("String", "SERVER_BASE_URL", quotedBuildConfigString(releaseServerBaseUrl.trimEnd('/')))
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile?.let { file(it) }
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // No SDKCipher / no third-party encryption libs / no Retrofit / no QR libs.
    // allowBackup is disabled per spec (Vault Key data must never be cloud-backed-up).
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // --- Official AndroidX / Jetpack only. Not third-party SDKs. ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06") // EncryptedSharedPreferences wrapper over Keystore
    compileOnly("com.google.errorprone:error_prone_annotations:2.28.0")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
