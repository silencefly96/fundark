package com.silencefly96.module_base.utils

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class HttpUrlNetModule: NetModule {

    private fun send(requestUrl: String, callBack: NetModule.OnRequestCallBack,
                     requestMethod: String) {
        var isSuccess = false
        var message: String?

        var inputStream: InputStream? = null
        var baos: ByteArrayOutputStream? = null
        try {
            val url = URL(requestUrl)
            val connection = url.openConnection().let {
                if (requestUrl.startsWith("https")) {
                    it as HttpsURLConnection
                }else {
                    it as HttpURLConnection
                }
            }

            // 设定请求的方法为"POST"，默认是GET
            connection.requestMethod = requestMethod
            connection.connectTimeout = 50000
            connection.readTimeout = 50000
            // User-Agent  IE9的标识
            connection.setRequestProperty(
                "User-Agent", "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; Trident/5.0;")
            connection.setRequestProperty("Accept-Language", "zh-CN")
            connection.setRequestProperty("Connection", "Keep-Alive")
            connection.setRequestProperty("Charset", "UTF-8")
            /*
            * 当我们要获取我们请求的http地址访问的数据时就是使用connection.getInputStream().read()方式时我们就
            * 需要setDoInput(true)，根据api文档我们可知doInput默认就是为true。我们可以不用手动设置了，如果不需要
            * 读取输入流的话那就setDoInput(false)。
            * 当我们要采用非get请求给一个http网络地址传参 就是使用connection.getOutputStream().write() 方法时
            * 我们就需要setDoOutput(true), 默认是false
            */
            // 设置是否从httpUrlConnection读入，默认情况下是true;
            connection.doInput = true
            // 设置是否向httpUrlConnection输出，如果是post请求，参数要放在http正文内，因此需要设为true, 默认是
            // false; Android  4.0 GET时候 用这句会变成POST  报错java.io.FileNotFoundException
            if (requestMethod == "POST") connection.doOutput = true
            connection.useCaches = false
            connection.connect()

            connection.contentLength
            if (connection.responseCode == 200) {
                inputStream = connection.inputStream //会隐式调用connect()
                baos = ByteArrayOutputStream()
                var readLen: Int
                val bytes = ByteArray(1024)
                while ((inputStream.read(bytes).also { readLen = it }) != -1) {
                    baos.write(bytes, 0, readLen)
                }
                val result = baos.toString()
                message = result
                isSuccess = true
            } else {
                message = "请求失败 code:" + connection.responseCode
            }

        }catch (e: MalformedURLException) {
            message = e.message
            e.printStackTrace()
        }catch (e: IOException) {
            message = e.message
            e.printStackTrace()
        }finally {
            try {
                baos?.close()
                inputStream?.close()
            } catch (e: IOException) {
                message = e.message
                e.printStackTrace()
            }
        }
        if (isSuccess) {
            callBack.onSuccess(message ?: "")
        } else {
            callBack.onError(message ?: "")
        }
    }

    override fun get(requestUrl: String, callBack: NetModule.OnRequestCallBack) {
        send(requestUrl, callBack, "GET")
    }

    override fun post(requestUrl: String, callBack: NetModule.OnRequestCallBack) {
        send(requestUrl, callBack, "POST")
    }

    override fun download() {

    }

    override fun upload() {

    }

}

interface NetModule {
    fun post(requestUrl: String, callBack: OnRequestCallBack)
    fun get(requestUrl: String, callBack: OnRequestCallBack)
    fun download()
    fun upload()

    interface OnRequestCallBack {
        fun onSuccess(json: String);
        fun onError(errorMsg: String);
    }
}