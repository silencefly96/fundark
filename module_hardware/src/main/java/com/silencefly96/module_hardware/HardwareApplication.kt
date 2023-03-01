package com.silencefly96.module_hardware

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context

class HardwareApplication : Application() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
    }
}