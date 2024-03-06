# 记录迁移gradle到kts
## 前言
最近练手了[《Android依赖管理实践与总结》](https://juejin.cn/post/7290746997061074959)，里面关于Version Catalogs的部分鸽了，因为我练手的项目没有升到gradle7.x，最近花了点时间搞了下，这里记录下吧，也踩了一些坑，希望能够对读者有帮助。

## 知识了解
这里还是推荐修改之前，先看下Android官方的文档吧: 

[将 build 配置从 Groovy 迁移到 KTS](https://developer.android.google.cn/studio/build/migrate-to-kts?hl=zh-cn)

我这也参考了几篇文章，也贴一下，他们写的会更全一些(虽然并不能完全解惑)，我这就是希望做个记录:

[Android gradle迁移至kts](https://juejin.cn/post/7116333902435893261)

[Android gradle 插件升级和 kts 迁移踩坑指南](https://juejin.cn/post/7148437364002521125)

[小伙快把你的Gradle从Groovy迁移到KTS](https://juejin.cn/post/7230416597012283453)

有一定了解之后，我们就开始下手改了！

## 迁移gradle到kts

### 升级gradle版本
看下官方文档的说明，要使用KTS，AGP要升级到4.0，也就是说gradle版本要对应升级到6.1.1:
> Android Gradle 插件 4.0 支持在 Gradle build 配置中使用 KTS。

我这用的版本是: Gradle Version 7.5.1，AGP 7.4.2。还有个问题就是，要注意下Android Studio，gradle以及AGP之间的关系。

### 修改文件名
这里需要把安卓项目中的.gradle文件，全部改成.gradle.kts，一般来说，主要就下面三个吧:

- setting.gradle
- 项目的build.gradle
- module的build.gradle

如果还使用了ext来做依赖管理，可能麻烦一些，需要[另外处理一下](https://juejin.cn/post/7116333902435893261#heading-6)。

### 变量修改
因为从groovy语言换到了kotlin，语法有点变化，需要修改下，主要是下面几个内容:

- 字符串
- 括号
- 等于号
- Boolean变量

下面一个一个说，官方文档也有，但是肯定要比官方文档简单些吧。

#### 替换字符串
这里就是拿[别人的正则替换](https://juejin.cn/post/7116333902435893261#heading-2)去修改:
```
正则表达式
'(.*?[^\\])'
作用范围为
"$1"
```

在AS中ctrl + shift + r开启全局替换，指定file mask为“*.gradle”，大致瞄一眼查找结果，给它全部替换了就行。

#### 增加括号
对括号的增加也可以用正则替换，和上面类似:
```
正则表达式
(\w+) (([^=\{\s]+)(.*))
作用范围为
$1($2)
```

#### 增加等于号
上面两步改完之后，还是有一些问题的，我这改的比较多的就是括号问题，比如:
```
android {
    compileSdk(BuildVersion.compileSdkVersion)
    ...
}
// 应该是下面这样
compileSdk  = BuildVersion.compileSdkVersion
```
剩下的minSdk、targetSdk等也是一样要加等于号，再一个就是仓库的也要加等于号，比如引入阿里云的仓库:
```
maven{ url = uri("https://maven.aliyun.com/repository/google/") }
maven{ url = uri("https://maven.aliyun.com/repository/public/") }
```
我看其他项目可以去掉等于号的，直接url带括号，不知道是不是有设置插件。

#### 修改Boolean变量
这里修改Boolean变量，实际就是对minifyEnabled修改，因为都会遇到这个:
```
minifyEnabled false
// 改为:
isMinifyEnabled = false
```

### 修改仓库配置
gradle7.x后把依赖管理放到了setting.gradle里面，这里要处理下，把原先项目build.gradle仓库放到这，比如:
```
// 插件管理
pluginManagement {
    repositories {
        // 是用于从 Gradle 插件门户下载插件的默认仓库。
        gradlePluginPortal()
        maven{ url = uri("https://maven.aliyun.com/repository/google/") }
        maven{ url = uri("https://maven.aliyun.com/repository/public/") }
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

dependencyResolutionManagement {
    // 存储库模式: 在项目的子 module 中配置仓库信息会导致编译失败。
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven{ url = uri("https://maven.aliyun.com/repository/google/") }
        maven{ url = uri("https://maven.aliyun.com/repository/public/") }
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

// 指定根目录，不加这个好像也没报错
rootProject.name = "fundark"
include(":app")
```
这时候就可以把项目的build.gradle.kts改成下面这样了:
```
buildscript {
    // 不知道为什么我这不加这个repositories会报错。。。
    repositories {
        maven{ url = uri("https://maven.aliyun.com/repository/google/") }
        maven{ url = uri("https://maven.aliyun.com/repository/public/") }
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.0")
    }
}

// 这个task也需要修改，groovy语法不对了
task<Delete>("clean") {
    delete(rootProject.buildDir)
}
```
按理来说build.gradle.kts的buildscript里面放dependencies就行了，可是我这会报错，还得把repositories加上。

这个task也是一个修改点，这里提前说一下。

### 问题处理
修改的地方大致就上面这些，后面就是处理升级带来的坑了。

#### task语法问题
第一个就是上面提到的task语法问题了，我这sync时会报错，修改如下:
```
task clean(type: Delete) {
    delete rootProject.buildDir
}
// 改为
task<Delete>("clean") {
    delete(rootProject.buildDir)
}
```

#### compileSdk等问题
这里有个很神奇的问题，估计是升级gradle版本的原因，我这之前都用的compileSdkVersion，给它赋值Int型，现在提示它只接受字符串了，后面看了下，这些都得改了:
```
android {
    compileSdkVersion BuildVersion.compileSdkVersion
    defaultConfig {
        applicationId "com.silencefly96.fundark"
        minSdkVersion BuildVersion.minSdkVersion
        targetSdkVersion BuildVersion.targetSdkVersion
        versionCode BuildVersion.versionCode
        versionName BuildVersion.versionName
        ...
    }
}
// 改为
android {
    compileSdk  = BuildVersion.compileSdkVersion
    defaultConfig {
        applicationId = ("com.silencefly96.fundark")
        minSdk  = (BuildVersion.minSdkVersion)
        targetSdk  = (BuildVersion.targetSdkVersion)
        versionCode = (BuildVersion.versionCode)
        versionName = (BuildVersion.versionName)
        ...
    }
}
```

#### 依赖冲突
可能时升级了gradle版本，我这本来没问题的代码提示了一些依赖冲突，后面升级了几个依赖版本:

第一个是lifecycle_viewmodel_ktx:，从"2.2.0" 升级到了 "2.4.0"。

第二个是升级了kotlin-gradle-plugin，从1.5.20升级到了1.6.0，下面两个classpath会冲突:
```
classpath("com.android.tools.build:gradle:7.4.2")
classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.0")
```
还看了一篇[stackOverflow文章](https://stackoverflow.com/questions/67699823/module-was-compiled-with-an-incompatible-version-of-kotlin-the-binary-version-o)，有详细写为什么，有需要可以看下。

#### ext变量的使用
在groovy里面ext用起来很方便，比如下面:
```java
// project的build.gradle
buildscript{
    ext.version="1.0.0"
    // ...
    repositories{
        println("version: "+rootProject.ext.version)
    }
}
allprojects {
    repositories{
        println("commonLib: "+rootProject.ext.version)
    }
}
// module的build.gradle
dependencies{
    println("commonLib: "+rootProject.ext.version)
}
```
ext是全局的，我们只要在project的build.gradle的buildscript里面定义，就能在buildscript、插件及依赖的repositories、module的build.gradle中使用，然而在kts里面就不行了。

首先，ext不能在kts的buildscript里面使用了，根目录和allprojects倒是不受影响:
```java
// project的build.gradle
ext {
    set("githubUser", "xxx")
    set("githubPassword", "xxx")
}

allprojects {
    ext {
        set("githubUser", "xxx")
        set("githubPassword", "xxx")
    }
}
```
在project的build.gradle的buildscript要用kts提供的新东西——extra:
```java
buildscript {
    // 使用extra设置全局变量
    // 方式一:
    val githubUser by extra("xxx")
    val githubPassword by extra("xxx")
    // 在module中使用: val githubUser: String by rootProject.extra
    // 方式二:
    extra["githubUser"] = "xxx"
    extra["githubPassword"] = "xxx"
    // 方式三:
    extra.set("githubUser", "xxx")
    extra.set("githubPassword", "xxx")
}
```
用起来和groovy里面的ext类似，而且和kts里面ext存的值指向位置是一样的，比如下面的值，ext和extra是一样的:
```java
username = rootProject.extra["githubUser"].toString()
password = rootProject.extra["githubPassword"].toString()
```
那就有人说了，不就是把ext换成了extra吗，基本没区别。那还真不一样，上面说groovy的时候，是包含了插件及依赖的repositories的，而转移到kts后，这两个是在setting.gradle里面的，这样就没法用了。

那在settting.gradle里面定义extra行不行，确实可以还能在repositories用:
```java
dependencyResolutionManagement{
    extra.set("githubUser", "xxx")
    extra.set("githubPassword", "xxx")
    repositories {
        // ...
    }
}
```
不过，经过我的测试这里设置的extra，在build.gradle里面拿不到，我打印了下他们的对象，对应不上，源码看得头疼不太想看(有时间系统性学下吧)，总之这里就是不一样吧。

那如何解决，我这是有个存在local.properties的变量，用文件里面取出来就行了，不知道读者没有有什么好办法。

### 整体梳理
迁移过程可能会有很多问题，最好还是拿一个项目的来参考，我下面也贴一下我迁移后的三个文件吧: setting.gradle 、项目的build.gradle 、module的build.gradle，读者可以参考下:

#### setting.gradle
迁移前:
```
include ':app'
```
迁移后:
```
pluginManagement {
    repositories {
        gradlePluginPortal()
        // 依赖本地仓库
        maven{ url = uri("./privacy_repo") }
        maven{ url = uri("https://maven.aliyun.com/repository/google/") }
        maven{ url = uri("https://maven.aliyun.com/repository/public/") }
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }

}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven{ url = uri("https://maven.aliyun.com/repository/google/") }
        maven{ url = uri("https://maven.aliyun.com/repository/public/") }
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

rootProject.name = "fundark"
include(":app")
```

#### 项目的build.gradle
迁移前:
```
buildscript {
    // ext不能使用了
    ext.kotlin_version = "1.4.21"
    repositories {
        maven{ url'https://maven.aliyun.com/repository/google/' }
        maven{ url'https://maven.aliyun.com/repository/public/' }
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:7.4.2'
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.20"
    }
}

allprojects {
    repositories {
        maven{ url'https://maven.aliyun.com/repository/google/' }
        maven{ url'https://maven.aliyun.com/repository/public/' }//central仓和jcenter仓的聚合仓
        google()
        mavenCentral()
    }
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
```
迁移后:
```
buildscript {
    // 按理来说应该不用写了
    repositories {
        maven{ url = uri("https://maven.aliyun.com/repository/google/") }
        maven{ url = uri("https://maven.aliyun.com/repository/public/") }
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.20")
    }
}

task<Delete>("clean",{
    delete(rootProject.buildDir)
})
```

#### module的build.gradle
迁移前:
```
plugins {
    id 'com.android.application'
    id 'kotlin-android'
    id 'kotlin-kapt'
    id 'kotlin-parcelize'
}

android {
    compileSdkVersion BuildVersion.compileSdkVersion
    defaultConfig {
        applicationId "com.silencefly96.fundark"
        minSdkVersion BuildVersion.minSdkVersion
        targetSdkVersion BuildVersion.targetSdkVersion
        versionCode BuildVersion.versionCode
        versionName BuildVersion.versionName

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = '1.8'
    }
    buildFeatures {
        viewBinding true
        dataBinding true
    }
}

dependencies {
    //从基础库继承各个依赖
    implementation project(':module_base')
}
```
迁移后:
```
plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

android {
    compileSdk  = BuildVersion.compileSdkVersion
    defaultConfig {
        applicationId = ("com.silencefly96.fundark")
        minSdk  = (BuildVersion.minSdkVersion)
        targetSdk  = (BuildVersion.targetSdkVersion)
        versionCode = (BuildVersion.versionCode)
        versionName = (BuildVersion.versionName)

        testInstrumentationRunner = ("androidx.test.runner.AndroidJUnitRunner")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = (JavaVersion.VERSION_1_8)
        targetCompatibility = (JavaVersion.VERSION_1_8)
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = (true)
        dataBinding = (true)
    }
}

dependencies {
    //从基础库继承各个依赖
    implementation(project(":module_base"))
}
```
有些括号自动替换的时候多余了，可以手动去掉。

## 总结
没什么技术含量，迁移过程遇到挺多坑，有些现在还没弄明白，就记录下吧！