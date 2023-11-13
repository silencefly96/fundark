@file:Suppress("unused", "UNUSED_PARAMETER")

package com.silencefly96.fundark

import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import okhttp3.Dns
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.Arrays

object DnsInterceptor {

    /**
     * 设置okhttpClient
     */
    lateinit var client: OkHttpClient

    /**
     * 拦截webView请求
     */
    fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest
    ): WebResourceResponse? {
        // WebResourceRequest Android6.0以上才支持header，不支持body所以只能拦截GET方法
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            && request.method.lowercase() == "get"
            && (request.url.scheme?.lowercase() == "http"
                    || request.url.scheme?.lowercase() == "https")) {

            //  获取头部
            val headersBuilder = Headers.Builder()
            request.requestHeaders.entries.forEach {
                headersBuilder.add(it.key, it.value)
            }
            val headers = headersBuilder.build()

            // 生成okhttp请求
            val newRequest =  Request.Builder()
                .url(request.url.toString())
                .headers(headers)
                .build()

            // 同步请求
            val response = client.newCall(newRequest).execute()

            // 对于无mime类型的请求不拦截
            val contentType = response.body()?.contentType()
            if (TextUtils.isEmpty(contentType.toString())) {
                return null
            }

            // 获取响应头
            val responseHeaders: MutableMap<String, String> = HashMap()
            val length = response.headers().size()
            for (i in 0 until length) {
                val name = response.headers().name(i)
                val value =  response.headers().get(name)
                if (null != value) {
                    responseHeaders[name] = value
                }
            }

            // 创建新的response
            return WebResourceResponse(
                "${contentType!!.type()}/${contentType.subtype()}",
                contentType.charset(Charset.defaultCharset())?.name(),
                response.code(),
                "OK",
                responseHeaders,
                response.body()?.byteStream()
            )
        } else {
            return null
        }
    }

    /**
     * 优先使用ipv4
     */
    class MyDns : Dns {
        @Throws(UnknownHostException::class)
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                val inetAddressList: MutableList<InetAddress> = ArrayList()
                val inetAddresses = InetAddress.getAllByName(hostname)
                Log.d("TAG", "lookup before: ${Arrays.toString(inetAddresses)}")
                for (inetAddress in inetAddresses) {
                    if (inetAddress is Inet4Address) {
                        inetAddressList.add(0, inetAddress)
                    } else {
                        inetAddressList.add(inetAddress)
                    }
                }
                Log.d("TAG", "lookup after: $inetAddressList")
                inetAddressList
            } catch (var4: NullPointerException) {
                val unknownHostException = UnknownHostException("Broken system behavior")
                unknownHostException.initCause(var4)
                throw unknownHostException
            }
        }
    }
}