
plugins {
    id("com.android.application")
}

android {
    namespace = "it.easycomanda.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.easycomanda.app"
        minSdk = 21
        targetSdk = 28
        versionCode = 14
        versionName = "1.5.8-beta-mobile"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
