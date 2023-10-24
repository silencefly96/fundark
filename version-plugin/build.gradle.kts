//buildscript {
//    repositories {
//        maven {
//            url = uri("https://maven.aliyun.com/repository/google/")
//        }
//        maven {
//            url = uri("https://maven.aliyun.com/repository/public/")
//        }
//        google()
//        mavenCentral()
//    }
//    dependencies {
//        classpath("com.android.tools.build:gradle:4.1.3")
//    }
//}
//allprojects {
//    repositories {
//        google()
//        mavenCentral()
//    }
//}

plugins {
    `kotlin-dsl`
}
dependencies {
//    implementation(gradleApi())
//    implementation(localGroovy())

    // implementation("com.android.tools.build:gradle:4.1.3")

    // ASM依赖
//    implementation("org.ow2.asm:asm:9.1")
//    implementation("org.ow2.asm:asm-commons:9.1")
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