@file:Suppress("UnstableApiUsage")
//enableFeaturePreview("VERSION_CATALOGS")
//enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        // 是用于从 Gradle 插件门户下载插件的默认仓库。
        gradlePluginPortal()
        // 依赖本地仓库
        maven{ url = uri("./privacy_repo") }
        maven{ url = uri("https://maven.aliyun.com/repository/google/") }
        maven{ url = uri("https://maven.aliyun.com/repository/public/") }
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }

}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven{ url = uri("https://maven.aliyun.com/repository/google/") }
        maven{ url = uri("https://maven.aliyun.com/repository/public/") }
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

rootProject.name = "fundark"
include(":app")
include(":module_base")
include(":module_mvvm")
include(":module_hardware")
include(":module_views")
include(":module_media")
include(":module_third")
include(":module_tech")
include(":privacy-plugin")
includeBuild("version-plugin")
