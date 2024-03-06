@file:Suppress("unused", "RedundantVisibilityModifier")

package com.silencefly96.module_tech.service.remote_view.audio

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.silencefly96.module_tech.MainActivity
import com.silencefly96.module_tech.R

class AudioPlayer(val context: Context) {

    companion object {
        // PendingIntent请求码
        const val REQUEST_CODE_ACTIVITY     = 0
        const val REQUEST_CODE_CLICK_LAST   = 1
        const val REQUEST_CODE_CLICK_PAUSE  = 2
        const val REQUEST_CODE_CLICK_NEXT   = 3

        // Action标识
        const val ACTION_LAST  = "com.silencefly96.module_tech.tech.remote_view.last"
        const val ACTION_PAUSE = "com.silencefly96.module_tech.tech.remote_view.pause"
        const val ACTION_NEXT  = "com.silencefly96.module_tech.tech.remote_view.next"
        const val ACTION_PLAY  = "com.silencefly96.module_tech.tech.remote_view.play"
    }

    // 通知管理
    private val notificationManager = AudioNotificationManager(context)
    private var notificationBuilder: NotificationCompat.Builder? = null

    // 通知意图广播
    private var mActionReceiver: AudioActionReceiver? = null

    // 歌曲列表
    private val mAudios = ArrayList<AudioLoader.Audio>()

    private var mCurPosition = 0

    private var isPlaying = false

    public fun init(audios: List<AudioLoader.Audio>) {
        Log.d("TAG", "init: $audios")
        mAudios.addAll(audios)

        // 启动广播接收器
        registerReceiver()
    }

    private fun registerReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_LAST)
            addAction(ACTION_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PLAY)
        }
        mActionReceiver = AudioActionReceiver(this)
        context.registerReceiver(mActionReceiver, intentFilter)
    }

    /**
     * 创建一个默认的通知，不发送，由service发送
     */
    public fun startNotification(): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        // 创建一个pendingIntent，点击整个通知跳转APP
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_ACTIVITY,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // 创建通知管理器
        notificationBuilder = notificationManager.createAudioNotificationBuilder(pendingIntent)
        return notificationManager.createAudioNotification(
            getRemoteView(null),
            notificationBuilder!!
        )
    }

    private fun updateNotification(audio: AudioLoader.Audio) {
        // 通过notificationBuilder更新remoteView去更新通知栏
        notificationBuilder?.let {
            notificationManager.updateAudioNotification(
                getRemoteView(audio),
                it
            )
        }
    }

    private fun getRemoteView(audio: AudioLoader.Audio?): RemoteViews {
        val remoteViews = RemoteViews(context.packageName, R.layout.item_remote_notification)

        // 设置标题
        val title = audio?.title ?: "waiting for play music..."
        remoteViews.setTextViewText(R.id.title, title)

        // 设置播放按钮
        val text = if (isPlaying) "PAUSE" else "PALY"
        remoteViews.setTextViewText(R.id.pause, text)

        // 上一首
        val lastIntent = Intent(ACTION_LAST)
        val lastPendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_CLICK_LAST, lastIntent, PendingIntent.FLAG_IMMUTABLE
        )
        remoteViews.setOnClickPendingIntent(R.id.last, lastPendingIntent)

        // 暂停
        val pauseIntent = Intent(ACTION_PAUSE)
        val pausePendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_CLICK_PAUSE, pauseIntent, PendingIntent.FLAG_IMMUTABLE
        )
        remoteViews.setOnClickPendingIntent(R.id.pause, pausePendingIntent)

        // 下一首
        val nextIntent = Intent(ACTION_NEXT)
        val nextPendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_CLICK_NEXT, nextIntent, PendingIntent.FLAG_IMMUTABLE
        )
        remoteViews.setOnClickPendingIntent(R.id.next, nextPendingIntent)

        return remoteViews
    }

    /**
     * 播放列表音乐
     *
     * @param index 列表内索引
     */
    public fun play(index: Int) {
        // 列表循环
        mCurPosition = (index + mAudios.size) % mAudios.size
        // 更新通知栏
        updateNotification(mAudios[mCurPosition])
        // TODO 播放音乐
        isPlaying = true
        Log.d("TAG", "play: $mCurPosition")
    }

    /**
     * 播放列表音乐
     *
     * @param audio 音频
     */
    public fun play(audio: AudioLoader.Audio) {
        val index = mAudios.indexOf(audio)
        if (index >= 0) {
            play(index)
        }
    }

    /**
     * 播放或暂停音乐
     */
    public fun playOrPause() {
        // TODO 暂停或者重新播放音乐
        isPlaying = !isPlaying
        // 更新通知栏
        updateNotification(mAudios[mCurPosition])
        Log.d("TAG", "pause: $isPlaying")
    }

    /**
     * 上一首
     */
    public fun last() {
        play(mCurPosition - 1)
    }

    /**
     * 下一首
     */
    public fun next() {
        play(mCurPosition + 1)
    }

    /**
     * 注销
     */
    public fun onDestroy() {
        Log.d("TAG", "AudioPlayer onDestroy: ")
        context.unregisterReceiver(mActionReceiver)
    }
}