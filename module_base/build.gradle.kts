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
    api(libs.bundles.versionBase)

    // Retrofit网络通信框架
    api(libs.retrofit)
    api(libs.converter.gson)

    // Glide
    api(libs.glide)
}