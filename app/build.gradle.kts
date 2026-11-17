plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The signing key lives outside the repo and its password in the macOS Keychain.
// Both build types use it, so debug and release install over each other and keep data.
val signingKeystore = File(System.getProperty("user.home"), ".config/liftlog/liftlog.jks")

fun keychainSecret(account: String): String =
    providers.exec {
        commandLine("security", "find-generic-password", "-s", "liftlog-signing", "-a", account, "-w")
    }.standardOutput.asText.get().trim()

android {
    namespace = "app.liftlog"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.liftlog"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("personal") {
            if (signingKeystore.exists()) {
                val password = keychainSecret("store_password")
                storeFile = signingKeystore
                storePassword = password
                keyAlias = "liftlog"
                keyPassword = password
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("personal")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("personal")
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
