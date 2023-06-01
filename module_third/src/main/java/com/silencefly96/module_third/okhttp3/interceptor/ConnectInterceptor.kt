package com.silencefly96.module_third.okhttp3.interceptor

import com.silencefly96.module_third.okhttp3.FunNetClient
import com.silencefly96.module_third.okhttp3.Response
import com.silencefly96.module_third.okhttp3.connection.Http1Codec1
import com.silencefly96.module_third.okhttp3.connection.RealConnection1

class ConnectInterceptor(val client: FunNetClient): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // 这里只是创建了httpCodec,和connection一起传递到了下一个责任链
        val realChain = chain as RealInterceptorChain
        val request = realChain.request
        val streamAllocation = realChain.streamAllocation1

        // We need the network to satisfy this request. Possibly for validating a conditional GET.
//        val doExtensiveHealthChecks = request.method() != "GET"
//        val httpCodec = streamAllocation.newStream(client, chain, doExtensiveHealthChecks)
//        val connection = streamAllocation.connection()

        return realChain.proceed(request, streamAllocation, Http1Codec1(null, null, null, null),
            RealConnection1(null, null)
        )
    }
}