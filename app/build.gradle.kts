plugins {
    id("com.android.application")
}

android {
    namespace = "com.nagimutech.crimecarcoinchase"
    compileSdk = 34

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.nagimutech.crimecarcoinchase"
        minSdk = 23
        targetSdk = 34
        versionCode = 29
        versionName = "2.4.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
