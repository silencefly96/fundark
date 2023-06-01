package com.silencefly96.module_third.okhttp3.interceptor

import com.silencefly96.module_third.okhttp3.Request
import com.silencefly96.module_third.okhttp3.Response
import java.io.IOException

interface Interceptor {

    @Throws(IOException::class)
    fun intercept(chain: Chain): Response

    interface Chain {
        @Throws(IOException::class)
        fun proceed(request: Request): Response
    }
}