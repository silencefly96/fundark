package com.silencefly96.plugins.privacy;

import com.android.build.api.instrumentation.*
import org.objectweb.asm.ClassVisitor

abstract class PrivacyTransform: AsmClassVisitorFactory<InstrumentationParameters.None> {

    override fun createClassVisitor(
        classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor {
        return PrivacyClassVisitor(nextClassVisitor,classContext.currentClassData.className)
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        return true
    }
}