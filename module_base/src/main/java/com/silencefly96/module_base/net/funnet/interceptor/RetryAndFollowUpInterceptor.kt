package com.silencefly96.module_base.net.funnet.interceptor

import com.silencefly96.module_base.net.funnet.Response

class RetryAndFollowUpInterceptor: Interceptor {

    var isCanceled = false

    override fun intercept(chain: Interceptor.Chain): Response {
        TODO("Not yet implemented")
    }

    fun cancel() {

    }

}