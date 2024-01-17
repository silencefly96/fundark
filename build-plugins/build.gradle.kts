import org.jetbrains.kotlin.konan.properties.Properties

buildscript {
    repositories {
        gradlePluginPortal()
        maven{ url = uri("https://maven.aliyun.com/repository/google/") }
        maven{ url = uri("https://maven.aliyun.com/repository/public/") }
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.0")
    }
}

plugins {
    `kotlin-dsl`
    id("version-catalog")
    id("maven-publish")
}

// 插件的依赖关系
dependencies {
    implementation(gradleApi())
    implementation("com.android.tools.build:gradle:7.4.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.0")
}

gradlePlugin {
    plugins.register("applicationOptimizePlugin") {
        id = "application-optimize-plugin"
        implementationClass = "com.silencefly96.plugins.optimize.ApplicationOptimizePlugin"
    }
    plugins.register("libraryOptimizePlugin") {
        id = "library-optimize-plugin"
        implementationClass = "com.silencefly96.plugins.optimize.LibraryOptimizePlugin"
    }
    plugins.register("privacyPlugin") {
        id = "privacy-plugin"
        implementationClass = "com.silencefly96.plugins.privacy.PrivacyPlugin"
    }
}

ext {
    // 从local.properties读取github用户名及personal access token (classic)
    val propsFile = File(rootProject.projectDir.parentFile.path + "/local.properties")
    val properties = Properties()
    properties.load(propsFile.inputStream())
    // 设置到extra，会先于buildSrc执行，但是这里设置的extra没办法在project/module的gradle里面用。。。。
    extra.set("githubUser", properties.getProperty("gpr.user"))
    extra.set("githubPassword", properties.getProperty("gpr.key"))
}

catalog {
    versionCatalog {
        from(files("./gradle/libs.versions.toml"))
    }
}



group = "com.silencefly96"
version = "1.0.0"
publishing{
    publications {
        register<MavenPublication>("build-plugin") {
            // 配置信息，使用: classpath("groupId:artifactId:version"(不能有空格))
            groupId = "com.silencefly96"
            artifactId = "lib-build_plugins"
            version = "1.0.0"
            from(components["java"])
        }

        // 会新建一个catalog-plugin目录
        create<MavenPublication>("catalog-plugin") {
            // 配置信息，使用: classpath("groupId:artifactId:version"(不能有空格))
            groupId = "com.silencefly96"
            artifactId = "lib-catalog_plugin"
            version = "1.0.0"
            from(components["versionCatalog"])
        }
    }
    repositories {
        // 本地的 Maven(地址设置，部署到本地，也就是项目的根目录下)
//        maven { url = uri("../catalog_repo") }
        // 远程仓库
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/silencefly96/fundark")
            credentials {
                username = rootProject.extra["githubUser"].toString()
                password = rootProject.extra["githubPassword"].toString()
            }
        }
    }
}