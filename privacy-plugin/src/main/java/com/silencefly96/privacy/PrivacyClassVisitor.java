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

                    // X5Webview组件跨域访问风险
                    else if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("com/tencent/smtt/sdk/WebSettings") && name.equals("setJavaScriptEnabled") && descriptor.equals("(Z)V")) {
                        System.out.println("处理X5Webview组件跨域访问风险: " + className);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/silencefly96/module_base/utils/AsmMethods", "setZxyX5JsEnabled", "(Lcom/tencent/smtt/sdk/WebSettings;Z)V", false);
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
