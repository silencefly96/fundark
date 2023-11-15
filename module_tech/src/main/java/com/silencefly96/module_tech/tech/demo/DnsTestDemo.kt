@file:Suppress("unused")

package com.silencefly96.module_tech.tech.demo

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.alibaba.sdk.android.httpdns.HttpDns
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_tech.databinding.FragmentDnsTestBinding
import com.silencefly96.module_tech.tech.dns.DnsInterceptor
import okhttp3.OkHttpClient
import java.net.URL

class DnsTestDemo: BaseFragment() {

    private var _binding: FragmentDnsTestBinding? = null
    private val binding get() = _binding!!

    private lateinit var client: OkHttpClient

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentDnsTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun doBusiness(context: Context?) {

        val httpdns = HttpDns.getService(getContext(), "128387")
        httpdns.setPreResolveHosts(ArrayList(listOf("www.baidu.com")))

        val originalUrl = "http://www.baidu.com"
        val url = URL(originalUrl)
        val ip = httpdns.getIpByHostAsync(url.host)
        Log.d("TAG", "httpdns get init: ip = $ip")

        // 创建okhttp
        client = OkHttpClient.Builder().dns(DnsInterceptor.MyDns()).build()
        DnsInterceptor.client = client

        // 配置webView
        val webSettings = binding.webView.settings
        webSettings.javaScriptEnabled = true //启用js,不然空白
        webSettings.domStorageEnabled = true //getItem报错解决
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                try {
                    // 通过okhttp拦截请求
                    val response = DnsInterceptor.shouldInterceptRequest(view, request)
                    if (response != null) {
                        return response
                    }
                }catch (e: Exception) {
                    // 可能有异常，发生异常就不拦截: UnknownHostException(MyDns)
                    e.printStackTrace()
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        // 点击事件
        binding.button.setOnClickListener {
            val ipClick = httpdns.getIpByHostAsync(url.host)
            val ipv6 = httpdns.getIPv6sByHostAsync(url.host).let {
                if (it.isNotEmpty()) return@let it[0]
                else return@let "not get"
            }
            Log.d("TAG", "httpdns get: ip = $ipClick, ipv6 = $ipv6")
            // 加载页面
            binding.webView.loadUrl(binding.ip.text.toString())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}