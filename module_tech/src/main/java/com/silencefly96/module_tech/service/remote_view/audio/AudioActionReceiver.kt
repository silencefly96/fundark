package com.silencefly96.module_tech.service.remote_view.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 动态广播，接受通知栏动作
 */
class AudioActionReceiver(private val audioPlayer: AudioPlayer): BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        audioPlayer.apply {
            when(intent.action) {
                AudioPlayer.ACTION_LAST -> last()
                AudioPlayer.ACTION_PAUSE -> playOrPause()
                AudioPlayer.ACTION_NEXT -> next()
                AudioPlayer.ACTION_PLAY -> {
                    val position = intent.getIntExtra("position", 0)
                    val audio = intent.getParcelableExtra<AudioLoader.Audio>("audio")

                    Log.d("TAG", "onReceive: position=$position, audio=$audio")
                    // 两种播放方法
                    if (audio == null) {
                        play(position)
                    }else {
                        play(audio)
                    }
                }
            }
        }
    }
}