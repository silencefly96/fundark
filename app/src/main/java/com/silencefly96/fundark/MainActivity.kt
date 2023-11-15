package com.silencefly96.fundark

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.alibaba.sdk.android.httpdns.HttpDns
import com.silencefly96.fundark.databinding.ActivityMainBinding
import com.silencefly96.module_base.base.BaseActivity
import okhttp3.OkHttpClient
import java.net.URL
import java.util.Arrays


class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun bindView(): View {
        binding = ActivityMainBinding.inflate(layoutInflater)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}