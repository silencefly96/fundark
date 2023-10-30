plugins {
    id("library-optimize-plugin")
}

@Suppress("UnstableApiUsage")
android {
    compileSdk = libs.versions.compileSdkVersion.get().toInt()
    defaultConfig {
        minSdk  = libs.versions.minSdkVersion.get().toInt()
        targetSdk  = libs.versions.targetSdkVersion.get().toInt()
    }
}

dependencies {
    //测试相关
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // 从基础库继承各个依赖
    implementation(project(":module_base"))

    // volley
    implementation(libs.volley)
}