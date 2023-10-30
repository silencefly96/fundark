plugins {
    id("application-optimize-plugin")
}

android {
    compileSdk = libs.versions.compileSdkVersion.get().toInt()
    defaultConfig {
        applicationId = ("com.silencefly96.module_media")
        minSdk  = libs.versions.minSdkVersion.get().toInt()
        targetSdk  = libs.versions.targetSdkVersion.get().toInt()
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get()
    }
}

dependencies {
    //测试相关
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // 从基础库继承各个依赖
    implementation(project(":module_base"))
}