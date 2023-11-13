package com.silencefly96.module_views.widget.markdown.client.chain

import java.io.IOException

interface Interceptor {

    @Throws(IOException::class)
    fun intercept(chain: Chain): Response

    interface Chain {
        @Throws(IOException::class)
        fun proceed(request: Request): Response
    }
}