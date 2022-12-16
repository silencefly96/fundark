package com.silencefly96.module_mvvm.plan.model

import androidx.lifecycle.liveData
import kotlin.coroutines.CoroutineContext

interface BaseRepository {

    fun <T> runAsLiveData(context: CoroutineContext, block: suspend () -> Result<T>) =
            liveData(context) {
                val result = try {
                    block()
                } catch (e: Exception) {
                    Result.failure(e)
                }
                emit(result)
            }
}