package com.silencefly96.module_base.net.funnet.interceptor

import com.silencefly96.module_base.net.funnet.FunNetClient
import com.silencefly96.module_base.net.funnet.Response

class ConnectInterceptor(val client: FunNetClient): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        TODO("Not yet implemented")
    }
}