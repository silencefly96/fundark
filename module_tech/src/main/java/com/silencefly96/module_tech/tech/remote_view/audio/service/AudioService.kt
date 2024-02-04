package com.silencefly96.module_tech.tech.remote_view.audio.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.lifecycle.LifecycleService
import com.silencefly96.module_tech.tech.remote_view.audio.AudioLoader
import com.silencefly96.module_tech.tech.remote_view.audio.AudioNotificationManager.Companion.NOTIFICATION_ID
import com.silencefly96.module_tech.tech.remote_view.audio.AudioPlayer

class AudioService: LifecycleService() {

    // 音乐播放器
    private val mAudioPlayer by lazy {
        AudioPlayer(this@AudioService)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // 使用协程查找音频
        AudioLoader.loadAudioByMediaStoreCoroutines(
            this,
            object : AudioLoader.OnAudioPrepared {
                override fun onAudioPrepared(result: List<AudioLoader.Audio>?) {
                    result?.let {
                        // 获取播放列表，然后播放第一首
                        mAudioPlayer.init(it)
                        if (it.isNotEmpty()) {
                            mAudioPlayer.play(0)
                        }
                    }
                }
            },
            lifecycle)

        // 创建通知启动前台服务(要在5s内)
        val notification = mAudioPlayer.startNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 安卓10要添加一个参数，在manifest中配置
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("TAG", "AudioService onDestroy: ")
        mAudioPlayer.onDestroy()
        super.onDestroy()
    }
}