package com.silencefly96.module_third.funnet

import com.silencefly96.module_third.funnet.interceptor.*
import java.io.IOException
import java.io.InterruptedIOException
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

class RealCall(
    val client: FunNetClient,
    val originalRequest: Request
): Call {

    companion object {
        fun newRealCall(client: FunNetClient, request: Request): Call {
            val realCall = RealCall(client, request)
            realCall.retryAndFollowUpInterceptor = RetryAndFollowUpInterceptor(client)
            return realCall
        }
    }

    // 是否执行
    private var executed = false

    // 重试拦截器
    private lateinit var retryAndFollowUpInterceptor: RetryAndFollowUpInterceptor

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
            val result = getResponseWithInterceptorChain()
            client.dispatcher.finished(this)
            return result
        } catch (e: IOException) {
            e.printStackTrace()
            // okhttp在这验证了是否超时
            throw e
        }
    }

    override fun cancel() {
        retryAndFollowUpInterceptor.cancel()
    }

    /**
     * 异步执行的 runnable
     */
    inner class AsyncCall(val responseCallback: Callback): Runnable{
        override fun run() { execute() }

        fun executeOn(executorService: ExecutorService) {
            var success = false
            try {
                // 使用线程池执行，通过run进入到execute()方法
                executorService.execute(this)
                success = true
            } catch (e: RejectedExecutionException) {
                val ioException = InterruptedIOException("executor rejected")
                ioException.initCause(e)
                responseCallback.onFailure(this@RealCall, ioException)
            } finally {
                if (!success) {
                    client.dispatcher.finished(this) // This call is no longer running!
                }
            }
        }

        fun execute() {
            // 验证下是否被取消
            var signalledCallback = false
            try {
                val response: Response = getResponseWithInterceptorChain()
                if (retryAndFollowUpInterceptor.isCanceled) {
                    signalledCallback = true
                    responseCallback.onFailure(this@RealCall, IOException("Canceled"))
                } else {
                    signalledCallback = true
                    responseCallback.onResponse(this@RealCall, response)
                }
            } catch (e: IOException) {
                // Do not signal the callback twice!
                if (!signalledCallback) {
                    responseCallback.onFailure(this@RealCall, e)
                }
            } finally {
                client.dispatcher.finished(this)
            }
        }
    }

    /**
     * 拦截器责任链
     */
    @Throws(IOException::class)
    fun getResponseWithInterceptorChain(): Response{
        // Build a full stack of interceptors.
        val interceptors: MutableList<Interceptor> = ArrayList()
        // 自定义的拦截器
        interceptors.addAll(client.interceptors)
        // 重试拦截器
        interceptors.add(retryAndFollowUpInterceptor)
        interceptors.add(BridgeInterceptor(client.cookieJar))
        interceptors.add(CacheInterceptor(client.cache))
        interceptors.add(ConnectInterceptor(client))

        // 实际上是通过RealInterceptorChain处理各个拦截器的，index为interceptors内的位置
        val chain = RealInterceptorChain(interceptors, null, null,
            null,0, originalRequest, this)
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