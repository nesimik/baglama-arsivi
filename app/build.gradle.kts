plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Her GitHub derlemesinde sürüm kodu otomatik artar -> telefonda her zaman "güncelle" olarak kurulur.
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "0").toInt()

android {
    namespace = "com.nesimi.baglamaarsivi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nesimi.baglamaarsivi"
        minSdk = 24
        targetSdk = 35
        versionCode = 200 + runNumber
        versionName = "2.1.$runNumber"
    }

    // SABİT İMZA ANAHTARI: Bu dosya değişmediği sürece her yeni APK eskisinin üstüne kurulur, veriler korunur.
    signingConfigs {
        create("sabit") {
            storeFile = file("imza/baglama-arsivi.jks")
            storePassword = "BaglamaArsivi2026"
            keyAlias = "baglama"
            keyPassword = "BaglamaArsivi2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sabit")
        }
        debug {
            signingConfig = signingConfigs.getByName("sabit")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.coil.compose)
}
