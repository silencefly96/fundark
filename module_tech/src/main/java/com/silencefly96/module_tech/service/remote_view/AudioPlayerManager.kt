package com.silencefly96.module_tech.service.remote_view

import android.content.Context
import android.content.Intent
import android.os.Build
import com.silencefly96.module_tech.service.remote_view.audio.service.AudioService

class AudioPlayerManager(val context: Context) {

    /**
     * APP在前台启动“前台服务”
     */
    public fun startForegroundServiceFromForeground() {
        val intent = Intent(context, AudioService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            // Android8 必须通过startForegroundService开启前台服务，Android9 需要添加权限
            context.startForegroundService(intent)
        }else {
            context.startService(intent)
        }
    }

    /**
     * APP在后台启动“前台服务”
     */
    public fun startForegroundServiceFromBackgroud() {
        val intent = Intent(context, AudioService::class.java)
        if (Build.VERSION.SDK_INT >= 31) {
            // Android12 startForegroundService被禁用，推荐使用WorkManager

        }else if (Build.VERSION.SDK_INT >= 26) {
            // Android8 必须通过startForegroundService开启前台服务，Android9 需要添加权限
            context.startForegroundService(intent)
        }else {
            context.startService(intent)
        }
    }

    /**
     * APP启动“后台服务”
     */
    public fun startBackgroundService() {
        val intent = Intent(context, AudioService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            // Android8之后对后台应用做了限制，无法无限期运行，推荐使用JobScheduler

        }else {
            context.startService(intent)
        }
    }

    /**
     * 及时关闭service
     */
    public fun cancelForegroundServiceFromForeground() {
        val intent = Intent(context, AudioService::class.java)
        context.stopService(intent)
    }

}