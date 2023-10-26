@file:Suppress("UnstableApiUsage")

// 开启version catalogs(gradle-7.4.1-src起为稳定特性，不需要开启)
// enableFeaturePreview("VERSION_CATALOGS")

// 开了会报错: 已在类 org.gradle.accessors.dm.RootProjectAccessor中定义了方法 getVersionPlugin()
// 引用本地模块(新版写法，比如“test-library”): implementation(projects.testLibrary)
//enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        // 是用于从 Gradle 插件门户下载插件的默认仓库。
        gradlePluginPortal()
        // 依赖本地仓库
        maven{ url = uri("./privacy_repo") }
        maven{ url = uri("https://maven.aliyun.com/repository/google/") }
        maven{ url = uri("https://maven.aliyun.com/repository/public/") }
        google()
        mavenCentral()
    }

}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven{ url = uri("https://maven.aliyun.com/repository/google/") }
        maven{ url = uri("https://maven.aliyun.com/repository/public/") }
        google()
        mavenCentral()
    }

    // Android推荐放到根目录的gradle文件夹中，不需要再引用
//    versionCatalogs {
//        create("libs") {
//            from(file("$rootDir/libs.versions.toml"))
//        }
//    }
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
