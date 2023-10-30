package com.silencefly96.plugins.privacy

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class PrivacyClassVisitor(nextVisitor: ClassVisitor, private val className: String)
    : ClassVisitor(Opcodes.ASM6, nextVisitor) {

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {

        val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)

        val newMethodVisitor = object: MethodVisitor(Opcodes.ASM6, methodVisitor) {

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