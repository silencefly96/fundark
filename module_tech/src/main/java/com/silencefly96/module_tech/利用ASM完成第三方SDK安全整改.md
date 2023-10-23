# 利用ASM完成第三方SDK安全整改
## 前言
上一篇文章写了下[Gradle自定义插件的编写](https://juejin.cn/post/7292952125193256995)，其主要目的还是为了给这篇文章提供支撑。

下面我将利用Transform(AGP7.0被标记为废弃)配合ASM来做一些SDK的安全整改，项目比较老，所以技术不是很新，踩了很多坑，方法会过时，知识不会过时。

## 关于整改
首先还是得说下ASM能帮我们整改什么东西吧，不然没有头绪的往下看代码会感觉好乱。ASM能够对JAVA字节码修改，最字节码进行增删改，比如下面这个方法:
```
Environment.getExternalStorageDirectory()
```

获取外部SDK目录，这属于隐私权限了，一般是不能随便调用的。如果这行代码在我们自己代码里面的话，那我们可以直接修改，但是如果是第三方SDK的话，那就麻烦了，要不是升级SDK，期待厂商能解决，要不就只能我们自己想办法了。

而字节码技术，就给了我们一种手段，比如我想把项目内所有调用这个方法的的地方，全却换成我自己的一个方法:
```
    fun getExternalDir(): File {
        // 控制动态执行
        if( hasPermission ) {
            ...
        } else {
            ...
        }
    }
```
这样就能防止SDK在同意隐私协议前去调用隐私方法了，或者进一步反射执行getExternalStorageDirectory规避静态代码扫描。

明白这样一个例子后，我们就能举一反三了，比如下面一些例子:
```
// 动态注册广播漏洞
context.registerReceiver(...)

// WebView组件跨域访问风险
setting.setJavaScriptEnabled(true)

// SQL数据库注入漏洞
database.execSQL(...)
```
我们都能将他们转到我们的逻辑，并对它们进行修改，以满足要求。

## 字节码与ASM
关于字节码和ASM我不想写太多，在掘金上看到有一篇文章写的非常不错了(虽然后面Transform部分有问题)，可以移步学习一下:
[Android - ASM 插桩你所需要知道的基础](https://juejin.cn/post/7000572440988352549)

---
下面我们就开始ASM整改之旅吧！

## 创建插件
要使用ASM，首先我们得创建一个插件，让这个插件参与gradle构建的过程，在所有字节码及JAR包都准备好时，对它们所有进行修改。

创建插件部分上一篇文章已经写到了，可以看下: [Gradle自定义插件实践与总结](https://juejin.cn/post/7292952125193256995)，不过我试了下使用Composing build插件去做有点问题(是我太菜了)，还是用本地maven的方式吧，后面我再研究研究。

以privacy-plugin为例，要在原先插件基础上修改，让他支持Transform和ASM，首先需要添加一些依赖,修改build.gradle文件，加入下面内容:
```
dependencies {
    // 需要用的的API，少了编译报错
    implementation gradleApi()
    implementation localGroovy()

    // Transform用到的依赖
    implementation 'com.android.tools.build:gradle:4.0.0'

    // ASM依赖
    implementation 'org.ow2.asm:asm:9.1'
    implementation 'org.ow2.asm:asm-commons:9.1'
}
```

注意这里要求gradle插件最低版本是4.0.0，对应gradle版本是6.1.1，可以打开你的project structure看一下是否符合要求，做好升级。
![ddd.png](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/dad836eae0064894ad5e9007dbeefa28~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1446&h=759&s=35139&e=png&b=3d4043)

插件部分我就不多讲了，上篇文章写的很详细了，注意下privacy-plugin的目录结构，多出来的两个文件就是我们的Transform和ASM代码:
![d.png](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/40d05d2958ac407688ae434d5416738c~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=547&h=458&s=16690&e=png&b=3d4042)

## 使用Transform
Transform是gradle的一个工具，盗用一张[别人的图](https://www.jianshu.com/p/47da7fb264db):
![image.png](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/219ac7aa351d4df48a9b60763a53682f~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=575&h=806&s=202253&e=png&b=fdfbfb)

Transform就是能够在字节码打包成dex之前，遍历所有class以及jar包，让我们访问并进行修改，修改的工具就是ASM(或者Javassist等)。

---
言归正传，在上面添加好依赖后，我们就可以在PrivacyPlugin同目录下新建一个PrivacyTransform.groovy文件来写我们的transform，这里最好使用已有的模板，不容易出错!!

下面是我找到别人的一个模板代码:
```
package silencefly96.privacy

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import com.silencefly96.privacy.PrivacyClassVisitor
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

class PrivacyTransform extends Transform {

    @Override
    String getName() {
        return "PrivacyTransform"
    }

    /**
     * 需要处理的数据类型，有两种枚举类型
     * CLASS->处理的java的class文件
     * RESOURCES->处理java的资源
     * @return
     */
    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    /**
     * 指 Transform 要操作内容的范围，官方文档 Scope 有 7 种类型：
     * 1. EXTERNAL_LIBRARIES        只有外部库
     * 2. PROJECT                   只有项目内容
     * 3. PROJECT_LOCAL_DEPS        只有项目的本地依赖(本地jar)
     * 4. PROVIDED_ONLY             只提供本地或远程依赖项
     * 5. SUB_PROJECTS              只有子项目。
     * 6. SUB_PROJECTS_LOCAL_DEPS   只有子项目的本地依赖项(本地jar)。
     * 7. TESTED_CODE               由当前变量(包括依赖项)测试的代码
     * @return
     */
    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    /**
     * 是否增量编译
     * @return
     */
    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation)
        println "开始PrivacyOptimizeTransform"
        _transform(transformInvocation.context, transformInvocation.inputs, transformInvocation.outputProvider)
        println "结束PrivacyOptimizeTransform"
    }

    /**
     *
     * @param context
     * @param inputs 有两种类型，一种是目录，一种是 jar 包，要分开遍历
     * @param outputProvider 输出路径
     */
    void _transform(Context context, Collection<TransformInput> inputs, TransformOutputProvider outputProvider) throws IOException, TransformException, InterruptedException {
        if (!incremental) {
            //不是增量更新删除所有的outputProvider
            outputProvider.deleteAll()
        }
        inputs.each { TransformInput input ->
            //遍历目录
            input.directoryInputs.each { DirectoryInput directoryInput ->
                handleDirectoryInput(directoryInput, outputProvider)
            }
            // 遍历jar 第三方引入的 class
            input.jarInputs.each { JarInput jarInput ->
                handleJarInput(jarInput, outputProvider)
            }
        }
    }

    static void handleDirectoryInput(DirectoryInput directoryInput, TransformOutputProvider outputProvider) {
        if (directoryInput.file.isDirectory()) {
            directoryInput.file.eachFileRecurse { File file ->
                String name = file.name
                if (filterClass(name)) {
                    // 用来读 class 信息
                    ClassReader classReader = new ClassReader(file.bytes)
                    // 用来写
                    ClassWriter classWriter = new ClassWriter(0 /* flags */)
                    //todo 改这里就可以了
                    def classVisitor = new PrivacyClassVisitor(classWriter)
                    classVisitor.setClassName(file.absolutePath)
                    // 下面还可以包多层
                    classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
                    // 重新覆盖写入文件
                    byte[] code = classWriter.toByteArray()
                    FileOutputStream fos = new FileOutputStream(
                            file.parentFile.absolutePath + File.separator + name)
                    fos.write(code)
                    fos.close()
                }
            }
        }
        // 把修改好的数据，写入到 output
        def dest = outputProvider.getContentLocation(directoryInput.name, directoryInput.contentTypes,
                directoryInput.scopes, Format.DIRECTORY)
        FileUtils.copyDirectory(directoryInput.file, dest)
    }

    static void handleJarInput(JarInput jarInput, TransformOutputProvider outputProvider) {
        if (jarInput.file.absolutePath.endsWith(".jar")) {
            // 重名名输出文件,因为可能同名,会覆盖
            def jarName = jarInput.name
            def md5Name = DigestUtils.md5Hex(jarInput.file.getAbsolutePath())
            if (jarName.endsWith(".jar")) {
                jarName = jarName.substring(0, jarName.length() - 4)
            }
            JarFile jarFile = new JarFile(jarInput.file)
            Enumeration enumeration = jarFile.entries()
            File tmpFile = new File(jarInput.file.getParent() + File.separator + "classes_temp.jar")
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(tmpFile))
            //用于保存
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement()
                String entryName = jarEntry.getName()
                ZipEntry zipEntry = new ZipEntry(entryName)
                InputStream inputStream = jarFile.getInputStream(jarEntry)
                //插桩class
                if (filterClass(entryName)) {
                    //class文件处理
                    jarOutputStream.putNextEntry(zipEntry)
                    ClassReader classReader = new ClassReader(IOUtils.toByteArray(inputStream))
                    ClassWriter classWriter = new ClassWriter(0)
                    //todo 改这里就可以了
                    def classVisitor = new PrivacyClassVisitor(classWriter)
                    classVisitor.setClassName(jarEntry.getName())
                    // 下面还可以包多层
                    classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
                    byte[] code = classWriter.toByteArray()
                    jarOutputStream.write(code)
                } else {
                    jarOutputStream.putNextEntry(zipEntry)
                    jarOutputStream.write(IOUtils.toByteArray(inputStream))
                }
                jarOutputStream.closeEntry()
            }
            //结束
            jarOutputStream.close()
            jarFile.close()
            def dest = outputProvider.getContentLocation(jarName + md5Name,
                    jarInput.contentTypes, jarInput.scopes, Format.JAR)
            FileUtils.copyFile(tmpFile, dest)
            tmpFile.delete()
        }
    }

    static boolean filterClass(String className) {
        return (className.endsWith(".class")
                && !className.startsWith("R\$")
                && "R.class" != className
                && "BuildConfig.class" != className
                // 这两个我加的，android库和直接替换代码的文件不要改
                && (!className.startsWith("android"))
                && "AsmMethods.class" != className)
    }
}
```
上面代码有两个TODO的地方，就是我们要通过ASM的ClassVisitor去修改的地方，我这是PrivacyClassVisitor，并加了个ClassName传进去，更好打印类所在位置。

需要注意的一个问题就是JAR包要特别处理，好多文章就一个FileUtils.copyDirectory，真不知道他们试了没有，搞得我的SDK一个都没处理，让我找了很久的BUG。。。

编写好PrivacyTransform记得去Plugin里面注册下:
```
package silencefly96.privacy

import org.gradle.api.Plugin
import org.gradle.api.Project

public class PrivacyPlugin implements Plugin<Project>{

    @Override
    void apply(Project project) {
        println("PrivacyPlugin")
        project.android.registerTransform(new PrivacyTransform())
    }
}
```
注册好了，要uploadArchives一下才能发布到本地maven仓库生效。

## 使用ASM
字节码和ASM前面已经分了一篇说了，这里就是来具体看看如何使用，以及一些大坑。。。

这里ASM代码是Java代码，需要再项目的main目录下面根据包名再建几个目录，比如我这是“com\silencefly96\privacy\”，里面编写PrivacyClassVisitor.java文件:
```
package com.silencefly96.privacy;


import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;


public class PrivacyClassVisitor extends ClassVisitor {


    private String className;
    public void setClassName(String className) {
        this.className = className;
    }

    public PrivacyClassVisitor(ClassVisitor classVisitor) {
        // 我这要使用ASM6不然报错，不知道为什么，太高了太低了都不行
        super(Opcodes.ASM6, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {

        //判断方法
        MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);

        // 判断方法
        if (methodVisitor != null) {
            return new MethodVisitor(Opcodes.ASM6, methodVisitor) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                    // 替换说明:
                    // 1. 路径以”/“分割，而不是包名里面的”.“
                    // 2. owner前不带”L“字符，descriptor内都要加上”L“字符
                    // 3. descriptor里面参数及返回值类型后的”;“不能省，特别是参数列表最后一个参数后的”;“
                    // 4. descriptor里面基本类型(比如V、Z)后不能添加”;“，否则匹配不上
                    // 5. 方法签名一定要写对，参数及返回值的类型，抛出的异常不算方法签名
                    // 6. 替换方法前后变量一定要对应，实例方法0位置是this，改为静态方法时，要用第一个参数去接收;
                    // 7. 替换方法前后，参数加返回值的数量要相等

                    // 替换调用 Environment.getExternalStorageDirectory() 的地方为应用程序的本地目录
                    if (opcode == Opcodes.INVOKESTATIC && owner.equals("android/os/Environment") && name.equals("getExternalStorageDirectory") && descriptor.equals("()Ljava/io/File;")) {
                        System.out.println("处理SD卡数据泄漏风险: " + className);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/silencefly96/module_base/utils/AsmMethods", "getExternalDir", "()Ljava/io/File;", false);
                    }

                    // 判断是否调用了 ContextWrapper 类的 registerReceiver 方法
                    else if (opcode == Opcodes.INVOKEVIRTUAL && name.equals("registerReceiver") && descriptor.equals("(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;")) {
                        // && owner.equals("android/content/Context")
                        System.out.println("处理动态注册广播: " + className);
                        // 调用你自定义的方法，并传递 Context 和参数
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/silencefly96/module_base/utils/AsmMethods", "registerZxyReceiver", "(Landroid/content/Context;Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;", false);
                    }

                    // SQL数据库注入漏洞: rawQuery
                    else if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("android/database/sqlite/SQLiteDatabase") && name.equals("rawQuery") && descriptor.equals("(Ljava/lang/String;[Ljava/lang/String;)Landroid/database/Cursor;")) {
                        System.out.println("处理SQL数据库注入漏洞 rawQuery: " + className);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/silencefly96/module_base/utils/AsmMethods", "rawZxyQuery", "(Landroid/database/sqlite/SQLiteDatabase;Ljava/lang/String;[Ljava/lang/String;)Landroid/database/Cursor;", false);
                    }

                    // SQL数据库注入漏洞: execSQL
                    else if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("android/database/sqlite/SQLiteDatabase") && name.equals("execSQL") && descriptor.equals("(Ljava/lang/String;)V")) {
                        System.out.println("处理SQL数据库注入漏洞 execSQL: " + className);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/silencefly96/module_base/utils/AsmMethods", "execZxySQL", "(Landroid/database/sqlite/SQLiteDatabase;Ljava/lang/String;)V", false);
                    }

                    // ZipperDown漏洞
                    else if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/util/zip/ZipEntry") && name.equals("getName") && descriptor.equals("()Ljava/lang/String;")) {
                        System.out.println("处理ZipperDown漏洞: " + className);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/silencefly96/module_base/utils/AsmMethods", "getZipEntryName", "(Ljava/util/zip/ZipEntry;)Ljava/lang/String;", false);
                    }

                    // 日志函数泄露风险: 只改方法签名为 (Ljava/lang/String;Ljava/lang/String;)I 的
                    else if (opcode == Opcodes.INVOKESTATIC && owner.equals("android/util/Log") && descriptor.equals("(Ljava/lang/String;Ljava/lang/String;)I")) {
                        System.out.println("处理日志函数泄露风险 " + name + ": " + className);
                        if (name.equals("e")) {
                            // 错误日志还是有用的
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/silencefly96/module_base/utils/AsmMethods", "optimizeLogE", "(Ljava/lang/String;Ljava/lang/String;)I", false);
                        }else {
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/silencefly96/module_base/utils/AsmMethods", "optimizeLog", "(Ljava/lang/String;Ljava/lang/String;)I", false);
                        }
                    }

                    // Webview组件跨域访问风险
                    else if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("android/webkit/WebSettings") && name.equals("setJavaScriptEnabled") && descriptor.equals("(Z)V")) {
                        System.out.println("处理Webview组件跨域访问风险: " + className);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/silencefly96/module_base/utils/AsmMethods", "setZxyJsEnabled", "(Landroid/webkit/WebSettings;Z)V", false);
                    }

                    else {
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                }
            };
        }

        return methodVisitor;
    }
}
```
对应替换的AsmMethods: 
```
object AsmMethods {

    // ASM替换代码勿动: 替换获取外部文件
    fun getExternalDir(): File {
        var result = File("")
        // ...
        return result
    }

    // ASM替换代码勿动: 替换直接动态注册广播
    fun registerMyReceiver(
        context: Context,
        receiver: BroadcastReceiver?,
        filter: IntentFilter?
    ): Intent? {
        var result: Intent? = null
        // ...
        return result
    }

    // ASM替换代码勿动: 处理SQL数据库注入漏洞: rawQuery
    fun rawMyQuery(
        database: SQLiteDatabase,
        sql: String?,
        selectionArgs: Array<String?>?
    ): Cursor? {
        var result: Cursor? = null
        // ...
        return result
    }

    // ASM替换代码勿动: 处理SQL数据库注入漏洞: rawQuery
    fun execMySQL(database: SQLiteDatabase, sql: String?) {
        // ...
    }

    // ASM替换代码勿动: ZipperDown漏洞
    fun getZipEntryName(entry: ZipEntry): String {
        var result = ""
        // ...
        return result
    }

    // ASM替换代码勿动: 日志函数泄露风险
    fun optimizeLog(tag: String?, msg: String?): Int {
        var result = 0
        if (BuildConfig.DEBUG) {
            // 要防止这里被替代，引发StackOverflow问题
            result = Log.d(tag, msg!!)
        }
        return result
    }

    // ASM替换代码勿动: 日志函数泄露风险
    fun optimizeLogE(tag: String?, msg: String?): Int {
        var result = 0
        if (BuildConfig.DEBUG) {
            // 要防止这里被替代，引发StackOverflow问题
            result = Log.e(tag, msg!!)
        }
        return result
    }

    // ASM替换代码勿动: WebView组件跨域访问风险
    fun setMyJsEnabled(settings: WebSettings, flag: Boolean) {
        // ...
    }
}
```
不是很复杂，因为我这就改了visitMethod这一个方法，下面主要想说的就是我在这踩了好多坑，总结了下面一些经验:

1. 路径以”/“分割，而不是包名里面的”.“
2. owner前不带”L“字符，descriptor内都要加上”L“字符
3. descriptor里面参数及返回值类型后的”;“不能省，特别是参数列表最后一个参数后的”;“
4. descriptor里面基本类型(比如V、Z)后不能添加”;“，否则匹配不上
5. 方法签名一定要写对，参数及返回值的类型，抛出的异常不算方法签名
6. 替换方法前后变量一定要对应，实例方法0位置是this，改为静态方法时，要用第一个参数去接收;
7. 替换方法前后，参数加返回值的数量要相等

这些坑会导致很多奇奇怪怪的问题，我也总结了一下:
1. D8编译问题
2. Different stack heights at jump target: 0 != 1
3. 找不到fileProvider、Application
4. multidex错误

如果你也出现了这些问题，还请排查下上面替换的的方法签名有没有问题，我一开始都不懂，改的头都麻了。

## 使用
把上面插件、Transform、ASM代码都弄好后，uploadArchives一下，到app里面引入repo以及插件: 
```
// project的build.gradle
buildscript {
    ext.kotlin_version = "1.4.21"
    repositories {
        // 依赖本地仓库
        maven{ url './privacy_repo' }
    }
    dependencies {
        // 从本地仓库中加载自定义插件 group + artifactId + version，不要多手打空格！
        classpath 'silencefly96.privacy:privacy-plugin:1.0.0'
    }
}

// module的build.gradle
plugins {
    id 'silencefly96.privacy'
}
```
app模块的MainActivity里面随便写个有调用隐私调用的方法:
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
rebuild一下，看下输出台内容，我这整改了很多，不过终点看下那个“处理动态注册广播”，这个我们添加的测试代码生效了:
![f.png](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/27ac6d999d2440e69aa3758b1a583866~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1846&h=599&s=171937&e=png&b=2c2c2c)

嘿嘿，第三方SDK安全整改搞定！Demo可以看下我练手的仓库: [Fundark]()!

## 总结
上篇文章的插件和这篇文章的整改，两篇文章就说完了，但是我去学习和使用的过程真就挺漫长啊，路漫漫其修远兮，吾将上下而求索！