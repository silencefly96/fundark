plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("version-test-plugin")
    id("version-third-plugin")
}

android {

    compileSdk  = BuildVersion.compileSdkVersion

    defaultConfig {
        minSdk  = (BuildVersion.minSdkVersion)
        targetSdk  = (BuildVersion.targetSdkVersion)
//        versionCode = (BuildVersion.versionCode)
//        versionName = (BuildVersion.versionName)

        testInstrumentationRunner = ("androidx.test.runner.AndroidJUnitRunner")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = (JavaVersion.VERSION_1_8)
        targetCompatibility = (JavaVersion.VERSION_1_8)
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // 从基础库继承各个依赖
    implementation(project(":module_base"))
}