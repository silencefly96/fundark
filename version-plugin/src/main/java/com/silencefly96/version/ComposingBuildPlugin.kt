package com.silencefly96.version

import org.gradle.api.Plugin
import org.gradle.api.Project

class ComposingBuildPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        println("ComposingBuildPlugin")
    }
}
