# Android依赖管理实践与总结
## 前言
最近整理了下自己练手的项目，顺便把Android依赖管理过了一遍，原谅我之前连buildSrc都没用过，又学了下Composing build，感觉挺有收获的，在此总结下。

截至目前，据我所知啊，Android的依赖管理大致有以下几种:
- 直接按库引入
- ext管理
- buildSrc
- Composing build
- Version Catalogs

其实我觉得吧，各有各的好处，也各有各的痛点吧，以下是我觉得几个值得考虑的地方:
- 统一管理
- 降低更新范围
- 方便使用: 能跳转、有更新提示、好引入

主要就是前面两个地方有冲突: 统一管理了，那一旦有更新那就得随着更新(对插件来说); 而降低了更新范围，那就势必要把依赖划分成更小的模块，那就造成了混乱。

其中“直接按库引入”和“buildSrc”就是两个极端，“直接按库引入”按每个module的每个依赖更新，而“buildSrc”一旦更新就会造成全量更新(不知道理解对不对)。

下面就一个一个来分析吧！

## 直接按库引入
直接按库引入的方式估计大家都熟悉了，第三方库一般也就给的这种形式，比如下面就是在app模块的dependence中添加Retrofit依赖:
```
dependencies {
    // Retrofit 网络通信框架
    implementation 'com.squareup.retrofit2:retrofit:2.6.1'
    implementation 'com.squareup.retrofit2:converter-gson:2.6.1'
}
```
这没什么好讲的。

## ext管理
ext管理方式也比较简单，就是在项目的build.gradle或者单独的gradle文件中配置依赖的版本号、字符串之类的。下面就是一个专门配置依赖的gradle文件:
```
// config.gradle
ext {
    android = [
        compileSdkVersion : 30,
        minSdkVersion : 19,
        targetSdkVersion : 30,
        versionCode : 1,
        versionName : "1.0",
    ]

    dependencies = [
        // 基本库
        kotlin: "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version",
        core_ktx: 'androidx.core:core-ktx:1.3.2',
        appcompat: 'androidx.appcompat:appcompat:1.2.0',
        material: 'com.google.android.material:material:1.2.1',
        constraintlayout: 'androidx.constraintlayout:constraintlayout:2.0.1',
    ]
}
```
使用前需要在项目的build.gradle中引入:
```
apply from: 'config.gradle'
```
然后就能在各个module的build.gradle中使用，像下面这样:
```
android {
    compileSdkVersion rootProject.ext.android.compileSdkVersion
    defaultConfig {
        minSdkVersion rootProject.ext.android.minSdkVersion
        targetSdkVersion rootProject.ext.android.targetSdkVersion
        versionCode rootProject.ext.android.versionCode
        versionName rootProject.ext.android.versionName
    }
    ...
}

dependencies {
    // kotlin
    api rootProject.ext.dependencies.kotlin
    ...
}
```
这里偷懒了，直接把dependence的链接和版本号写一起了，可以分开写成两份。

使用ext方式写的引用，虽然统一管理了，但是用久了就会觉得恶心，一来收不到IDE的更新提示，二来不能点击跳转到定义变量的地方，除非以后都不改这些引用了，否则就怎么都看它不爽。

## buildSrc
buildSrc是为了解决什么问题呢？它就是gradle会自动识别的一个文件夹，也可以说是一个插件，它就是为了解决上面说的变量不能跳转的问题，虽然有人说能够提示更新，不过我好像没发现。

我也是从网上找到资料，自己试了一遍，调试到能够使用了，再总结的，下面简单看下:

1. 创建buildSrc文件夹
    在项目的根目录下创建一个名为“buildSrc”的文件夹，名字不要乱改，gradle会特别识别它。

2. 创建build.gradle.kts文件
    这里使用了kts来编写编译脚本，我也不想改了，因为用groovy麻烦好多哦，而且这能用！
    ```
    plugins {
        `kotlin-dsl`
    }
    repositories{
        jcenter()
    }
    ```

3. 创建Dependencies文件
    这里直接在buildSrc文件夹的“src/main/java/”目录下，创建一个Dependencies.kt文件，里面就是我们管理的依赖了:
    ```
    // 不要package！
    object BuildVersion {
        const val compileSdkVersion = 31
        const val minSdkVersion = 19
        const val targetSdkVersion = 30
        const val versionCode = 1
        const val versionName = "1.0"
    }

    object Versions {
        //基本库
        const val kotlin = "1.4.21"
        const val core_ktx = "1.3.2"
        const val appcompat = "1.2.0"
        const val material = "1.2.1"
        const val constraintlayout = "2.0.1"
        ...
    }
    ```
    这里也可以选择给Dependencies文件加个包名，定义个package(xxx.xxx.xxx)，但是直接写到java目录下后面不用去引入类。

4. 使用buildSrc
    先不管Sync，我们在我们module中就可以修改引用了:
    ```
    android {
        compileSdkVersion BuildVersion.compileSdkVersion
        defaultConfig {
            applicationId "com.silencefly96.fundark"
            minSdkVersion BuildVersion.minSdkVersion
            targetSdkVersion BuildVersion.targetSdkVersion
            versionCode BuildVersion.versionCode
            versionName BuildVersion.versionName
        }
    }
    dependencies {
        // kotlin
        api Libs.kotlin
        api Libs.core_ktx
        // base相关基类需要使用
        api Libs.appcompat
        // RecyclerView适配器等
        api Libs.material
        // Constraintlayout
        api Libs.constraintlayout
    }
    ```
    写完之后Sync一下，或者重启下Android Studio应该就差不多了。

这里和ext形式的区别还是挺大的，Libs.kotlin这样的变量我们可以用ctrl + 左键去访问了，方便了很多。

但是前面说到了buildSrc就是一个极端，按我理解啊，它是一个插件，相对独立，如果你修改了里面一个依赖的版本号，那就意味着这个插件修改了，那所有依赖这个插件的module都是需要修改的，额，那就是全量更新了。

## Composing build
一开始，我觉得Composing build和buildSrc差不多，也是一个插件，后面再想想，其实Composing build应该更像一个独立的project，而不是我们项目中的module，这种形式更像是多项目构建。

Composing build可以定义很多plugin，将依赖分成几个模块，这样依赖的修改会限定在一定的范围，这样就不会造成全量更新了，也达到了集中管理的要求。

下面就来简单看下我所理解的Composing build，如有不对可以评论区指出。

### 简单使用
首先，和buildSrc一样，在项目根目录下新建一个“version-plugin”文件夹，在里面创建两个文件build.gradle.kts和settings.gradle.kts，并在其中填入下面代码:

在build.gradle.kts中:
```java
plugins {
    `kotlin-dsl`
}
gradlePlugin {
    // 注册插件
    plugins.register("versionPlugin") {
        id = "version-plugin"
        implementationClass = "com.silencefly96.version.VersionPlugin"
    }
}
```

在settings.gradle.kts中:
```java
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "version-plugin"
include (":version-plugin")
```

看到settings.gradle是不是有点明白为什么它和单独的module有区别了，“version-plugin”文件夹更像是一个项目，实际我们引入它的时候也是这么做的。

创建完上面两个脚本，我们开始创建Dependencies.kt文件，和buildSrc类似，把他放到“src/main/java/”目录下，内容和buildSrc那的一样:
```
// 不要package！
object BuildVersion {
    const val compileSdkVersion = 31
    const val minSdkVersion = 19
    const val targetSdkVersion = 30
    const val versionCode = 1
    const val versionName = "1.0"
}

object Versions {
    //基本库
    const val kotlin = "1.4.21"
    const val core_ktx = "1.3.2"
    const val appcompat = "1.2.0"
    const val material = "1.2.1"
    const val constraintlayout = "2.0.1"
    ...
}
```
也可以选择放到自定义的package里面，但是这样写的话就要注意在module的build.gradle中要手动引入，下面是一个例子:
```
// Dependencies.kt放在下面包名对应目录
import com.silencefly96.version.Dependencies
```

项目中要引入我们新建的“version-plugin”，需要通过插件的形式，上面build.gradle中我们已经注册了插件，下面就把插件的class创建出来，com.silencefly96.version包名对应目录下新建VersionPlugin.kt文件
```
// 这里需要package
package com.silencefly96.version

import org.gradle.api.Plugin
import org.gradle.api.Project

class VersionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        println("VersionPlugin")
    }
}
```

创建好插件，“version-plugin”这个“project”就算完成了，下面是到我们项目中去引入并使用。

首先，在我们项目根目录下的setting.gradle文件中引入“version-plugin”:
```
include ':app'
...
includeBuild 'version-plugin'
```
注意这里用的不是include，而是includeBuild。

接下来，在需要使用的module的build.gradle中引入插件:
```
plugins {
    id 'com.android.application'
    ...
    id 'version-plugin'
}
```

然后，依赖的使用也buildSrc一样，如果是从buildSrc改过来的，引用都不用动了:
```
android {
    compileSdkVersion BuildVersion.compileSdkVersion
    defaultConfig {
        applicationId "com.silencefly96.fundark"
        minSdkVersion BuildVersion.minSdkVersion
        targetSdkVersion BuildVersion.targetSdkVersion
        versionCode BuildVersion.versionCode
        versionName BuildVersion.versionName
    }
}
dependencies {
    // kotlin
    api Libs.kotlin
    api Libs.core_ktx
    // base相关基类需要使用
    api Libs.appcompat
    // RecyclerView适配器等
    api Libs.material
    // Constraintlayout
    api Libs.constraintlayout
}
```
可以试一下，这里也能点击跳转到定义的变量。

不过这么说，比buildSrc复杂了这么多，但是最后还是一样的用法，耍猴呢？那当然不是这样，下面看下复杂点的用法。

### 多插件构建
首先说明啊，我找了挺多资料，搞得也不是很懂(大致和那些千篇一律的博客强一些)，下面是我根据看的资料以及自己理解写的用法。

我这创建了几个插件用来管理不同的依赖:
- VersionBasePlugin
- VersionTestPlugin
- VersionThirdPlugin

里面代码拿VersionTestPlugin举例子吧:
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
我这把依赖的添加放到插件里面去了，这样我们module的build.gradle中的dependencies基本就可以去掉了:
```
plugins {
    id 'com.android.library'
    ...
    id 'version-base-plugin'
    // id 'version-test-plugin'
    id 'version-third-plugin'
}
dependencies {

}
```
这里可以选择性的添加plugin，添加上对应plugin后就可以把dependencies里面的依赖去掉了。

不过，看别的博主说这样就实现了非全量更新，我是有点怀疑，插件不变就能防止全量更新吗？去修改Dependencies文件不会导致所有插件都更新吗？有时间再验证下

## Version Catalogs
Version Catalogs我项目还没升级到gradle7.0，等升级了再搞，先放个别人的链接:

[Version Catalogs 配置](https://juejin.cn/post/7130530401763737607#heading-6)

## 小结
最后就从开头的几个方面来看下这几种依赖的优缺点吧:
- 直接按库引入: 非全量更新，有更新提示; 不能统一管理，比较混乱。
- ext管理: 集中管理，非全量更新，好引入; 无法提示更新，无法跳转依赖
- buildSrc: 集中管理，能跳转依赖，好引入; 全量更新，提示更新不好使
- Composing build: 分模块集中管理，非全量更新，能跳转依赖; 不好引入，没法提示更新
- Version Catalogs: 待更新

## 参考文章
[是时候弃用 buildSrc ,使用 Composing builds 加快编译速度了](https://juejin.cn/post/7208015274079387707)
[再见吧 buildSrc, 拥抱 Composing builds 提升 Android 编译速度](https://juejin.cn/post/6844904176250519565)
[Android Gradle 三方依赖管理](https://juejin.cn/post/7130530401763737607)