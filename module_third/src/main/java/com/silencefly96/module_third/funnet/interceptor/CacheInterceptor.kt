package com.silencefly96.module_third.funnet.interceptor

import com.silencefly96.module_third.funnet.Cache
import com.silencefly96.module_third.funnet.Response

class CacheInterceptor(val cache: Cache?): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        TODO("Not yet implemented")
    }
}