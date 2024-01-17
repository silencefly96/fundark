plugins {
    id("library-optimize-plugin")
    id("maven-publish")
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

publishing{
    publications {
        // 注册整个发布物
        register<MavenPublication>("lib-base") {
            // 配置信息，使用: classpath("groupId:artifactId:version"(不能有空格))
            groupId = "com.silencefly96"
            artifactId = "lib-module_base"
            version = "1.0.1"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
    repositories {
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