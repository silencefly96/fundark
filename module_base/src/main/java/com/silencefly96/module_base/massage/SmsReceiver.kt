package com.silencefly96.module_base.massage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.util.Consumer

class SmsReceiver : BroadcastReceiver() {

    var smsConsumer: Consumer<String>? = null

    companion object {
        // 使用广播进行监听
        fun register(receiver: SmsReceiver, context: Context) {
            val smsFilter = IntentFilter()
            smsFilter.addAction("android.provider.Telephony.SMS_RECEIVED")
            smsFilter.addAction("android.provider.Telephony.SMS_DELIVER")
            context.registerReceiver(receiver, smsFilter)
        }

        fun unregister(receiver: SmsReceiver, context: Context) {
            context.unregisterReceiver(receiver)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SmsReceiver", "onReceive: " + intent.action)
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            // 这里携带的是以“pdus”为key、短信内容为value的键值对，android设备接收到的SMS是pdu形式的
            var msg: SmsMessage
            val format = intent.getStringExtra("format")
            intent.extras?.let { bundle ->
                //生成一个数组，将短信内容赋值进去
                val smsObg = bundle["pdus"] as Array<*>?
                for (pdu in smsObg!!) {
                    msg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        SmsMessage.createFromPdu(pdu as ByteArray, format)
                    }else {
                        SmsMessage.createFromPdu(pdu as ByteArray)
                    }
                    // 获取短信内容
                    val content = msg.displayMessageBody
                    // 获取短信发送方地址
                    // String from = msg.getOriginatingAddress();
                    // 将数据传递出去
                    smsConsumer?.accept(content)
                }
            }
        }
    }
}