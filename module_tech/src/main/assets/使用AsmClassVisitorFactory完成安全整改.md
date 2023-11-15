# 使用AsmClassVisitorFactory完成安全整改
## 前言
前面写了一篇文章([《利用ASM完成第三方SDK安全整改》](https://juejin.cn/post/7293122002724945955))对项目中的安全漏洞做了些修改，里面有提到Transform在AGP7.0被标记为废弃，作为一个好奇的安卓开发，我觉得还是有必要学学被废弃后的新方法的-_-||，于是花了点时间，找了下资料，尝试了下，顺便记录下。

## Gradle版本要求
这里gradle版本当然需要升级到7.x才能使用，打算升级并且想用kts的话可以看下我之前的文章:

[《记录迁移gradle到kts》](https://juejin.cn/post/7293709429886189568)

不想升级还想使用ASM修改字节码的话，可以看下Transform那种方法(这里也要求gradle升级到6.1.1，AGP版本4.0):

[《利用ASM完成第三方SDK安全整改》](https://juejin.cn/post/7293122002724945955)

---
这里说下我的版本配置: Gradle Version 7.5.1，AGP 7.4.2。

## 编写插件
关于Gradle插件编写的内容，我之前也写了一篇文章，有需要的可以看下:

[《Gradle自定义插件实践与总结》](https://juejin.cn/post/7292952125193256995)

选择使用buildSrc编写插件的话，可以跳过这节，直接看AsmClassVisitorFactory部分，代码放buildSrc里面就行。

---
使用Transform方法的那篇文章里，我用的是发布到本地maven仓库的形式使用插件，当时没搞懂Composing build里面的插件，又学了学，这篇文章就用Composing build来做吧。

### Composing build编写插件
这里从头说清楚吧，Composing build实际就是多项目构建，我们先创建一个项目，在根目录下新建一个build-plugins目录，里面创建两个文件以及代码目录，结构如下:
```
// 用我代码举例了，包名自己定义
build-plugins
|--src/main/java/com/silencefly96/plugins/privacy
|--|--PrivacyPlugin.kt
|--build.gradle.kts
|--settings.gradle.kts
```
在settings.gradle.kts填入如下代码:
```
@file:Suppress("UnstableApiUsage")
pluginManagement {
    repositories {
        // 是用于从 Gradle 插件门户下载插件的默认仓库。
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "build-plugins"
include (":build-plugins")
```
注意下这里把rootProject指向了build-plugins，这样就不分项目的build.gradle.kts和模块的build.gradle.kts了，两个放一起了。

下面就是两个放一起的build.gradle.kts，代码如下:
```
buildscript {
    // 我这不加有问题，按道理repositories是不用的
    repositories {
        gradlePluginPortal()
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
}

// 插件的依赖关系
dependencies {
    implementation(gradleApi())
    implementation("com.android.tools.build:gradle:7.4.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.0")
}

gradlePlugin {
    // 注册插件
    plugins.register("privacyPlugin") {
        id = "privacy-plugin"
        implementationClass = "com.silencefly96.plugins.privacy.PrivacyPlugin"
    }
}
```
细心的可能会发现gradle和kotlin-gradle-plugin我们引入了两次，注意下buildscript里面的是给gradle脚本用的(classpath)，下面dependencies里面的是给自己代码使用的(implementation)。

配置好这些我们就来写PrivacyPlugin的代码:
```
package com.silencefly96.plugins.privacy

import org.gradle.api.Plugin
import org.gradle.api.Project

class PrivacyPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        println("PrivacyPlugin")
    }
}
```
这里就随便打印了下名称，sync一下就能在项目的module中使用了。

在主项目的根目录的settings.gradle.kts(和build-plugins区分开来)中引入build-plugins模块，要使用includeBuild:
```
...
include(":app")
...
includeBuild("build-plugins")
```

然后在要使用的模块的build.gradle.kts中根据插件id配置:
```
plugins {
    id("privacy-plugin")
    ...
}
```
build一下，控制台应该就会打印"PrivacyPlugin"了，至此插件我们就写好了，接下来就是重点的AsmClassVisitorFactory环节。

## AsmClassVisitorFactory使用
我觉得嘛，其实AsmClassVisitorFactory就是我们之前的Transform，这里在上面PrivacyPlugin同目录下新建一个PrivacyTransform(命名随意)，里面来写AsmClassVisitorFactory代码:
```
package com.silencefly96.plugins.privacy;

import com.android.build.api.instrumentation.*
import org.objectweb.asm.ClassVisitor

// 注意这里需要一个抽象类！
abstract class PrivacyTransform: AsmClassVisitorFactory<InstrumentationParameters.None> {

    override fun createClassVisitor(
        classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor {
        // 创建自定义的ClassVisitor并返回
        return PrivacyClassVisitor(nextClassVisitor, classContext.currentClassData.className)
    }

    // 过滤处理的class
    override fun isInstrumentable(classData: ClassData): Boolean {
        // 处理className: com.silencefly96.module_base.base.BaseActivity
        val className = with(classData.className) {
            val index = lastIndexOf(".") + 1
            substring(index)
        }

        // 筛选要处理的class
        return !className.startsWith("R\$")
                && "R" != className
                && "BuildConfig" != className
                // 这两个我加的，代替的类小心无限迭代
                && !classData.className.startsWith("android")
                && "AsmMethods" != className
    }
}
```
这里就两步，一个是创建自定义的ClassVisitor，里面实现ASM代码逻辑，第二个是对class的过滤，看自己需要吧，直接返回true也行。

写好AsmClassVisitorFactory后，需要在上面的PrivacyPlugin里面注册下:
```
package com.silencefly96.plugins.privacy

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class PrivacyPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        val androidComponents =
            project.extensions.getByType(AndroidComponentsExtension::class.java)

        androidComponents.onVariants { variant ->
            // 控制是否需要扫描依赖库代码， ALL / PROJECT
            variant.instrumentation.transformClassesWith(
                PrivacyTransform::class.java,
                InstrumentationScope.ALL
            ) {}

            // 可设置不同的栈帧计算模式
            variant.instrumentation.setAsmFramesComputationMode(
                FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
            )
        }
    }
}
```
这里可以着重看下InstrumentationScope.ALL和InstrumentationScope.PROJECT，之前的Transform的Scope可是有七种啊，这里只有两了，如果要对SDK修改的话就设置为ALL吧。

## PrivacyClassVisitor编写
上面自定义的ClassVisitor传入了一个PrivacyClassVisitor，下面就写下它的代码:
```
package com.silencefly96.plugins.privacy

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class PrivacyClassVisitor(nextVisitor: ClassVisitor, private val className: String)
    : ClassVisitor(Opcodes.ASM7, nextVisitor) {

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {

        val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)

        val newMethodVisitor = object: MethodVisitor(Opcodes.ASM7, methodVisitor) {

            override fun visitMethodInsn(
                opcode: Int,
                owner: String,
                name: String,
                descriptor: String,
                isInterface: Boolean
            ) {
                // 替换说明:
                // 1. 路径以”/“分割，而不是包名里面的”.“
                // 2. owner前不带”L“字符，descriptor内都要加上”L“字符
                // 3. descriptor里面参数及返回值类型后的”;“不能省，特别是参数列表最后一个参数后的”;“
                // 4. descriptor里面基本类型(比如V、Z)后不能添加”;“，否则匹配不上
                // 5. 方法签名一定要写对，参数及返回值的类型，抛出的异常不算方法签名
                // 6. 替换方法前后变量一定要对应，实例方法0位置是this，改为静态方法时，要用第一个参数去接收;
                // 7. 替换方法前后，参数加返回值的数量要相等

                // 替换调用 Environment.getExternalStorageDirectory() 的地方为应用程序的本地目录
                if (opcode == Opcodes.INVOKESTATIC && owner == "android/os/Environment" && name == "getExternalStorageDirectory" && descriptor == "()Ljava/io/File;") {
                    println("处理SD卡数据泄漏风险: $className")
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "com/silencefly96/module_base/utils/AsmMethods",
                        "getExternalDir",
                        "()Ljava/io/File;",
                        false
                    )
                } else if (opcode == Opcodes.INVOKEVIRTUAL && name == "registerReceiver" && descriptor == "(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;") {
                    // && owner.equals("android/content/Context")
                    println("处理动态注册广播: $className")
                    // 调用你自定义的方法，并传递 Context 和参数
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "com/silencefly96/module_base/utils/AsmMethods",
                        "registerZxyReceiver",
                        "(Landroid/content/Context;Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;",
                        false
                    )
                } else if (opcode == Opcodes.INVOKEVIRTUAL && owner == "android/database/sqlite/SQLiteDatabase" && name == "rawQuery" && descriptor == "(Ljava/lang/String;[Ljava/lang/String;)Landroid/database/Cursor;") {
                    println("处理SQL数据库注入漏洞 rawQuery: $className")
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "com/silencefly96/module_base/utils/AsmMethods",
                        "rawZxyQuery",
                        "(Landroid/database/sqlite/SQLiteDatabase;Ljava/lang/String;[Ljava/lang/String;)Landroid/database/Cursor;",
                        false
                    )
                } else if (opcode == Opcodes.INVOKEVIRTUAL && owner == "android/database/sqlite/SQLiteDatabase" && name == "execSQL" && descriptor == "(Ljava/lang/String;)V") {
                    println("处理SQL数据库注入漏洞 execSQL: $className")
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "com/silencefly96/module_base/utils/AsmMethods",
                        "execZxySQL",
                        "(Landroid/database/sqlite/SQLiteDatabase;Ljava/lang/String;)V",
                        false
                    )
                } else if (opcode == Opcodes.INVOKEVIRTUAL && owner == "java/util/zip/ZipEntry" && name == "getName" && descriptor == "()Ljava/lang/String;") {
                    println("处理ZipperDown漏洞: $className")
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "com/silencefly96/module_base/utils/AsmMethods",
                        "getZipEntryName",
                        "(Ljava/util/zip/ZipEntry;)Ljava/lang/String;",
                        false
                    )
                } else if (opcode == Opcodes.INVOKESTATIC && owner == "android/util/Log" && descriptor == "(Ljava/lang/String;Ljava/lang/String;)I") {
                    println("处理日志函数泄露风险 $name: $className")
                    if (name == "e") {
                        // 错误日志还是有用的
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "com/silencefly96/module_base/utils/AsmMethods",
                            "optimizeLogE",
                            "(Ljava/lang/String;Ljava/lang/String;)I",
                            false
                        )
                    } else {
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "com/silencefly96/module_base/utils/AsmMethods",
                            "optimizeLog",
                            "(Ljava/lang/String;Ljava/lang/String;)I",
                            false
                        )
                    }
                } else if (opcode == Opcodes.INVOKEVIRTUAL && owner == "android/webkit/WebSettings" && name == "setJavaScriptEnabled" && descriptor == "(Z)V") {
                    println("处理Webview组件跨域访问风险: $className")
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "com/silencefly96/module_base/utils/AsmMethods",
                        "setZxyJsEnabled",
                        "(Landroid/webkit/WebSettings;Z)V",
                        false
                    )
                } else if (opcode == Opcodes.INVOKEVIRTUAL && owner == "com/tencent/smtt/sdk/WebSettings" && name == "setJavaScriptEnabled" && descriptor == "(Z)V") {
                    println("处理X5Webview组件跨域访问风险: $className")
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "com/silencefly96/module_base/utils/AsmMethods",
                        "setZxyX5JsEnabled",
                        "(Lcom/tencent/smtt/sdk/WebSettings;Z)V",
                        false
                    )
                } else {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                }
            }
        }
        return newMethodVisitor
    }
}
```
还是原来ASM代替的代码，就不多解释了，不过这里明显比之前简单多了啊，不错！

唯一需要注意的是ASM的版本，我这要求Opcodes.ASM7，低了会报错，这问题遇到好多次了-_-||

关于用来替换的AsmMethods类，读者可以自己编写，需要要注意的是这个类里面别被替代搞得无限迭代了，另外一个就是kotlin静态方法记得加上JvmStatic注解:
```
// 注意包名一致啊！
package com.silencefly96.module_base.utils

object AsmMethods {

    // ASM替换代码勿动: 替换获取外部文件
    @JvmStatic
    fun getExternalDir(): File {
        var result = File("")
        // ...
        return result
    }
```

## 使用
上面代码写好的话，目录整体结构如下(忽略我多余的文件):
![d.png](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/e71235c3cc5140a18d6f940cfc45cda3~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=473&h=489&s=22810&e=png&b=3c3f41)

在要使用的地方加入插件，比如我这是app模块:
```
plugins {
    id("privacy-plugin")
}
```
app的MainActivity放了个测试用的代码:
```
fun onTestRegisterZxyReceiver() {
    val cw: ContextWrapper = object : ContextWrapper(this) {
        override fun registerReceiver(
            receiver: BroadcastReceiver?,
            filter: IntentFilter
        ): Intent? {
            Log.d("TAG", "ContextWrapper registerReceiver: ")
            return super.registerReceiver(receiver, filter)
        }
    }
    val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("TAG", "onReceive: " + intent.action)
        }
    }
    val intentFilter = IntentFilter()
    intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
    Log.d("TAG", "registerZxyReceiver: invoke before")
    cw.registerReceiver(receiver, intentFilter)
}
```
在AS中选择rebuild，一会在控制台就能看到ASM处理的输出了，速度比之前Transform方式还更快(这个是有增量更新的):
![d.png](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/9e38f217556e4965b4999dc00f6846f3~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1657&h=522&s=106634&e=png&b=2c2c2c)

看下输出，打印了很多，瞄一眼我们在MainActivity内的有打印，如果说你觉得打印不能证明ASM修改成功，我们可以继续看下APK包:
![image.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/5bb5d40d5b5a43ab9722ac26026f4477~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1125&h=897&s=109316&e=png&b=3d4042)

点开MainActivity的字节码看一下:
![image.png](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/726497eafa1e4a9d83da77f542dafdd0~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1561&h=842&s=123299&e=png&b=2b2b2b)

根据字节码对应的代码行数，对比下源码位置:
![image.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/68a0f507028c42b8a9a25ab57bfb4ed5~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1030&h=846&s=137562&e=png&b=2c2c2c)

第46行对日志的替换，第47行对动态注册广播的替换是不是生效了，(●ˇ∀ˇ●)

## 文章参考及源码
参考文章:

[现在准备好告别Transform了吗？ | 拥抱AGP7.0](https://juejin.cn/post/7016147287889936397)

[android官方文档](https://developer.android.com/reference/tools/gradle-api/7.2/com/android/build/api/instrumentation/AsmClassVisitorFactory)

---
[Demo源码](https://github.com/silencefly96/fundark/tree/main/build-plugins)(可能随时有改动，练手的项目) 

## 总结
这篇文章用了Composing build的方式编写了gradle的插件，并使用gradle7.x的AsmClassVisitorFactory来对项目及SDK的代码进行整改，学习了！

