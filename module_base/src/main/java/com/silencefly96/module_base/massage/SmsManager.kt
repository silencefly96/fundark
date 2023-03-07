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

    fun startListen(context: Context, consumer: Consumer<String>) {
        // 使用receiver监听
        if (smsReceiver == null) {
            smsReceiver = SmsReceiver()
        }
        smsReceiver!!.smsConsumer = consumer
        SmsReceiver.register(smsReceiver!!, context)

        // 使用media监听
        if (smsObserver == null) {
            smsObserver = SmsObserver()
        }
        smsObserver!!.weakReference = WeakReference(context)
        smsObserver!!.mHandler.smsConsumer = consumer
        SmsObserver.register(smsObserver!!, context)
    }

    fun finishListen(context: Context) {
        // 注销广播
        smsReceiver?.let {
            SmsReceiver.unregister(it, context)
        }
        smsReceiver = null

        // 注销media监听
        smsObserver?.let {
            SmsObserver.unregister(it, context)
        }
        smsObserver = null
    }
}