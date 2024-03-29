plugins {
    id("groovy")
    id("maven-publish")
}

dependencies {
    // 需要用的的API，少了编译报错
    implementation(gradleApi())
    implementation(localGroovy())

    // Transform用到的依赖
    implementation("com.android.tools.build:gradle:4.0.0")

    // ASM依赖
    implementation("org.ow2.asm:asm:9.1")
    implementation("org.ow2.asm:asm-commons:9.1")
}

// 需要打开AndroidStudio的Task显示，关闭下面选项(旧版前面，新版后面)
// File(-> Settings -> Experimental -> “Do not build Gradle task list during Gradle sync”)
// File(-> Settings -> Experimental -> “only include test tasks in the gradle task list generated during gradle sync”)

// 上传到本地/远程仓库
// 需要创建(文件名为插件id): resources\META-INF\gradle-plugins\silencefly96.privacy.properties
// maven-publish说明: https://developer.android.com/studio/build/maven-publish-plugin?hl=zh-cn
publishing{
    publications {
        create<MavenPublication>("privacy-plugin") {
            // 配置信息，使用: classpath("groupId:artifactId:version"(不能有空格))
            groupId = "silencefly96.privacy"
            artifactId = "privacy-plugin"
            version = "1.0.0"
        }
    }
    repositories {
        // 本地的 Maven(地址设置，部署到本地，也就是项目的根目录下)
        maven { url = uri("../privacy_repo") }
    }
}