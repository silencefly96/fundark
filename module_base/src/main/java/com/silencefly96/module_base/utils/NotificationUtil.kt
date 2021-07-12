@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.silencefly96.module_base.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

/**
 * 向通知栏发送通知工具类
 * 用法
 * private void sendAlarmNotification() {
 *     NotificationUtils notificationUtils = new NotificationUtils(this);
 *     Intent intent = new Intent(this, XXXActivity.class);
 *     notificationUtils.sendNotification("电量警告", "设备电量过低，请及时充电！", intent);
 * }
 *
 * @author fdk
 * @date 2021/07/12
 */
class NotificationUtil(context: Context?) : ContextWrapper(context) {

    /**
     * 消息管理器
     */
    private var manager: NotificationManager? = null
        get() {
            //懒汉式
            if (field == null) {
                field = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            }
            return field
        }

    /**
     * 发送消息通知到通知栏
     * @param title 通知标题
     * @param content 通知内容
     * @param intent 点击跳转到指定页面的intent
     */
    fun sendNotification(title: String?, content: String?, intent: Intent?) {
        val pendingIntent =
            PendingIntent.getActivity(this, 0, intent, 0)

        if (Build.VERSION.SDK_INT >= 26) {
            //创建通知渠道，高版本要求，不然无法发送通知
            createNotificationChannel()
            val notification =
                getChannelNotification(title, content).setContentIntent(pendingIntent).build()
            //发送通知
            manager!!.notify(1, notification)
        } else {
            val notification =
                getNotification25(title, content).setContentIntent(pendingIntent).build()
            //发送通知
            manager!!.notify(1, notification)
        }
    }

    /**
     * 发送消息通知到通知栏
     * @param builder 在外部设置好的 Notification.Builder
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun sendNotification(builder: Notification.Builder) {
        //创建通知渠道，高版本要求，不然无法发送通知
        createNotificationChannel()
        //发送通知
        manager!!.notify(1, builder.build())
    }

    /**
     * 发送消息通知到通知栏
     * @param builder 在外部设置好的 NotificationCompat.Builder
     */
    fun sendNotification(builder: NotificationCompat.Builder) {
        //发送通知
        manager!!.notify(1, builder.build())
    }

    /**
     * 创建通知渠道，高版本要求，不然无法发送通知
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    fun createNotificationChannel() {
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
        manager!!.createNotificationChannel(channel)
    }

    /**
     * 获得 Notification.Builder，用于创建通知
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun getChannelNotification(title: String?, content: String?): Notification.Builder {
        return Notification.Builder(applicationContext, id)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_more)//自定义小图标
            .setAutoCancel(true)
    }

    /**
     * 获得 NotificationCompat.Builder，用于创建通知
     */
    fun getNotification25(title: String?, content: String?): NotificationCompat.Builder {
        return NotificationCompat.Builder(applicationContext, id)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_more)//自定义小图标
            .setAutoCancel(true)
    }

    companion object {
        const val id = "channel_1"
        const val name = "channel_name_1"
    }
}