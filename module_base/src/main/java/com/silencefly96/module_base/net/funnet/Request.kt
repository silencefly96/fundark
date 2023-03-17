package com.silencefly96.module_base.net.funnet

import android.util.ArrayMap

/**
 * 请求类
 *
 * @param url 请求链接
 * @param header 请求头
 * @param method 请求方法
 * @param params 参数
 */
data class Request(
    var url: String,
    var header: List<String> = ArrayList(),
    var method: String = "GET",
    var params: Map<String, String> = ArrayMap()
)