package com.silencefly96.module_base.net.funnet

interface Call {
    /**
     * 异步执行
     */
    fun enqueue()

    /**
     * 同步执行
     */
    fun execute()

    /**
     * 取消
     */
    fun cancel()
}