package com.silencefly96.module_base.utils.massage

import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.core.util.Consumer

// 主线程中处理
class SmsHandler : Handler(Looper.getMainLooper()) {

    companion object{
        // 对SmsObserver消息延时，保证SmsReceiver先被触发
        private const val LISTEN_TIME = 3000L
    }

    var smsConsumer: Consumer<String>? = null

    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)
        when (msg.what) {
            0 -> {
                val content = msg.obj as String
                smsConsumer?.accept(content)
                removeMessages(1)
            }
            1 -> {
                removeMessages(1)
                sendMessageDelayed(obtainMessage(0, msg.obj), LISTEN_TIME)
            }
            else -> {}
        }
    }
}