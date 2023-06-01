package com.silencefly96.module_third.okhttp3.interceptor

import com.silencefly96.module_third.okhttp3.CookieJar
import com.silencefly96.module_third.okhttp3.Request
import com.silencefly96.module_third.okhttp3.Response

/**
 * 桥适配器
 *
 * 主要功能：
 * Bridges from application code to network code
 * 1，处理请求头
 * 2，对cookies进行操作
 * 3，解压之类
 */
class BridgeInterceptor(val cookieJar: CookieJar): Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val realChain = chain as RealInterceptorChain
        val userRequest: Request = realChain.request

        // 第一步，添加各种请求头
        // 根据已有请求头添加header
//        val body = userRequest.body()
//        if (body != null) {
//            val contentType = body.contentType()
//            if (contentType != null) {
//                requestBuilder.header("Content-Type", contentType.toString())
//            }
//            val contentLength = body.contentLength()
//            if (contentLength != -1L) {
//                requestBuilder.header("Content-Length", java.lang.Long.toString(contentLength))
//                requestBuilder.removeHeader("Transfer-Encoding")
//            } else {
//                requestBuilder.header("Transfer-Encoding", "chunked")
//                requestBuilder.removeHeader("Content-Length")
//            }
//        }

        // 添加其他header
//        if (userRequest.header("Host") == null) {
//            requestBuilder.header("Host", Util.hostHeader(userRequest.url(), false))
//        }
//
//        if (userRequest.header("Connection") == null) {
//            requestBuilder.header("Connection", "Keep-Alive")
//        }

        // If we add an "Accept-Encoding: gzip" header field we're responsible for also decompressing
        // the transfer stream.
//        var transparentGzip = false
//        if (userRequest.header("Accept-Encoding") == null && userRequest.header("Range") == null) {
//            transparentGzip = true
//            requestBuilder.header("Accept-Encoding", "gzip")
//        }

        // 添加cookies
//        val cookies: List<Cookie> = cookieJar.loadForRequest(userRequest.url())
//        if (!cookies.isEmpty()) {
//            requestBuilder.header("Cookie", cookieHeader(cookies))
//        }

//        if (userRequest.header("User-Agent") == null) {
//            requestBuilder.header("User-Agent", Version.userAgent())
//        }

        // 第二步，通过责任链发起请求，进入下一个责任链，request的header填充完毕
//        val networkResponse: Response = chain.proceed(requestBuilder.build())

        // 第三步，保存cookies
//        HttpHeaders.receiveHeaders(cookieJar, userRequest.url(), networkResponse.headers())

        // 第四步，根据回复进行处理(解压)，得到最终的response
//        val responseBuilder = networkResponse.newBuilder()
//            .request(userRequest)
//
//        if (transparentGzip
//            && "gzip".equals(networkResponse.header("Content-Encoding"), ignoreCase = true)
//            && HttpHeaders.hasBody(networkResponse)
//        ) {
//            val responseBody = GzipSource(networkResponse.body()!!.source())
//            val strippedHeaders = networkResponse.headers().newBuilder()
//                .removeAll("Content-Encoding")
//                .removeAll("Content-Length")
//                .build()
//            responseBuilder.headers(strippedHeaders)
//            val contentType = networkResponse.header("Content-Type")
//            responseBuilder.body(RealResponseBody(contentType, -1L, Okio.buffer(responseBody)))
//        }

//        return responseBuilder.build()
        return Response()
    }
}