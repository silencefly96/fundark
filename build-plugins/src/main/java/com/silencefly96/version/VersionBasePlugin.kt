package com.silencefly96.version

import org.gradle.api.Plugin
import org.gradle.api.Project

class VersionBasePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        println("VersionBasePlugin")
        target.afterEvaluate {
            with(target.dependencies) {
                // kotlin
                add("api", Libs.kotlin)
                // base相关基类需要使用
                add("api", Libs.core_ktx)
                // RecyclerView适配器等
                add("api", Libs.appcompat)
                // Constraintlayout
                add("api", Libs.material)
                // Constraintlayout
                add("api", Libs.constraintlayout)

                // 协程
                add("api", Libs.kotlinx_coroutines_core)
                add("api", Libs.kotlinx_coroutines_android)

                //Jetpack lifecycle
                add("api", Libs.lifecycle_extensions)
                add("api", Libs.lifecycle_viewmodel_ktx)
                add("api", Libs.lifecycle_livedata_ktx)
            }
        }

    }
}
