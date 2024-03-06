@file:Suppress("unused", "RedundantVisibilityModifier")

package com.silencefly96.module_tech.service.remote_view.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.silencefly96.module_tech.R

class AudioNotificationManager(val context: Context) {

    // 通知 渠道信息
    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "channel_remote_view_id"
        const val CHANNEL_NAME = "channel_remote_view_name"
    }

    // 通知管理
    private val manager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    /**
     * 发送音频播放器到通知栏
     *
     * @param pendingIntent 点击整个通知的意图
     */
    public fun createAudioNotificationBuilder(
        pendingIntent: PendingIntent,
    ): NotificationCompat.Builder {
        // Android8 需要创建通知渠道
        if (Build.VERSION.SDK_INT >= 26) {
            createNotificationChannel()
        }

        // 创建NotificationBuilder，通知由service发送
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)                         // 必须设置，否则会奔溃
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            // .setCustomContentView(remoteViews)                          // 折叠后通知显示的布局
            // .setCustomHeadsUpContentView(remoteViews)                   // 横幅样式显示的布局
            // .setCustomBigContentView(remoteViews)                       // 展开后通知显示的布局
            // .setContent(remoteViews)                                    // 兼容低版本
            .setColor(ContextCompat.getColor(context, R.color.blue))    // 小图标的颜色
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)                // 默认配置,通知的提示音、震动等
            // .setAutoCancel(true)                                     // 允许点击后清除通知
            .setContentIntent(pendingIntent)
    }

    /**
     * 创建音频播放器通知
     *
     * @param remoteViews 显示的自定义view
     * @param builder 通知builder
     * @return 通知
     */
    public fun createAudioNotification(
        remoteViews: RemoteViews,
        builder: NotificationCompat.Builder,
    ): Notification {
        // 创建通知
        return builder
            .setCustomContentView(remoteViews)                          // 折叠后通知显示的布局
            .setCustomHeadsUpContentView(remoteViews)                   // 横幅样式显示的布局
            .setCustomBigContentView(remoteViews)                       // 展开后通知显示的布局
            .setContent(remoteViews)                                    // 兼容低版本
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)                // 默认配置,通知的提示音、震动等
            .build()
    }

    /**
     * 更新音频播放器到通知栏
     *
     * @param remoteViews 显示的自定义view
     * @param builder 通知builder
     */
    public fun updateAudioNotification(
        remoteViews: RemoteViews,
        builder: NotificationCompat.Builder,
        notificationId: Int = NOTIFICATION_ID
    ) {
        // 更新remoteView
        val notification = createAudioNotification(remoteViews, builder)

        // 通过NOTIFICATION_ID发送，可借此关闭
        manager.notify(notificationId, notification)
    }

    /**
     * 发送音频播放器到通知栏
     *
     * @param notificationId 通知ID
     */
    public fun cancelAudioNotification(notificationId: Int = NOTIFICATION_ID) {
        manager.cancel(notificationId)
    }

    /**
     * 创建通知渠道，高版本要求，不然无法发送通知
     *
     * @param importance 重要程度
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    fun createNotificationChannel(importance: Int = NotificationManager.IMPORTANCE_DEFAULT) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            importance
        ).apply {
            // 是否在应用图标的右上角展示小红点
            setShowBadge(false)
            // 推送消息时是否让呼吸灯闪烁。
            enableLights(true)
            // 推送消息时是否让手机震动。
            enableVibration(true)
            // 呼吸灯颜色
            lightColor = Color.BLUE
        }
        manager.createNotificationChannel(channel)
    }

}