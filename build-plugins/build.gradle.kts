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

gradlePlugin {
    plugins.register("versionBasePlugin") {
        id = "version-base-plugin"
        implementationClass = "com.silencefly96.version.VersionBasePlugin"
    }
    plugins.register("versionTestPlugin") {
        id = "version-test-plugin"
        implementationClass = "com.silencefly96.version.VersionTestPlugin"
    }
    plugins.register("versionThirdPlugin") {
        id = "version-third-plugin"
        implementationClass = "com.silencefly96.version.VersionThirdPlugin"
    }
}

catalog {
    versionCatalog {
        from(files("./gradle/libs.versions.toml"))
    }
}

group = "silencefly96.catalog"
version = "1.0.0"
publishing{
    publications {
        // 会新建一个catalog-plugin目录
        create<MavenPublication>("catalog-plugin") {
            // 配置信息，使用: classpath("groupId:artifactId:version"(不能有空格))
            groupId = "silencefly96.catalog"
            artifactId = "catalog-plugin"
            version = "1.0.0"
            from(components["versionCatalog"])
        }
    }
    repositories {
        // 本地的 Maven(地址设置，部署到本地，也就是项目的根目录下)
        maven { url = uri("../catalog_repo") }
    }
}