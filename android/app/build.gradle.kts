plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.classroomadventure.sidescreen"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.classroomadventure.sidescreen"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
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
}
