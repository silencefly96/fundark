package com.silencefly96.module_base.massage

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import androidx.core.util.Consumer
import java.lang.ref.WeakReference
import java.util.ArrayList

@Suppress("unused")
object SmsManager {

    private var smsHandler: SmsHandler? = null
    private var smsReceiver: SmsReceiver? = null
    private var smsObserver: SmsObserver? = null

    // 收件箱，Telephony.Sms.Inbox.CONTENT_URI
    val SMS_INBOX: Uri = Uri.parse("content://sms/inbox")
    // 已发送，Telephony.Sms.Sent.CONTENT_URI
    val SMS_SENT: Uri = Uri.parse("content://sms/sent")
    // 草稿，Telephony.Sms.Draft.CONTENT_URI
    val SMS_DRAFT: Uri = Uri.parse("content://sms/draft")
    // 发件箱，Telephony.Sms.Outbox.CONTENT_URI
    val SMS_OUTBOX: Uri = Uri.parse("content://sms/outbox")

    // 查询列
    val PROJECTION = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE
    )

    fun readSms(context: Context, uri: Uri, projection: Array<String>): List<Pair<String, String>> {
        // TIPS: 考虑使用线程池处理query
        val data = ArrayList<Pair<String, String>>()
        val cr: ContentResolver = context.contentResolver
        val cur: Cursor? = cr.query(uri, projection,
            null, null, Telephony.Sms.Inbox.DEFAULT_SORT_ORDER)
        if (null != cur) {
            if (cur.moveToNext()) {
                val number: String = cur.getString(cur.getColumnIndexOrThrow("address")) //手机号
                // val name: String = cur.getString(cur.getColumnIndexOrThrow("person")) //联系人姓名列表
                val body: String = cur.getString(cur.getColumnIndexOrThrow("body")) //短信内容
                data.add(Pair(number, body))
            }
        }
        cur?.close()
        return data
    }

    fun startListen(context: Context, consumer: Consumer<String>, valueTimeMillis: Long = 2 * 60 * 1000) {
        // 在smsHandler内统一处理
        if (smsHandler == null) {
            smsHandler = SmsHandler()
        }
        smsHandler!!.smsConsumer = consumer

        // 使用receiver监听，可以保证准确性，但不一定触发
        if (smsReceiver == null) {
            smsReceiver = SmsReceiver(smsHandler!!)
        }
        SmsReceiver.register(smsReceiver!!, context)

        // 使用media监听，可以保证触发，但是可能拿不到验证码或者最新短信
        if (smsObserver == null) {
            smsObserver = SmsObserver(smsHandler!!)
        }
        smsObserver!!.weakReference = WeakReference(context)
        // 为了防止拿到过期短信，增加对时间间隔的验证，默认监听内两分钟的短信有效
        smsObserver!!.startDate = System.currentTimeMillis()
        smsObserver!!.valueTimeMillis = valueTimeMillis
        SmsObserver.register(smsObserver!!, context)
    }

    fun finishListen(context: Context) {
        // 注销广播
        smsReceiver?.let {
            SmsReceiver.unregister(it, context)
        }
        //smsReceiver = null

        // 注销media监听
        smsObserver?.let {
            SmsObserver.unregister(it, context)
        }
        //smsObserver = null

        // 清空消息
        smsHandler?.let {
            it.removeMessages(0)
            it.removeMessages(1)
        }
    }
}