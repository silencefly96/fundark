package com.silencefly96.module_third.okhttp3

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
    var code: Int = -1,
    var message: String = "",
    var header: List<String> = ArrayList(),
    var body: String = ""
){
    var priorResponse: Response? = null
}

interface Callback {
    fun onFailure(call: Call, e: IOException)

    @Throws(IOException::class)
    fun onResponse(call: Call, response: Response)
}