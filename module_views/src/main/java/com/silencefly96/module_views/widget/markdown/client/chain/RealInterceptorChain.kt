package com.silencefly96.module_views.widget.markdown.client.chain

import com.silencefly96.module_views.widget.markdown.client.adapter.AdapterPack
import com.silencefly96.module_views.widget.markdown.client.parser.MarkdownParser
import java.io.IOException

class RealInterceptorChain(
    val interceptors: List<Interceptor>,
    val markdownParser: MarkdownParser?,
    val adapterPack: AdapterPack?,
    val index: Int,
    val request: Request
): Interceptor.Chain {

    @Throws(IOException::class)
    override fun proceed(request: Request): Response {
        return proceed(request, null, null)
    }

    @Throws(IOException::class)
    fun proceed(
        request: Request,
        markdownParser: MarkdownParser?,
        adapterPack: AdapterPack?
    ): Response {
        if (index >= interceptors.size) throw AssertionError()

        // 生成下一个责任链
        val next = RealInterceptorChain(
            interceptors, null, null,
            index + 1, request
        )

        // 处理当前拦截器代码
        val interceptor: Interceptor = interceptors[index]

        // 获得结果
        return interceptor.intercept(next)
    }
}