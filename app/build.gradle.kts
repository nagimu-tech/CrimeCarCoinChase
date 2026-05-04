plugins {
    id("com.android.application")
}

android {
    namespace = "com.nagimutech.crimecarcoinchase"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nagimutech.crimecarcoinchase"
        minSdk = 23
        targetSdk = 34
        versionCode = 14
        versionName = "2.1.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
