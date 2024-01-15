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

allprojects {
    // 从local.properties读取github用户名及personal access token (classic)
    val properties = java.util.Properties()
    val inputStream = project.rootProject.file("local.properties").inputStream()
    properties.load(inputStream)

    // 使用extra设置全局变量
    // 方式一:
    val githubUser by extra(properties.getProperty("gpr.user"))
    val githubPassword by extra(properties.getProperty("gpr.key"))
    // 在module中使用: val githubUser: String by rootProject.extra
    // 方式二:
    extra["githubUser"] = properties.getProperty("gpr.user")
    extra["githubPassword"] = properties.getProperty("gpr.key")
    // 方式三:
//    extra.set("githubUser", properties.getProperty("gpr.user"))
//    extra.set("githubPassword", properties.getProperty("gpr.key"))


    // 使用ext设置全局变量，无法在buildscript里面编写
    // 使用: rootProject.ext["key1"]，extra.get("githubUser")取的也是这个值
//    ext {
//        set("githubUser", properties.getProperty("gpr.user"))
//        set("githubPassword", properties.getProperty("gpr.key"))
//    }

    // 也可以通过设置 repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS) 添加仓库
//    repositories {
//        // 远程仓库
//        maven {
//            url = uri("https://maven.pkg.github.com/silencefly96/fundark")
//            credentials {
//                username = rootProject.ext["githubUser"].toString()
//                password = rootProject.ext["githubPassword"].toString()
//            }
//        }
//    }
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}
