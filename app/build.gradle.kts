import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.pencatatan"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.pencatatan"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // Release signing. The keystore holds the app's private signing key, so
    // neither it nor its credentials belong in version control. Credentials are
    // read from keystore.properties (git-ignored) or from the environment for CI.
    // When the keystore is absent the release build still succeeds, but produces
    // an unsigned APK rather than failing outright.
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { load(it) }
        }
    }

    fun signingValue(envVar: String, property: String): String? =
        System.getenv(envVar) ?: keystoreProperties.getProperty(property)

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("catatku-release.jks")
            val storePasswordValue = signingValue("CATATKU_STORE_PASSWORD", "storePassword")
            val keyPasswordValue = signingValue("CATATKU_KEY_PASSWORD", "keyPassword")
            val keyAliasValue = signingValue("CATATKU_KEY_ALIAS", "keyAlias")
            if (keystoreFile.exists() && storePasswordValue != null && keyPasswordValue != null && keyAliasValue != null) {
                storeFile = keystoreFile
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation("androidx.fragment:fragment:1.6.2")
    implementation("androidx.recyclerview:recyclerview:1.3.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime:2.6.2")

    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    implementation("androidx.credentials:credentials:1.2.0-rc01")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.0-rc01")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")
    // Classic Google Sign-In. Credential Manager reports "no credential" on some
    // devices/emulators even when accounts exist; this is the fallback path.
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
