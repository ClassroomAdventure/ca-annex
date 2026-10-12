plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.classroomadventure.sidescreen"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.classroomadventure.sidescreen"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.0"
    }

    // リリース鍵は git に入れない（.env.tpl 参照）。無ければ debug 鍵で署名する
    val releaseKeystore = rootProject.file("keystore/sidescreen-release.jks")
    val keystorePassword = System.getenv("SIDESCREEN_KEYSTORE_PASSWORD")
    signingConfigs {
        if (releaseKeystore.exists() && keystorePassword != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = keystorePassword
                keyAlias = "sidescreen"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        debug {
            // リリース鍵があれば debug もそれで署名（配布版の上から入れ直せるように）
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
}
