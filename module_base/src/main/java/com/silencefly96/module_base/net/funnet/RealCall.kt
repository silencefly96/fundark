package com.silencefly96.module_base.net.funnet

import com.silencefly96.module_base.net.funnet.interceptor.RetryAndFollowUpInterceptor

class RealCall(
    val client: FunNetClient,
    val originalRequest: Request
): Call {

    companion object {
        fun newRealCall(client: FunNetClient, request: Request): Call {
            val realCall = RealCall(client, request)
            realCall.retryInterceptor = RetryAndFollowUpInterceptor()
            realCall.timeout = AsyncTimeout()
            return realCall
        }
    }

    // 事件监听器
    private val eventListener: EventListener? = null
    // 重试拦截器
    private lateinit var retryInterceptor: RetryAndFollowUpInterceptor
    // 超时计时器
    private lateinit var timeout: AsyncTimeout

    override fun enqueue() {

    }

    override fun execute() {

    }

    override fun cancel() {

    }
}