package com.silencefly96.module_views.widget.markdown.client

import com.silencefly96.module_views.widget.markdown.client.chain.Interceptor
import com.silencefly96.module_views.widget.markdown.client.chain.RealInterceptorChain
import com.silencefly96.module_views.widget.markdown.client.chain.Request
import com.silencefly96.module_views.widget.markdown.client.chain.Response
import com.silencefly96.module_views.widget.markdown.client.chain.interceptors.AdapterInterceptor
import com.silencefly96.module_views.widget.markdown.client.chain.interceptors.ObtainInterceptor
import com.silencefly96.module_views.widget.markdown.client.chain.interceptors.ParseInterceptor
import com.silencefly96.module_views.widget.markdown.client.chain.interceptors.RetryInterceptor
import java.lang.IllegalArgumentException

class MarkEggClient {

    var originalRequest: Request? = null

    /**
     * 自定义拦截器
     */
    val customInterceptors: MutableList<Interceptor> = ArrayList()

    // 执行责任链获得结果
    fun getResponseWithInterceptorChain(): Response {
        if (originalRequest == null) {
            throw IllegalArgumentException("request can not be null")
        }

        val interceptors: MutableList<Interceptor> = ArrayList()

        // 默认拦截器
        interceptors.add(RetryInterceptor())
        interceptors.add(ObtainInterceptor())
        interceptors.add(ParseInterceptor())
        interceptors.add((AdapterInterceptor()))

        // 自定义拦截器
        interceptors.addAll(customInterceptors)

        val chain: Interceptor.Chain = RealInterceptorChain(
            interceptors, null, null,
            0, originalRequest!!)

        return chain.proceed(originalRequest!!)
    }
}