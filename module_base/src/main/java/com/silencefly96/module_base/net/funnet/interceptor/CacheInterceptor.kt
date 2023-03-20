package com.silencefly96.module_base.net.funnet.interceptor

import com.silencefly96.module_base.net.funnet.Cache
import com.silencefly96.module_base.net.funnet.Response

class CacheInterceptor(val cache: Cache?): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        TODO("Not yet implemented")
    }
}