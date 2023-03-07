package com.silencefly96.module_base.net.httpUrlConnection

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