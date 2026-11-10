plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ciKeystore = rootProject.file("ci/powerclimb-ci.jks")

// GitHub Actions defines these env vars even when the secrets are unset, so an
// empty value must fall back to the committed CI key instead of overriding it.
fun envOrNull(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

val externalKeystore = envOrNull("RELEASE_KEYSTORE_PATH")

// CI calculates the version from git tags and passes it in; local builds use
// the defaults below.
val ciVersionName = providers.gradleProperty("powerclimb.versionName").orNull
val ciVersionCode = providers.gradleProperty("powerclimb.versionCode").orNull?.toIntOrNull()

android {
    namespace = "com.powerclimb"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.powerclimb"
        minSdk = 26
        targetSdk = 34
        versionCode = ciVersionCode ?: 3
        versionName = ciVersionName ?: "0.3.0"
    }

    signingConfigs {
        create("release") {
            storeFile = externalKeystore?.let { file(it) } ?: ciKeystore
            storePassword = envOrNull("RELEASE_KEYSTORE_PASSWORD") ?: "powerclimbci"
            keyAlias = envOrNull("RELEASE_KEY_ALIAS") ?: "powerclimb"
            keyPassword = envOrNull("RELEASE_KEY_PASSWORD") ?: "powerclimbci"
        }
    }

    buildTypes {
        release {
            // The committed CI key keeps sideload updates compatible. Override
            // it with RELEASE_KEYSTORE_* secrets for a private signing key.
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // Official Karoo extension SDK: https://github.com/hammerheadnav/karoo-ext
    implementation("com.github.hammerheadnav:karoo-ext:1.1.9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
