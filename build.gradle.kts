// Top-level(build file where you can add configuration options common to all sub-projects/modules.)
buildscript {
    repositories {
        // 依赖本地仓库
        maven{ url = uri("./privacy_repo") }
        maven{ url = uri("https://maven.aliyun.com/repository/google/") }
        maven{ url = uri("https://maven.aliyun.com/repository/public/") }
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
    dependencies {
        // 从本地仓库中加载自定义插件 group(+ artifactId + version，不要多手打空格！)
        classpath("silencefly96.privacy:privacy-plugin:1.0.0")

        classpath("com.android.tools.build:gradle:7.4.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.20")

        // 用于navigation间传递数据
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.3.5")
    }
}

task<Delete>("clean",{
    delete(rootProject.buildDir)
})
