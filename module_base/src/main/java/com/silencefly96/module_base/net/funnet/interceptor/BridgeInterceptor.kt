package com.silencefly96.module_base.net.funnet.interceptor

import com.silencefly96.module_base.net.funnet.CookieJar
import com.silencefly96.module_base.net.funnet.Response

class BridgeInterceptor(val cookieJar: CookieJar): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        TODO("Not yet implemented")
    }
}