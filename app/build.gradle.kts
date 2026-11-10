plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ciKeystore = rootProject.file("ci/powerclimb-ci.jks")

// GitHub Actions defines these env vars even when the secrets are unset, so an
// empty value must fall back to the committed CI key instead of overriding it.
fun envOrNull(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

val externalKeystore = envOrNull("RELEASE_KEYSTORE_PATH")

// Versión: CI la calcula desde los tags de git y la inyecta con -P. En CI es
// obligatoria a propósito: preferimos que el build falle antes que publicar
// una versión por defecto que ya existe como tag.
val injectedVersionName = providers.gradleProperty("powerclimb.versionName").orNull
val injectedVersionCode = providers.gradleProperty("powerclimb.versionCode").orNull?.toIntOrNull()
val isCiBuild = System.getenv("CI") == "true"

if (isCiBuild && (injectedVersionName == null || injectedVersionCode == null)) {
    throw GradleException(
        "CI requiere -Ppowerclimb.versionName y -Ppowerclimb.versionCode; " +
            "los calcula .github/workflows/release.yml desde los tags de git.",
    )
}

fun versionCodeOf(versionName: String): Int {
    val parts = versionName.split(".")
    require(parts.size == 3) { "powerclimb.baseVersion debe ser X.Y.Z, pero era '$versionName'" }
    return parts[0].toInt() * 10_000 + parts[1].toInt() * 100 + parts[2].toInt()
}

val baseVersionName = providers.gradleProperty("powerclimb.baseVersion").get()
val appVersionName = injectedVersionName ?: "$baseVersionName-local"
val appVersionCode = injectedVersionCode ?: versionCodeOf(baseVersionName)

android {
    namespace = "com.powerclimb"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.powerclimb"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
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
