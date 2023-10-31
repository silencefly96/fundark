package com.silencefly96.plugins.privacy;

import com.android.build.api.instrumentation.*
import org.objectweb.asm.ClassVisitor

abstract class PrivacyTransform: AsmClassVisitorFactory<InstrumentationParameters.None> {

    override fun createClassVisitor(
        classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor {
        return PrivacyClassVisitor(nextClassVisitor, classContext.currentClassData.className)
    }

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