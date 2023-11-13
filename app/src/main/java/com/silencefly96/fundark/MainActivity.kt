package com.silencefly96.fundark

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.silencefly96.fundark.databinding.ActivityMainBinding
import com.silencefly96.module_base.base.BaseActivity
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Arrays

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var client: OkHttpClient

    override fun bindView(): View {
        binding = ActivityMainBinding.inflate(layoutInflater)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            // 加载页面
            binding.webView.loadUrl(binding.ip.text.toString())
        }
    }
}