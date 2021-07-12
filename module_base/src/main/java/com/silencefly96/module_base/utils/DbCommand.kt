@file:Suppress("unused", "RedundantVisibilityModifier")

package com.silencefly96.module_base.utils

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * 单线程异步处理，处理完返回主线程，来自《安卓进阶之光》
 * 复杂异步线程调用（传递进度等）请用 AsyncTask
 *
 * @author fdk
 * @date 2021/07/09
 */
@Suppress("MemberVisibilityCanBePrivate")
public abstract class DbCommand<T> {

    /**
     * 执行异步操作
     */
    fun execute() {
        sExecutorService.execute {
            try {
                postResult(doInBackground())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 将结果投递到 UI 线程
     * @param result 结果数据
     */
    private fun postResult(result: T) {
        sHandler.post { onPostExecute(result) }
    }

    /**
     * 在后台执行的数据库操作
     * @return 处理完成的数据
     */
    protected abstract fun doInBackground(): T

    /**
     * 在UI线程处理结果，根据需要重写方法处理
     * @param result 结果
     */
    protected fun onPostExecute(result: T) {}

    companion object {

        /**
         * 只有一个线程的线程池
         */
        private val sExecutorService = Executors.newSingleThreadExecutor()

        /**
         * 主线程消息队列的 Handler
         */
        private val sHandler = Handler(Looper.getMainLooper())
    }

}