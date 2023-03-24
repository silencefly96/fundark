package com.silencefly96.module_third.funnet.interceptor

import com.silencefly96.module_third.funnet.Call
import com.silencefly96.module_third.funnet.Request
import com.silencefly96.module_third.funnet.Response
import com.silencefly96.module_third.funnet.connection.HttpCodec
import com.silencefly96.module_third.funnet.connection.RealConnection
import com.silencefly96.module_third.funnet.connection.StreamAllocation
import java.io.IOException
import java.lang.AssertionError
import kotlin.jvm.Throws

class RealInterceptorChain(
    val interceptors: List<Interceptor>,
    val streamAllocation: StreamAllocation?,
    val httpCodec: HttpCodec?,
    val connection: RealConnection?,
    val index: Int,
    val request: Request,
    val call: Call
): Interceptor.Chain {

    @Throws(IOException::class)
    override fun proceed(request: Request): Response {
        return proceed(request, null, null, null)
    }

    @Throws(IOException::class)
    fun proceed(request: Request, streamAllocation: StreamAllocation?, httpCodec: HttpCodec?,
                connection: RealConnection?): Response {
        // AssertionError放这里好吗？为什么不用assert
        if (index >= interceptors.size) throw AssertionError()

        // 生成下一个责任链
        val next = RealInterceptorChain(interceptors, null, null,
            null, index + 1, request, call)
        // 处理当前拦截器代码
        val interceptor: Interceptor = interceptors[index]
        val response = interceptor.intercept(next)

        checkNotNull(response.body) { "interceptor $interceptor returned a response with no body" }
        return response
    }
}