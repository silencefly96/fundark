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
            // 控制是否需要扫描依赖库代码
            variant.instrumentation.transformClassesWith(
                PrivacyTransform::class.java,
                InstrumentationScope.PROJECT
            ) {}

            // 可设置不同的栈帧计算模式
            variant.instrumentation.setAsmFramesComputationMode(
                FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
            )
        }
    }
}