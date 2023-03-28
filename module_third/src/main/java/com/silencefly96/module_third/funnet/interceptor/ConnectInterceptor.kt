package com.silencefly96.module_third.funnet.interceptor

import com.silencefly96.module_third.funnet.FunNetClient
import com.silencefly96.module_third.funnet.Response
import com.silencefly96.module_third.funnet.connection.HttpCodec
import com.silencefly96.module_third.funnet.connection.RealConnection

class ConnectInterceptor(val client: FunNetClient): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // 这里只是创建了httpCodec,和connection一起传递到了下一个责任链
        val realChain = chain as RealInterceptorChain
        val request = realChain.request
        val streamAllocation = realChain.streamAllocation

        // We need the network to satisfy this request. Possibly for validating a conditional GET.
//        val doExtensiveHealthChecks = request.method() != "GET"
//        val httpCodec = streamAllocation.newStream(client, chain, doExtensiveHealthChecks)
//        val connection = streamAllocation.connection()

        return realChain.proceed(request, streamAllocation, HttpCodec(), RealConnection())
    }
}