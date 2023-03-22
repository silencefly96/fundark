package com.silencefly96.module_base.net.funnet

import java.util.*
import java.util.concurrent.*

class Dispatcher {
    // 线程池空闲时处理线程
    private val idleCallback: Runnable? = null

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
        synchronized(this) { readyAsyncCalls.add(call) }
        promoteAndExecute()
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
                SynchronousQueue(), threadFactory("OkHttp Dispatcher", false)
            )
        }
        return executorService!!
    }

    // 线程工厂，用于创建线程
    fun threadFactory(name: String, daemon: Boolean): ThreadFactory {
        return ThreadFactory { runnable ->
            val result = Thread(runnable, name)
            result.isDaemon = daemon
            result
        }
    }

    // 执行代码位置
    private fun promoteAndExecute(): Boolean {
        // 下面代码需要用到锁，确保对象锁不在其他地方使用
        assert(!Thread.holdsLock(this))

        // 从ready列表中拿出call存到执行列表
        val executableCalls: MutableList<RealCall.AsyncCall> = ArrayList()
        var isRunning: Boolean
        synchronized(this) {
            val iterator = readyAsyncCalls.iterator()
            while (iterator.hasNext()) {
                val asyncCall = iterator.next()
                iterator.remove()
                executableCalls.add(asyncCall)
                runningAsyncCalls.add(asyncCall)
            }
            isRunning = runningCallsCount() > 0
        }

        // 调度执行
        for (asyncCall in executableCalls) {
            asyncCall.executeOn(executorService())
        }

        return isRunning
    }

    /**
     * 停止异步请求
     */
    fun finished(call: RealCall.AsyncCall) {
        finished(runningAsyncCalls, call)
    }

    /**
     * 停止同步请求
     */
    fun finished(call: RealCall) {
        finished(runningSyncCalls, call)
    }

    // 停止异步队列内的某个call
    private fun <T> finished(calls: Deque<T>, call: T) {
        var idleCallback: Runnable?
        synchronized(this) {
            if (!calls.remove(call)) throw AssertionError("Call wasn't in-flight!")
            idleCallback = this.idleCallback
        }
        val isRunning = promoteAndExecute()

        // 执行空闲时线程
        if (!isRunning && idleCallback != null) {
            idleCallback!!.run()
        }
    }

    @Synchronized
    fun runningCallsCount(): Int {
        return runningAsyncCalls.size + runningSyncCalls.size
    }
}