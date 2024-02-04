@file:Suppress("UnstableApiUsage")

// 开启version catalogs(gradle-7.4.1-src起为稳定特性，不需要开启)
// enableFeaturePreview("VERSION_CATALOGS")

// 开了会报错: 已在类 org.gradle.accessors.dm.RootProjectAccessor中定义了方法 getVersionPlugin()
// 引用本地模块(新版写法，比如“test-library”): implementation(projects.testLibrary)
//enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    // 从local.properties读取github用户名及personal access token (classic)
    val propsFile = File(rootProject.projectDir.path + "/local.properties")
    val properties = java.util.Properties()
    properties.load(propsFile.inputStream())
    // 设置到extra，会先于buildSrc执行，但是这里设置的extra没办法在project/module的gradle里面用。。。。
    extra.set("githubUser", properties.getProperty("gpr.user"))
    extra.set("githubPassword", properties.getProperty("gpr.key"))

    repositories {
        // Github远程仓库
        maven {
            url = uri("https://maven.pkg.github.com/silencefly96/fundark")
            credentials {
                username = extra["githubUser"].toString()
                password = extra["githubPassword"].toString()
            }
        }
        // 是用于从 Gradle 插件门户下载插件的默认仓库。
        gradlePluginPortal()
        maven{ url = uri("https://maven.aliyun.com/repository/google/") }
        maven{ url = uri("https://maven.aliyun.com/repository/public/") }
        google()
        mavenCentral()
    }

}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven{ url = uri("./catalog_repo") }
        // Github远程仓库
        maven {
            url = uri("https://maven.pkg.github.com/silencefly96/fundark")
            credentials {
                username = extra["githubUser"].toString()
                password = extra["githubPassword"].toString()
            }
        }
        maven {
            url = uri("http://maven.aliyun.com/nexus/content/repositories/releases/")
            name = "aliyun"
            //一定要添加这个配置
            isAllowInsecureProtocol = true
        }
        maven{ url = uri("https://maven.aliyun.com/repository/google/") }
        maven{ url = uri("https://maven.aliyun.com/repository/public/") }
        google()
        mavenCentral()
    }

    // Android推荐放到根目录的gradle文件夹中，不需要再引用(注意是files，出bug卡了我好久)
//    versionCatalogs {
//        create("libs") {
//            from(files("$rootDir/libs.versions.toml"))
//        }
//    }

    // 版本目录配置
//    versionCatalogs {
//        // 创建一个名称为 libs 的版本目录
//        create("libs") {
//            // 从 maven 仓库获取依赖
//            from("com.silencefly96:lib-catalog_plugin:1.0.0")
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
includeBuild("build-plugins")
