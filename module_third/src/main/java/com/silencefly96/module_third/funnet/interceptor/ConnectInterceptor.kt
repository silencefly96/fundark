package com.silencefly96.module_third.funnet.interceptor

import com.silencefly96.module_third.funnet.FunNetClient
import com.silencefly96.module_third.funnet.Response

class ConnectInterceptor(val client: FunNetClient): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        TODO("Not yet implemented")
    }
}