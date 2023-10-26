# 使用Version Catalog管理Android依赖
## 前言
前面写了篇[《Android依赖管理实践与总结》](https://juejin.cn/post/7290746997061074959)，里面关于Version Catalog的部分鸽了，最近拿练手项目升级了gradle7.X，并使用了kts，正好把Version Catalog也实践了下，这里记录下。

## 版本依赖
Version Catalog需要gradle7.x版本，如果还没升级的话可以先升级下-_-||。前面gradle版本中都是特性，到了gradle7.4.1版本好像就稳定版本了。

如果gradle版本低于7.4.1需要在setting.gradle.kts中添加下面代码开启Version Catalog:
```
enableFeaturePreview("VERSION_CATALOGS")
```
如果版本比这更高就不用管它了。这里顺便说个问题，我看好多例子的setting.gradle.kts代码里面还有下面这句:
```
// 开了会报错: 已在类 org.gradle.accessors.dm.RootProjectAccessor中定义了方法 getVersionPlugin()
// 引用本地模块(新版写法，比如“test-library”): implementation(projects.testLibrary)
//enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
```
我这发现，加上这句会报异常，类似的enableFeaturePreview，提一下。

说完版本依赖，说下我下面例子的版本环境:

- Gradle 7.5.1
- AGP 7.4.2

---
下面就开始介绍使用吧。

## 配置Version Catalog
上面依赖没问题了，我们就可以在setting.gradle.kts的dependencyResolutionManagement中去配置versionCatalogs了，例子如下:
```
dependencyResolutionManagement {
    versionCatalogs {
        // 可以创建多个版本目录
        create("libs") {
            // 设置版本: alias + version
            version("groovy", "3.0.5")
            version("checkstyle", "8.37")
            
            // 设置库: alias + group + articact + version
            library("groovy-core", "org.codehaus.groovy", "groovy").versionRef("groovy")
            library("groovy-json", "org.codehaus.groovy", "groovy-json").versionRef("groovy")
            library("groovy-nio", "org.codehaus.groovy", "groovy-nio").versionRef("groovy")
            
            // 版本号可以设置成范围的
            library("commons-lang3", "org.apache.commons", "commons-lang3").version {
                strictly("[3.8, 4.0[")
                prefer("3.9")
            }
            
            // 声明一个依赖组
            bundle("groovy", listOf("groovy-core", "groovy-json", "groovy-nio"))
            
            // gradle插件的版本
            plugin("versions", "com.github.ben-manes.versions").version("0.45.0")
        }
    }
}
```
配置好了就可以在module中去使用了，不过不是kotlin代码而是build.gradle，下面是上面例子在app的build.gradle的使用:
```
dependencies {
    // 引用库
    implementation(libs.groovy.core)
    implementation(libs.groovy.json)
    implementation(libs.groovy.nio)
    
    // 引用一组库
    implementation(libs.bundles.groovy)
}

// 插件中的使用
plugins {
    `java-library`
    checkstyle
    alias(libs.plugins.versions)
}
```

效果如图:
![image.png](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/b3ad009d59ee47209bfb949283f2b77f~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1010&h=486&s=93108&e=png&b=434648)

上面这些例子都是从gradle的官方文档来的，kts和groovy语法可能还不一样，由于版本原因用法也可能不一样，不明白的话可以看下官方文档:
- [Gradle官方文档](https://docs.gradle.org/current/userguide/platforms.html)
- [Android官方文档](https://developer.android.com/build/migrate-to-catalogs?hl=zh-cn)

---
不过，如果只是看下如何配置的话，就没必要写一篇文章了，下面我们来继续看看Version Catalog如何通过文件和插件进行配置。

## 使用文件配置
Android官方文档里面推荐我们在项目根目录下面的gradle目录里面创建一个libs.versions.toml来进行配置Version Catalog，案例如下:
```
[versions]
ktx = "1.9.0"

[libraries]
androidx-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "ktx" }
```
配置好了，我们就可以直接用了，不需要导入，gradle会自动操作:
```
dependencies {
   implementation(libs.androidx.ktx)
}
```
官方文档要求目录和文件名都不修改，用起来还是挺简单的，但是我们也是可以自定义的。

下面在根目录下新建一个libs.toml文件(文件名任意)，配置同样的东西，我们手动导入下，使用方式还是一样:
```
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            // 从文件中导入，files别写错了
            from(files("$rootDir/libs.toml"))
        }
    }
}
```

## 关于TOML文件
TOML文件里面的写法实际和我们在前面手动配置的类似，它主要由4个部分组成
- \[versions\] 用于声明可以被依赖项引用的版本
- \[libraries\] 用于声明依赖的别名
- \[bundles\] 用于声明依赖包（依赖组）
- \[plugins\] 用于声明插件

里面的节点不能随便定义，只能是versions、libraries、bundles、plugins、metadata中的一个，下面给个全一点的例子:
```
[versions]
# 编译版本
compileSdkVersion = "31"
minSdkVersion = "19"
targetSdkVersion = "30"
versionCode = "1"
versionName = "1.0"

[libraries]
# 基本库
kotlin = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin"}
core_ktx = { module = "androidx.core:core-ktx", version.ref = "core_ktx"}
appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat"}
material = { module = "com.google.android.material:material", version.ref = "material"}
constraintlayout = { module = "androidx.constraintlayout:constraintlayout", version.ref = "constraintlayout"}

[bundles]
versionBase = [
    # kotlin
    "kotlin", "core_ktx", "appcompat", "material", "constraintlayout",

    # 协程
    "kotlinx_coroutines_core", "kotlinx_coroutines_android",

    # Jetpack lifecycle
    "lifecycle_extensions", "lifecycle_viewmodel_ktx", "lifecycle_livedata_ktx"
]

[plugins]
```
在文件里面可以通过“#”进行注释，整个文件通过节点分成几部分，可以有空行，但是“=”号不可以换行，数组里面带逗号倒是可以换行。

我也是通过我Dependencies.kt改过来的，可以配合ctrl + r来处理这些繁琐的语法替换。

## Version Catalog插件使用
我看很多文章也有提到插件的使用，但是给的例子却有点让人摸不着头脑，我这前面文章正好写了插件的使用，稍微改了改，把Version Catalog插件发布到本地，给其他项目使用，希望对读者有所帮助。

首先我们要明白下整个过程的逻辑，搞这个Version Catalog插件是干什么，我理解啊，就是我们把通用的Version Catalog配置打包成一个gradle插件，然后其他项目再引入这个插件，那样这些个项目的版本就能保持一致，不容易冲突。

也就是说，我们得先有个用来发布gradle插件的项目，在这打包号gradle插件，然后还要有用来引入这个插件的项目，理解好了这些，我们的目的就很明确了。

### 打包插件
首先我们就得搞个项目来打包插件，可以新建个项目(也可以是module，项目更好理解)，然后在它的app模块的build.gradle配置两个插件:
```
plugins {
    id("version-catalog")
    id("maven-publish")
}
```
其中maven-publish是用来发布到maven仓库的，version-catalog是用来把Version Catalog配置放到插件中的。

配置好上面两个插件，下面就来配置需要打包的Version Catalog配置:
```
catalog {
    versionCatalog {
        // 从文件中导入
        from(files("./gradle/libs.versions.toml"))
        
        // 直接配置
        version("groovy", "3.0.5")
        version("checkstyle", "8.37")
        library("groovy-core", "org.codehaus.groovy", "groovy").versionRef("groovy")
        library("groovy-json", "org.codehaus.groovy", "groovy-json").versionRef("groovy")
    }
}
```
和引入Version Catalog类似，不过不要理解成了引入，配置方法可以直接配置，也可以从文件中导入。

将要打包进插件的Version Catalog配置弄好后就是发布插件了，同样在app模块的build.gradle中添加:
```
// 这两个是我项目的配置，不写会出错，可以适当参考下，我是觉得不用
group = "silencefly96.catalog"
version = "1.0.0"

// 发布的task
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
```
其实这里就是maven-publish插件(老版本还是叫maven)的使用了，kts和groovy语法差异有点大，gradle7.x的用法和之前也不一样，可以查查资料看下你的版本maven-publish插件如何用。

关于插件可以看下我这篇文章: [Gradle自定义插件实践与总结](https://juejin.cn/post/7292952125193256995)

写好后就可以执行gradle task了，在侧边栏gradle中找到publishing执行，就能发现在项目的根目录生成了一个catalog_repo目录，里面放的就是我们的Version Catalog插件了。
![d.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/49bb888957f9422a86c9b8f08ea6defc~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=464&h=467&s=17528&e=png&b=3c3f41)

### 使用插件
生成好插件了，我们就能在其他项目引入它了，引入的方式和上面差不多，在需要引入项目的setting.gradle.kts中配置:
```
dependencyResolutionManagement {
    repositories {
        // 引入本地仓库依赖
        maven{ url = uri("./catalog_repo") }
        
        // 其他依赖
    }
    
    versionCatalogs {
        create("libs") {
            // 从 maven 仓库获取依赖
            from("silencefly96.catalog:catalog-plugin:1.0.0")
            
            // 其他配置
        }
    }
}
```
这里需要两步，第一步就是在repositories引入本地maven仓库，第二个就是从刚刚我们打包好的插件中导入Version Catalog配置。

导入后，sync一下，在需要使用的module中我们就可以使用依赖了:
![image.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/5ba01a8c08e2468ebf136ee105a13905~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=995&h=570&s=127937&e=png&b=444648)

### 一些思考
搞到这我发现Version Catalog有问题啊，这不就是buildSrc一样么，每个要使用的模块都要手动去implementation、api、kapt，没有使用Composing build插件那样一键搞定:
```
class VersionTestPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        println("VersionTestPlugin")
        // 添加afterEvaluate，不然无法添加依赖
        target.afterEvaluate {
            with(target.dependencies) {
                // 测试相关
                add("testImplementation", Libs.junit)
                add("androidTestImplementation", Libs.ext_junit)
                add("androidTestImplementation", Libs.espresso_core)
            }
        }
    }
}
```
后面想想好像使用bundles也是类似啊，而且使用插件的话，里面一键添加的依赖太多的话容易冗余，太少的话又没必要，想想好像Version Catalog这样子还更好一些。

反正ext、buildSrc、Composing build以及Version Catalog看需要使用吧。

## 参考文章
在学习的过程中参考了一些文章，还是得感谢各位大佬们的贡献:

[【Gradle7.0】依赖统一管理的全新方式，了解一下~](https://juejin.cn/post/6997396071055900680)

[迁移到 Gradle 7.x 使用 Version Catalogs 管理依赖](https://www.cnblogs.com/joy99/p/17397989.html)

[Android Gradle 三方依赖管理](https://juejin.cn/post/7130530401763737607)

[Android Gradle 三方依赖管理](https://www.jianshu.com/p/e5550180a8f9)

## 总结
这里实践了下Android里面Version Catalog的使用，并且实现了Version Catalog打包成本地maven仓库插件，并在其他项目中依赖使用，感觉还挺有意思的！