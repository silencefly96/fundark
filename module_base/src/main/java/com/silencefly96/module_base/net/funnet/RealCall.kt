package com.silencefly96.module_base.net.funnet

import com.silencefly96.module_base.net.funnet.interceptor.*
import java.io.IOException
import java.util.ArrayList
import java.util.concurrent.ExecutorService
import kotlin.jvm.Throws

class RealCall(
    val client: FunNetClient,
    val originalRequest: Request
): Call {

    companion object {
        fun newRealCall(client: FunNetClient, request: Request): Call {
            val realCall = RealCall(client, request)
            realCall.retryInterceptor = RetryAndFollowUpInterceptor()
            return realCall
        }
    }

    // 是否执行
    private var executed = false

    // 重试拦截器
    private lateinit var retryInterceptor: RetryAndFollowUpInterceptor

    override fun enqueue(callback: Callback) {
        synchronized(this) {
            check(!executed) { "Already Executed" }
            executed = true
        }
        client.dispatcher.enqueue(AsyncCall(callback))
    }

    @Throws(IOException::class)
    override fun execute(): Response {
        synchronized(this) {
            check(!executed) { "Already Executed" }
            executed = true
        }

        try {
            // 只是向队列添加call
            client.dispatcher.executed(this)
            // 由拦截器责任链获得结果
            val result = getResponseWithInterceptorChain() ?: throw IOException("Canceled")
            client.dispatcher.finished(this)
            return result
        } catch (e: IOException) {
            e.printStackTrace()
            // okhttp在这验证了是否超时
            throw e
        }
    }

    override fun cancel() {

    }

    /**
     *
     */
    inner class AsyncCall(val responseCallback: Callback): Runnable{
        override fun run() { execute() }

        fun executeOn(executorService: ExecutorService) {

        }

        fun execute() {

        }
    }

    /**
     * 拦截器责任链
     */
    @Throws(IOException::class)
    fun getResponseWithInterceptorChain(): Response{
        // Build a full stack of interceptors.
        val interceptors: MutableList<Interceptor> = ArrayList()
        interceptors.addAll(client.interceptors)
        interceptors.add(retryInterceptor)
        interceptors.add(BridgeInterceptor(client.cookieJar))
        interceptors.add(CacheInterceptor(client.cache))
        interceptors.add(ConnectInterceptor(client))

        val chain: Interceptor.Chain = RealInterceptorChain()

        return chain.proceed(originalRequest)
    }
}

interface Call {
    /**
     * 异步执行
     */
    fun enqueue(callback: Callback)

    /**
     * 同步执行
     */
    @Throws(IOException::class)
    fun execute(): Response

    /**
     * 取消
     */
    fun cancel()
}