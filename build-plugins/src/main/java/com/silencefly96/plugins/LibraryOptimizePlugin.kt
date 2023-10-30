package com.silencefly96.plugins

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class LibraryOptimizePlugin : Plugin<Project> {

    @Suppress("UnstableApiUsage")
    override fun apply(target: Project) {
        // 配置三个基础插件
        with(target.plugins) {
            apply("com.android.library")
            apply("kotlin-android")
            apply("kotlin-kapt")
            apply("kotlin-parcelize")
        }

        // 配置android闭包
        target.extensions.configure<LibraryExtension> {

            // compileSdk需要自己设置
            // compileSdk = libs.versions.compileSdkVersion.get().toInt()

            defaultConfig {
                // 这些也都要自己设置
//                    applicationId = "com.silencefly96.fundark"
//                    minSdk  = libs.versions.minSdkVersion.get().toInt()
//                    targetSdk  = libs.versions.targetSdkVersion.get().toInt()
//                    versionCode = libs.versions.versionCode.get().toInt()
//                    versionName = libs.versions.versionName.get()

                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            buildTypes {
                release {
                    isMinifyEnabled = false
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                }
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }

        // 配置dependencies闭包
        with(target.dependencies) {}
    }

}