package com.silencefly96.module_third.funnet.interceptor

import com.silencefly96.module_third.funnet.CookieJar
import com.silencefly96.module_third.funnet.Response

class BridgeInterceptor(val cookieJar: CookieJar): Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        TODO("Not yet implemented")
    }
}