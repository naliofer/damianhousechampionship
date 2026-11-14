plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.campeonato.duplas"
    compileSdk = 35
    defaultConfig { applicationId = "com.campeonato.duplas"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "1.1" }
    val releaseStore = providers.environmentVariable("CAMPEONATO_KEYSTORE_PATH").orNull
    val releaseStorePassword = providers.environmentVariable("CAMPEONATO_KEYSTORE_PASSWORD").orNull
    val releaseKeyPassword = providers.environmentVariable("CAMPEONATO_KEY_PASSWORD").orNull
    signingConfigs {
        create("release") {
            if (releaseStore != null) storeFile = file(releaseStore)
            storePassword = releaseStorePassword
            keyAlias = "campeonato-damiao"
            keyPassword = releaseKeyPassword
        }
    }
    buildTypes { release {
        isMinifyEnabled = false
        signingConfig = signingConfigs.getByName("release")
    } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
dependencies {
    testImplementation("junit:junit:4.13.1")
    testImplementation("org.json:json:20231013")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
}
