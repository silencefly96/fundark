# Github Package 托管SDK实践
## 前言
上一篇文章试了下GitHub的Action，能够利用它的虚拟机帮助我们打包、发版等，发现GitHub还有个Package功能还挺有意思的，可以托管aar，之前用过jcenter，后面jcnter废弃了，mavenCentral有点麻烦就没去实践了，现在有点时间，实践下GitHub的吧。

## 获取GitHub Token
要想在GitHub上传SDK，首先我们要申请一个“个人访问令牌”，而且只支持personal access token (classic)类型的。 有了这个令牌，我们才能发布、安装和删除专用、内部和公共包。

那就看下如何获取吧:

![image.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/be332f99d79448bd80b49a982fbc6fe2~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1920&h=892&s=229705&e=png&b=343739)

这里不是在仓库里设置，而是要点页面右上角，我们的头像，找到设置，点进去，拉到最下面:

![image.png](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/fc094b2e6f47435487b95d411e7a3fb3~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1920&h=892&s=147740&e=png&b=131313)

![image.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/acf15ab164a34a248d2b235e31ecbe91~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1920&h=892&s=181110&e=png&b=121212)

![image.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/e50a7298767e4e3689c6d45630ad9882~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1920&h=892&s=160316&e=png&b=131313)

在Developer settings里面如图新增一条，note和时间注意下，可以随便填，重点是勾上package的权限，把token复制出来，别手贱关了。

## 发布SDK
上面我们拿到token后，就可以通过"maven-publish"插件上传我们的SDK了，以前好像“maven”插件就够了，现在不行了，可以看下"maven-publish"插件的使用说明:

[Android官方使用说明](https://developer.android.com/studio/build/maven-publish-plugin?hl=zh-cn)

[GitHub官方使用说明](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry)

其实很简单，就三步，引入插件、配置发布内容、执行gradle的publish task，在要发布的module的build.gradle文件中修改:
```kotlin
plugins {
    // ...
    id("maven-publish")
}

// 需要打开AndroidStudio的Task显示，关闭下面选项(旧版前面，新版后面)
// File(-> Settings -> Experimental -> “Do not build Gradle task list during Gradle sync”)
// File(-> Settings -> Experimental -> “only include test tasks in the gradle task list generated during gradle sync”)

// 上传到本地/远程仓库
// maven-publish说明: https://developer.android.com/studio/build/maven-publish-plugin?hl=zh-cn

// 可能要在这加个group，不报错可不加
group = "com.silencefly96"
publishing{
    publications {
        // register和create都差不多
        register<MavenPublication>("lib-base") {
            // 配置信息，使用: classpath("groupId:artifactId:version") (不能有空格)
            groupId = "com.silencefly96"
            artifactId = "lib-module_base"
            version = "1.0.1"
            
            // 这条要加上，不然不会包含代码文件
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
```
因为kts和groovy有所区别，还是按上面的使用说明编写代码，不过内容实质上都一样，下面简单说下。

### maven-publish插件
在id里面引入插件只要加一行代码就行，而配置发布内容就麻烦一点了，首先要配置repositories:
```kotlin
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
```
其中name随便填，url注意下格式，后面填入用户名和仓库名:
> "https://maven.pkg.github.com/" + GitHub用户名 + "/" + 要发布的仓库名

credentials就是填验证信息了，里面username就是你GitHub的用户名，password是上面一节申请到的token，即“个人访问令牌”。

我这把这两个变量放到了local.properties里面，设置kts全局变量拿的，后面我会讲。再来看下发布信息:
```kotlin
publications {
    // register和create都差不多
    register<MavenPublication>("lib-base") {
        // 配置信息，使用: classpath("groupId:artifactId:version") (不能有空格)
        groupId = "com.silencefly96"
        artifactId = "lib-module_base"
        version = "1.0.1"

        // 这条要加上，不然不会包含代码文件
        afterEvaluate {
            from(components["release"])
        }
    }
}
```
这里注册了一个发布物(也可以用create)，名称随意，重点是groupId、artifactId、version这三个，他们唯一标识了一个SDK，使用的时候要用到，例如:
> implementation("com.silencefly96:lib-module_base:1.0.1")
> classpath("groupId:artifactId:version")

还有一点要注意的是，要在afterEvaluate里面配置打包代码，不然上传的SDK不包含aar，也就没法用。

有个问题，比如GitHub官方使用说明里面是按下面这样写的，可是我这样写没法用，上面用法是我结合Android官方使用说明改的，贴一下吧:
```kotlin
plugins {
    id("maven-publish")
}
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/OWNER/REPOSITORY")
            credentials {
                username = project.findProperty("gpr.user") ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key") ?: System.getenv("TOKEN")
            }
        }
    }
    publications {
        gpr(MavenPublication) {
            from(components.java)
        }
    }
}
```

### 全局变量配置
因为GitHub的token涉及一些权限，我们不能直接把它提交公开出去，而是应该放到本地变量去，网上都是说新建个如下文件放进去:
> ~/.gradle/gradle.properties

可以我感觉没什么用，还是放在了根目录的local.properties里面:
```properties
gpr.user=username
gpr.key=password_token
```

放在local.properties里面，就涉及到从里面读取了，在项目的build.gradle文件里面加个ext就行了:
```kotlin
ext {
    // 从local.properties读取github用户名及personal access token (classic)
    val properties = java.util.Properties()
    val inputStream = project.rootProject.file("local.properties").inputStream()
    properties.load(inputStream)

    set("githubUser", properties.getProperty("gpr.user"))
    set("githubPassword", properties.getProperty("gpr.key"))
}
```

这里设置好，我们在各个module的build.gradle就可以通过下面用法拿到数据了:
```kotlin
username = rootProject.extra["githubUser"].toString()
password = rootProject.extra["githubPassword"].toString()
```
关于，这部分内容，我在另一篇文章里面详细说明了下: [《记录迁移gradle到kts - ext变量的使用》](https://juejin.cn/post/7293709429886189568#heading-16)

### 执行发布
一开始我也是傻，写好代码了一直不知道怎么发布，而且Android Studio后面默认不显示gradle里面的task了，可以按下面方法显示出来:
> 需要打开AndroidStudio的Task显示，关闭下面选项(旧版前面，新版后面)
> 
> File(-> Settings -> Experimental -> “Do not build Gradle task list during Gradle sync”)
> 
> File(-> Settings -> Experimental -> “only include test tasks in the gradle task list generated during gradle sync”)

![image.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/ad3f3861043742e9ae5a628f91e37d78~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1494&h=1033&s=124586&e=png&b=3d4043)

关掉Android Studio的隐藏配置后，就能在侧边栏执行gradle脚本发布内容了:

![image.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/0e92182af79043fca29e08005f232f74~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1459&h=885&s=220952&e=png&b=3c4042)

点击publish后就会开始执行，在build里面显示successful后，就能去GitHub查看结果:

![image.png](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/54a9d67bf2f244d1a9dfb267576bc7f3~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1920&h=892&s=200511&e=png&b=131313)

## 使用GitHub上的SDK
SDK上传成功了，使用就简单了，不过也有一点点小问题提一下。 首先就是要引入repositories:
```kotlin
dependencyResolutionManagement {
   // 从local.properties读取github用户名及personal access token (classic)
    val propsFile = File(rootProject.projectDir.path + "/local.properties")
    val properties = java.util.Properties()
    properties.load(propsFile.inputStream())
    // 设置到extra，会先于buildSrc执行，但是这里设置的extra没办法在project/module的gradle里面用。。。。
    extra.set("githubUser", properties.getProperty("gpr.user"))
    extra.set("githubPassword", properties.getProperty("gpr.key"))

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Github远程仓库
        maven {
            url = uri("https://maven.pkg.github.com/silencefly96/fundark")
            credentials {
                username = extra["githubUser"].toString()
                password = extra["githubPassword"].toString()
            }
        }
    }
}
```
repositories的配置和发布那里一样，有个小问题就是setting.gradle.kts里面拿不到project的build.gradle配置的全局变量，得重新读一遍文件(代码有改动)。
> ps. 当然也可以改repositoriesMode，到project的build.gradle去配置repositories，这样就不用再读一遍文件了，只不过感觉不够优雅

配置好repositories，就能再module的build.gradle引入我们的SDK了:
```kotlin
dependencies {
    // ...
    //从基础库继承各个依赖
    implementation("com.silencefly96:lib-module_base:1.0.1")
}
```

同步一下就可以尽情的用了:

![image.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/4cce2fd81e804654b38bb5df2c2307ec~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1459&h=656&s=103776&e=png&b=2c2c2c)

## 参考文章
[Android官方使用说明](https://developer.android.com/studio/build/maven-publish-plugin?hl=zh-cn)

[GitHub官方使用说明](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry)

[gradle发布jar到GitHub Packages](https://juejin.cn/post/7007289428158709797)

[使用 Apache Maven 注册表](https://docs.github.com/zh/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)

## 源码参考
源码在我练手的仓库里:

[发布的gradle: module_base](https://github.com/silencefly96/fundark/blob/develop/module_base/build.gradle.kts)

[引入repositories: setting.gradle.kts](https://github.com/silencefly96/fundark/blob/develop/settings.gradle.kts)

[使用的module gradle](https://github.com/silencefly96/fundark/blob/develop/app/build.gradle.kts)

## 小结
这篇文章把一个Android模块打包成aar，并发布到GitHub的Package上，并示范了如何拉取并引用，感觉很nice！