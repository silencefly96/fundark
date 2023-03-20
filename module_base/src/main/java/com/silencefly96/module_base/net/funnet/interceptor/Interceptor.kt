package com.silencefly96.module_base.net.funnet.interceptor

import com.silencefly96.module_base.net.funnet.Call
import com.silencefly96.module_base.net.funnet.Request
import com.silencefly96.module_base.net.funnet.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

interface Interceptor {

    @Throws(IOException::class)
    fun intercept(chain: Chain): Response

    interface Chain {
        fun request(): Request

        @Throws(IOException::class)
        fun proceed(request: Request): Response


        fun call(): Call
        fun connectTimeoutMillis(): Int
        fun withConnectTimeout(timeout: Int, unit: TimeUnit?): Chain?
        fun readTimeoutMillis(): Int
        fun withReadTimeout(timeout: Int, unit: TimeUnit?): Chain?
        fun writeTimeoutMillis(): Int
        fun withWriteTimeout(timeout: Int, unit: TimeUnit?): Chain?
    }
}