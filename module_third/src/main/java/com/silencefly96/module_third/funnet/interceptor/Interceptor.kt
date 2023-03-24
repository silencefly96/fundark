package com.silencefly96.module_third.funnet.interceptor

import com.silencefly96.module_third.funnet.Request
import com.silencefly96.module_third.funnet.Response
import java.io.IOException

interface Interceptor {

    @Throws(IOException::class)
    fun intercept(chain: Chain): Response

    interface Chain {
        @Throws(IOException::class)
        fun proceed(request: Request): Response
    }
}