package com.silencefly96.module_base.net.httpUrlConnection

import com.silencefly96.module_base.net.httpUrlConnection.HttpUrlNetModule

object NetInjection {
    fun provideNetModule() = HttpUrlNetModule()
}