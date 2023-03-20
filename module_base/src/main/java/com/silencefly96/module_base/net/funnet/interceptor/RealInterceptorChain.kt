package com.silencefly96.module_base.net.funnet.interceptor

import com.silencefly96.module_base.net.funnet.Call
import com.silencefly96.module_base.net.funnet.Request
import com.silencefly96.module_base.net.funnet.Response
import java.util.concurrent.TimeUnit

class RealInterceptorChain: Interceptor.Chain {
    override fun request(): Request {
        TODO("Not yet implemented")
    }

    override fun proceed(request: Request): Response {
        TODO("Not yet implemented")
    }

    override fun call(): Call {
        TODO("Not yet implemented")
    }

    override fun connectTimeoutMillis(): Int {
        TODO("Not yet implemented")
    }

    override fun withConnectTimeout(timeout: Int, unit: TimeUnit?): Interceptor.Chain? {
        TODO("Not yet implemented")
    }

    override fun readTimeoutMillis(): Int {
        TODO("Not yet implemented")
    }

    override fun withReadTimeout(timeout: Int, unit: TimeUnit?): Interceptor.Chain? {
        TODO("Not yet implemented")
    }

    override fun writeTimeoutMillis(): Int {
        TODO("Not yet implemented")
    }

    override fun withWriteTimeout(timeout: Int, unit: TimeUnit?): Interceptor.Chain? {
        TODO("Not yet implemented")
    }
}