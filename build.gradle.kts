// Top-level(build file where you can add configuration options common to all sub-projects/modules.)
buildscript {
    repositories {
        maven{ url = uri("https://maven.aliyun.com/repository/google/") }
        maven{ url = uri("https://maven.aliyun.com/repository/public/") }
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.0")

        // 用于navigation间传递数据
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.3.5")
    }
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}
