package com.silencefly96.module_tech.tech.remote_view.audio.service

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class AudioJobService: JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        TODO("Not yet implemented")
    }

    // 在条件不满足时才触发，返回true，表示任务将被再次调度执行，如果返回false表示任务完全结束
    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }
}