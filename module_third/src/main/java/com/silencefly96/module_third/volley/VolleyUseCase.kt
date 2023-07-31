package com.silencefly96.module_third.volley

import android.content.Context
import android.util.Log
import com.android.volley.AsyncRequestQueue
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.AsyncHttpStack
import com.android.volley.toolbox.BasicAsyncNetwork
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley

class VolleyUseCase {

    fun useVolley(context: Context) {
        // 1，创建RequestQueue，默认使用BasicNetwork、HurlStack
        val requestQueue = Volley.newRequestQueue(context)
        val url = "https://www.baidu.com"

        // 2，构造一个 request（请求）
        val request = StringRequest(Request.Method.GET, url, {
            Log.d("TAG", "useVolley result: $it")
        }, {
            Log.d("TAG", "useVolley error: ${it.message}")
        })

        // 3，把request添加进请求队列RequestQueue里面
        requestQueue.add(request)


        val asyncNetwork = BasicAsyncNetwork.Builder(object : AsyncHttpStack() {
            override fun executeRequest(
                request: Request<*>?,
                additionalHeaders: MutableMap<String, String>?,
                callback: OnRequestComplete?
            ) {

            }
        }).build()
        val asyncRequestQueue = AsyncRequestQueue.Builder(asyncNetwork).build()
        asyncRequestQueue.add(request)
        asyncRequestQueue.start()
    }

}