package com.silencefly96.module_base.net.funnet

/**
 * 事件监听
 */
interface EventListener {
    /**
     * 网络回复
     */
    fun onResponse()

    /**
     * 网络出错：连接失败、未发送出去..
     */
    fun onError()

    /**
     * 超时
     */
    fun onTimeOut()
}