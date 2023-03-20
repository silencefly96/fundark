package com.silencefly96.module_base.net.funnet

import okhttp3.internal.Util
import java.lang.AssertionError
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class Dispatcher {

    // 线程池，懒汉式创建
    private var executorService: ExecutorService? = null

    // 执行的同步请求，包含已取消但未结束的请求
    private val runningSyncCalls: Deque<RealCall> = ArrayDeque()

    // 已添加的异步请求
    private val readyAsyncCalls: Deque<RealCall.AsyncCall> = ArrayDeque()

    // 执行的异步请求，包含已取消但未结束的请求
    private val runningAsyncCalls: Deque<RealCall.AsyncCall> = ArrayDeque()

    /**
     * 同步请求
     */
    fun enqueue(call: RealCall.AsyncCall) {

    }

    /**
     * 异步请求
     */
    @Synchronized
    fun executed(call: RealCall) {
        runningSyncCalls.add(call)
    }

    /**
     * 线程池
     */
    @Synchronized
    fun executorService(): ExecutorService {
        if (executorService == null) {
            executorService = ThreadPoolExecutor(
                0, Int.MAX_VALUE, 60, TimeUnit.SECONDS,
                SynchronousQueue(), Util.threadFactory("OkHttp Dispatcher", false)
            )
        }
        return executorService!!
    }

    // 执行代码位置
    private fun promoteAndExecute(): Boolean {

        return false
    }

    /**
     * 停止请求
     */
    fun finished(call: RealCall) {
        finished(runningSyncCalls, call)
    }

    // 停止异步队列内的某个call
    private fun <T> finished(calls: Deque<T>, call: T) {
        synchronized(this) {
            if (!calls.remove(call)) throw AssertionError("Call wasn't in-flight!")
        }
        promoteAndExecute()
    }
}