plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ciKeystore = rootProject.file("ci/powerclimb-ci.jks")
val externalKeystore = System.getenv("RELEASE_KEYSTORE_PATH")

android {
    namespace = "com.powerclimb"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.powerclimb"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
    }

    signingConfigs {
        create("release") {
            storeFile = externalKeystore?.let { file(it) } ?: ciKeystore
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: "powerclimbci"
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "powerclimb"
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "powerclimbci"
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
