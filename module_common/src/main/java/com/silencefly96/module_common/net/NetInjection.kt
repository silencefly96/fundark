package com.silencefly96.module_common.net

import com.silencefly96.module_common.net.httpUrlConnection.HttpUrlNetModule

object NetInjection {
    fun provideNetModule() = HttpUrlNetModule()
}