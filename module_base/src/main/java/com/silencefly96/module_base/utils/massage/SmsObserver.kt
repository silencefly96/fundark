package com.silencefly96.module_base.utils.massage

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.annotation.RequiresApi
import java.lang.ref.WeakReference

@RequiresApi(api = Build.VERSION_CODES.KITKAT)
class SmsObserver @JvmOverloads constructor(
    val mHandler: SmsHandler = SmsHandler()
) : ContentObserver(mHandler) {

    var weakReference: WeakReference<Context>? = null

    // 注册时时间
    var startDate: Long = 0L

    // 有效时间间隔
    var valueTimeMillis: Long = 0L

    companion object {
        //API level>=23，可直接使用Telephony.Sms.Inbox.CONTENT_URI，用于获取 cursor
        const val SMS_INBOX_URI = "content://sms/inbox"
        //API level>=23，可直接使用Telephony.Sms.CONTENT_URI,用于注册内容观察者
        private const val SMS_URI = "content://sms"

        val PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.PERSON,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        // 使用ContentResolver进行监听
        fun register(observer: SmsObserver, context: Context) {
            context.contentResolver.registerContentObserver(
                Uri.parse(SMS_URI), true, observer)
        }

        fun unregister(observer: SmsObserver, context: Context) {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d("ReadSmsObserver", "onChange: ")
        // 当收到短信时调用一次，当短信显示到屏幕上时又调用一次，所以需要return掉一次调用
        if (uri.toString() == "content://sms/raw") {
            return
        }
        // 读取短信收件箱，只读取未读短信，即read=0，并按照默认排序
        weakReference?.get()?.let { context ->
            val cursor = context.contentResolver.query(
                Uri.parse(SMS_INBOX_URI), PROJECTION,
                Telephony.Sms.READ + "=?", arrayOf("0"),
                Telephony.Sms.Inbox.DEFAULT_SORT_ORDER
            )?: return
            // 获取倒序的第一条短信
            if (cursor.moveToFirst()) {
                // 读取短信发送人
                // String address = cursor.getString(cursor.getColumnIndex(Telephony.Sms.ADDRESS));
                // 读取短息内容
                val smsBody = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY))
                // 读取日期
                val smsDate = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))

                // 只取两分钟内的短信
                if (startDate == 0L || (smsDate - startDate) < valueTimeMillis) {
                    // 传递出去
                    mHandler.obtainMessage(1, smsBody).sendToTarget()
                } else {
                    mHandler.obtainMessage(1, "未获取到短信！").sendToTarget()
                }
            }
            // 关闭cursor的方法
            cursor.close()
        }
    }
}
