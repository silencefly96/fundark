# Android Gradle自定义插件编写实践与总结
## 前言
十月份来一直在弄安全整改的问题，主要就是对第三方SDK的整改，搞得有点累，但是收获挺多的，最近终于闲了点，准备把资料整理整理，文章写一写。

因为这事Glide源码解析系列的文章也鸽了挺久，~~慢慢来吧~~，还是得行动起来吧！

## Gradle插件概述
这里就先用[安卓官方文档](https://developer.android.com/studio/build/extend-agp?hl=zh-cn#build-process)来给gradle插件做个介绍吧:
> Android Gradle 插件 (AGP) 是官方的 Android 应用构建系统。该系统支持编译多种不同类型的源代码，以及将其链接到可在实体 Android 设备或模拟器上运行的应用中。

根据[gradle的官方文档](https://docs.gradle.org/6.9.1/userguide/custom_plugins.html)，构建一个自定义插件有三种方式:
- Build script
- buildSrc project
- Standalone project

下面我们就来探究并实践下这三个方式。

## Build script方式
Build script方式其实就是直接在要用到插件的module的build.gradle中写，比较简单，如下面:
```
// 定义插件
class InsideModulePlugin implements Plugin<Project> {
    void apply(Project project) {
        println 'InsideModulePlugin'
    }
}

// 应用插件
apply plugin: InsideModulePlugin
```
比如我再app模块的build.gradle文件中写入上面代码，在AS上点击sync，就会有下面信息打印:
```
> Configure project :app
InsideModulePlugin
```

## buildSrc project方式
前面我我写了一篇关于Android依赖管理的文章: [Android依赖管理实践与总结](https://juejin.cn/post/7290746997061074959)，里面有详细说到如何使用buildSrc，有需要可以先看下。

这里我们就在buildSrc项目中修改了，直接在buildSrc的“src\main\java\”下创建一个插件，包名不写:
```
// BuildSrcPlugin.groovy
import org.gradle.api.Plugin
import org.gradle.api.Project

public class BuildSrcPlugin implements Plugin<Project>{

    @Override
    void apply(Project project) {
        println("BuildSrcPlugin")
    }
}
```
然后就可以在app模块的build.gradle文件中使用了:
```
apply plugin: BuildSrcPlugin
```
和上面一样，sync一下就能看到:
```
> Configure project :app
BuildSrcPlugin
```

---
有的文章说要在buildSrc目录的build.gradle说要添加groovy插件，我这去掉了rebuild好像也没问题，后面会用到，这里稍微提一下:
```
// 使用groovy
apply plugin: 'groovy'
apply plugin: 'groovy-gradle-plugin'

// 使用Java
apply plugin: 'java'
apply plugin: 'java-gradle-plugin'
```


---
buildSrc用起来挺方便的，但是不要忘了它是对所有模块生效的，修改了buildSrc模块就等于一次全量编译，要使用的话还是需要注意下。

## Standalone project方式
这个Standalone project方式搞得我有点懵，因为网上都是需要写个resources配置文件发到maven仓库使用，我想通过不生成的仓库的方式去使用插件，还研究了挺久，再看下官网关于Standalone project的说明:
> You can create a separate project for your plugin. This project produces and publishes a JAR which you can then use in multiple builds and share with others. Generally, this JAR might include some plugins, or bundle several related task classes into a single library. Or some combination of the two.

好像就是得将plugin生成jar才能使用，但是想到我[Android依赖管理实践与总结](https://juejin.cn/post/7290746997061074959)里面Composing build也是用的插件啊，好像这里有两种不同的办法，下面分别说。

### 通过resources配置文件
第一种方法就是网上很多的通过resource配置文件去创建插件，根据我自己实践啊，最简单且好理解的的形式如下(以PrivacyPlugin为例):

1. 在项目根目录创建插件文件目录
    ![pic.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/79e1974105db48d4add04fcaadb5fad2~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=587&h=428&s=26871&e=png&b=3a3d3f)
    如图，在对应目录(目录也要建)创建三个文件: PrivacyPlugin.groovy、silencefly96.privacy.properties、build.gradle

2. 修改build.gradle
    ```
    // 让AS识别groovy代码
    apply plugin: 'groovy'
    // 支持maven
    apply plugin: 'maven'
    // 添加gradle插件依赖
    apply plugin: 'groovy-gradle-plugin'

    // 添加依赖，groovy-gradle-plugin内有添加
    // dependencies {
    //     implementation gradleApi()
    //     implementation localGroovy()
    // }

    // 需要打开AndroidStudio的Task显示，关闭下面选项(旧版前面，新版后面)
    // File -> Settings -> Experimental -> “Do not build Gradle task list during Gradle sync”
    // File -> Settings -> Experimental -> “only include test tasks in the gradle task list generated during gradle sync”

    // 上传到本地/远程仓库
    // 需要创建(文件名为插件id): resources\META-INF\gradle-plugins\silencefly96.privacy.properties
    uploadArchives{
        repositories.mavenDeployer {
            //提交到远程服务器：
            // repository(url: "http://www.xxx.com/repos") {
            //    authentication(userName: "admin", password: "admin")
            // }
            // 本地的 Maven 地址设置，部署到本地，也就是项目的根目录下
            repository(url: uri('../privacy_repo'))

            // 配置信息，使用: classpath 'groupId:artifactId:version'(不能有空格)
            pom.groupId = 'silencefly96.privacy'
            pom.artifactId = 'privacy-plugin'
            pom.version = '1.0.0'
        }
    }
    ```
    这里加了很多说明，如果不需要可以去掉，看看还是能学点东西的。

3. 修改PrivacyPlugin.groovy
    ```
    package silencefly96.privacy

    import org.gradle.api.Plugin
    import org.gradle.api.Project

    public class PrivacyPlugin implements Plugin<Project>{

        @Override
        void apply(Project project) {
            println("PrivacyPlugin")
        }
    }
    ```
    包名注意和文件目录一致，看别的文章有报错，点击去看文件夹目录是“xxx.xxx”，也是有点无语了。

4. 修改silencefly96.privacy.properties
    ```
    implementation-class=silencefly96.privacy.PrivacyPlugin
    ```
    实际这里就是注册插件吧，但是我没发现哪里设置插件的ID，最后感觉插件的id就是这个properties文件的文件名。

5. 发布插件
    在上面build.gradle里面有写uploadArchives，他能发布插件，在AS侧边栏点开gradle，找到我们的module执行uploadArchives就能发布出去:
    ![企业微信截图_16980315469516.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/ac94f1f3ff374044abcddecc7bbcdd8c~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=666&h=381&s=17522&e=png&b=3c3f41)

    如果没有uploadArchives可以看下下面说明:
    ```
    // 需要打开AndroidStudio的Task显示，关闭下面选项(旧版前面，新版后面)
    // File -> Settings -> Experimental -> “Do not build Gradle task list during Gradle sync”
    // File -> Settings -> Experimental -> “only include test tasks in the gradle task list generated during gradle sync”
    ```

6. 在项目build.gradle中引入
    上面我们已经将插件发布到了本地的maven仓库，就在根目录下，下面要在项目的build.gradle引入:
    ```
    buildscript {
        repositories {
            // 依赖本地仓库
            maven{ url './privacy_repo' }
            ...
        }
        dependencies {
            // 从本地仓库中加载自定义插件 group + artifactId + version，不要多手打空格！
            classpath 'silencefly96.privacy:privacy-plugin:1.0.0'
            ...
        }
    }
    ```

7. 在module中使用
    引入本地maven仓库后就可以使用插件了:
    ```
    plugins {
        ...
        id 'silencefly96.privacy'
    }
    ```
    上面有说到，这个id好像就是properties文件的文件名，sync一下就能看高我们的插件已经引入成功了:
    ```
    > Configure project :app
    PrivacyPlugin
    ```

其实用起来还是挺麻烦的，group、artifactId、version、id稍微弄错点，找错误找一天-_-||。

### Composing build中添加
这里还是在Composing build里面加一个插件吧，这里就不详细讲Composing build了，下面看代码:
```
// ComposingBuildPlugin.kt
package com.silencefly96.version

import org.gradle.api.Plugin
import org.gradle.api.Project

class ComposingBuildPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        println("VersionBasePlugin")
    }
}
```
在Composing build模块的build.gradle.kts文件中注册:
```
gradlePlugin {
    ...
    plugins.register("composingBuildPlugin") {
        id = "composing-build-plugin"
        implementationClass = "com.silencefly96.version.ComposingBuildPlugin"
    }
}
```
然后在使用的module中添加使用，这里顺便把其他配置也发下:
```
plugins {
    id 'com.android.application'
    id 'kotlin-android'
    id 'kotlin-kapt'
    id 'kotlin-parcelize'
    id 'version-test-plugin'
    // id 'version-third-plugin'
    id 'silencefly96.privacy'
    id 'composing-build-plugin'
}

class InsideModulePlugin implements Plugin<Project> {
    void apply(Project project) {
        println 'InsideModulePlugin'
    }
}

apply plugin: InsideModulePlugin
apply plugin: BuildSrcPlugin
```
执行一下sync，看下输出，这里就截个图，更有说明力一些:
![pic.png](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/4930b574692848059c64ba4d8d160118~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1220&h=560&s=58775&e=png&b=2b2b2b)

### 失败案例
本来也学着Composing build那样，写了个module，在build.gradle里面注册插件:
```
apply plugin: 'groovy'
apply plugin: 'maven'
apply plugin: 'groovy-gradle-plugin'

// 注册gradle插件
gradlePlugin {
    plugins.register("privacyPlugin") {
        id = 'privacy-plugin'
        implementationClass = 'silencefly96.privacy.PrivacyPlugin'
    }
}
```
然后在setting.gradle中添加:
```
includeBuild ':privacy-plugin'
```
在到项目build.gradle的buildscript中注册
```
buildscript {
    repositories { ... }
    dependencies {
        // 直接从module里面加载自定义插件
        // classpath project(':privacy-plugin')
    }
}
```
但是这样好像不太行，会找不到插件，一开始问GPT也给的这种方式，感觉是不太行，如果有发现问题可以在评论区指出。

## 总结
这里算是写了四种编写gradle插件的方法吧，还是觉得Composing build那种形式好一点，不用发布到maven仓库。gradle插件只是一个工具，后面我会继续写如何用它配合ASM改第三方SDK，敬请期待！