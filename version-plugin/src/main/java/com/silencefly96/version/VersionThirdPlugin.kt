package com.silencefly96.version

import org.gradle.api.Plugin
import org.gradle.api.Project

class VersionThirdPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        println("ThirdVersionPlugin")
        target.afterEvaluate {
            with(target.dependencies) {
                // multidex
                add("implementation", Libs.multidex)

                // Jetpack Room数据库框架, kapt不能传递，需要在使用的地方引入
                add("implementation", Libs.room_runtime)
                add("kapt", Libs.room_compiler)

                // Retrofit网络通信框架
                add("implementation", Libs.retrofit)
                add("api", Libs.converter_gson)

                // Glide
                add("implementation", Libs.glide)
                add("implementation", Libs.glide_compiler)

                // navigation
                add("implementation", Libs.navigation_fragment)
                add("implementation", Libs.navigation_ui)

                // volley
                add("implementation", Libs.volley)
            }
        }

    }
}