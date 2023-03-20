package com.silencefly96.module_base.net.funnet

import okhttp3.Call
import okhttp3.Response
import java.io.IOException

/**
 * 响应类
 *
 * @param code 状态码
 * @param message HTTP status message
 * @param header 请求头
 * @param body 返回数据
 */
data class Response(
    var code: Int,
    var message: String,
    var header: List<String>,
    var body: String
)

interface Callback {
    fun onFailure(call: Call, e: IOException)

    @Throws(IOException::class)
    fun onResponse(call: Call, response: Response)
}