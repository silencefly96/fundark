package com.silencefly96.version

import org.gradle.api.Plugin
import org.gradle.api.Project

class VersionTestPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        println("VersionTestPlugin")
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