@file:Suppress("unused")

package com.silencefly96.module_base.utils

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.widget.Toast

/**
 * 使用系统自带分享功能，分享到微信、微博、QQ、QQ空间、钉钉
 *
 * @author fdk
 * @date 2023-05-08
 */
object ShareUtil {

    // 微信包名、分享页包名、朋友圈分享页包名
    private const val WECHAT_PACKAGE_NAME = "com.tencent.mm"
    private const val WECHAT_SHARE_ACTIVITY = "com.tencent.mm.ui.tools.ShareImgUI"
    private const val WECHAT_FRIEND_ACTIVITY = "com.tencent.mm.ui.tools.ShareToTimeLineUI"

    // 微博包名、微博分享页包名
    private const val WEIBO_PACKAGE_NAME = "com.sina.weibo"
    private const val WEIBO_SHARE_ACTIVITY = "com.sina.weibo.composerinde.ComposerDispatchActivity"

    // QQ包名、QQ分享页包名
    private const val MOBILEQQ_PACKAGE_NAME = "com.tencent.mobileqq"
    private const val MOBILEQQ_SHARE_ACTIVITY = "com.tencent.mobileqq.activity.JumpActivity"

    // QQ空间包名、QQ空间分享页包名
    private const val QZONE_PACKAGE_NAME = "com.qzone"
    private const val QZONE_SHARE_ACTIVITY = "com.qzonex.module.operation.ui.QZonePublishMoodActivity"

    // 钉钉包名、钉钉分享页包名
    private const val DINGDING_PACKAGE_NAME = "com.alibaba.android.rimet"
    private const val DINGDING_SHARE_ACTIVITY = "com.alibaba.android.rimet.biz.BokuiActivity"


    /**
     * 分享文字到微信好友
     *
     * @param context 上下文
     * @param text 文字
     */
    fun shareToWeChat(context: Context, text: String) {
        //判断是否安装微信，如果没有安装微信 又没有判断就直达微信分享是会挂掉的
        if (!isAppInstall(context, WECHAT_PACKAGE_NAME)) {
            showToast(context, "wechat is not install")
            return
        }
        shareText(context, text, WECHAT_PACKAGE_NAME, WECHAT_SHARE_ACTIVITY)
    }

    /**
     * 分享到微信，单图
     *
     * @param context 上下文
     * @param message 内容
     */
    fun shareToWeChat(context: Context, message: Message) {
        if (!isAppInstall(context, WEIBO_PACKAGE_NAME)) {
            showToast(context, "wechat is not install")
            return
        }

        try {
            val intent = Intent()
            intent.component = ComponentName(WECHAT_PACKAGE_NAME, WECHAT_SHARE_ACTIVITY)
            intent.action = Intent.ACTION_SEND
            intent.type = "image/*"

            val shareStr = """
                ${message.title}
                ${message.description}
                ${message.shareUrl}
                """.trimIndent()
            intent.putExtra(Intent.EXTRA_TEXT, shareStr)
            intent.putExtra("Kdescription", shareStr)
            // 给目标应用一个临时授权
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra(Intent.EXTRA_STREAM, message.thumbnail)

            context.startActivity(Intent.createChooser(intent, "分享"))
        }catch (e: Exception) {
            e.printStackTrace()
            showToast(context, "分享失败")
        }
    }

    /**
     * 分享到微信朋友圈，单图
     *
     * @param context 上下文
     * @param message 内容
     */
    fun shareToWeChatFriend(context: Context, message: Message) {
        if (!isAppInstall(context, WEIBO_PACKAGE_NAME)) {
            showToast(context, "wechat is not install")
            return
        }

        try {
            val intent = Intent()
            intent.component = ComponentName(WECHAT_PACKAGE_NAME, WECHAT_FRIEND_ACTIVITY)
            intent.action = Intent.ACTION_SEND
            intent.type = "image/*"

            val shareStr = """
                ${message.title}
                ${message.description}
                ${message.shareUrl}
                """.trimIndent()
            intent.putExtra(Intent.EXTRA_TEXT, shareStr)
            intent.putExtra("Kdescription", shareStr)
            // 给目标应用一个临时授权
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra(Intent.EXTRA_STREAM, message.thumbnail)

            context.startActivity(Intent.createChooser(intent, "分享"))
        }catch (e: Exception) {
            e.printStackTrace()
            showToast(context, "分享失败")
        }
    }

    /**
     * 分享文字到微博
     *
     * @param context 上下文
     * @param text 文字
     */
    fun shareToWeibo(context: Context, text: String) {
        if (!isAppInstall(context, WEIBO_PACKAGE_NAME)) {
            showToast(context, "weibo is not install")
            return
        }
        shareText(context, text, WEIBO_PACKAGE_NAME, WEIBO_SHARE_ACTIVITY)
    }

    /**
     * 分享图片到微博，单图
     *
     * @param context 上下文
     * @param message 内容
     */
    fun shareToWeibo(context: Context, message: Message) {
        if (!isAppInstall(context, WEIBO_PACKAGE_NAME)) {
            showToast(context, "weibo is not install")
            return
        }

        try {
            val intent = Intent()
            intent.component = ComponentName(WEIBO_PACKAGE_NAME, WEIBO_SHARE_ACTIVITY)
            intent.action = Intent.ACTION_SEND
            intent.type = "image/*"

            val shareStr = """
                ${message.title}
                ${message.description}
                ${message.shareUrl}
                """.trimIndent()
            intent.putExtra(Intent.EXTRA_TEXT, shareStr)
            intent.putExtra(Intent.EXTRA_STREAM, message.thumbnail)

            context.startActivity(Intent.createChooser(intent, "分享"))
        }catch (e: Exception) {
            e.printStackTrace()
            showToast(context, "分享失败")
        }
    }

    /**
     * 分享文字到QQ
     *
     * @param context 上下文
     * @param text 文字
     */
    fun shareToQQ(context: Context, text: String) {
        if (!isAppInstall(context, MOBILEQQ_PACKAGE_NAME)) {
            showToast(context, "您还没有安装QQ")
            return
        }
        shareText(context, text, MOBILEQQ_PACKAGE_NAME, MOBILEQQ_SHARE_ACTIVITY)
    }

    /**
     * 分享图片到QQ，单图
     *
     * @param context 上下文
     * @param message 内容
     */
    fun shareToQQ(context: Context, message: Message) {
        if (!isAppInstall(context, MOBILEQQ_PACKAGE_NAME)) {
            showToast(context, "weibo is not install")
            return
        }

        try {
            val intent = Intent()
            intent.component = ComponentName(MOBILEQQ_PACKAGE_NAME, MOBILEQQ_SHARE_ACTIVITY)
            intent.action = Intent.ACTION_SEND
            intent.type = "image/*"

            val shareStr = """
                ${message.title}
                ${message.description}
                ${message.shareUrl}
                """.trimIndent()
            intent.putExtra(Intent.EXTRA_TEXT, shareStr)
            intent.putExtra(Intent.EXTRA_STREAM, message.thumbnail)

            context.startActivity(Intent.createChooser(intent, "分享"))
        }catch (e: Exception) {
            e.printStackTrace()
            showToast(context, "分享失败")
        }
    }

    /**
     * 分享文字到QQ空间
     *
     * @param context 上下文
     * @param text 文字
     */
    fun shareToQzone(context: Context, text: String) {
        if (!isAppInstall(context, QZONE_PACKAGE_NAME)) {
            showToast(context, "您还没有安装QQ空间")
            return
        }
        shareText(context, text, QZONE_PACKAGE_NAME, QZONE_SHARE_ACTIVITY)
    }

    /**
     * 分享图片到QQ空间，单图
     *
     * @param context 上下文
     * @param message 内容
     */
    fun shareToQzone(context: Context, message: Message) {
        if (!isAppInstall(context, QZONE_PACKAGE_NAME)) {
            showToast(context, "weibo is not install")
            return
        }

        try {
            val intent = Intent()
            intent.component = ComponentName(QZONE_PACKAGE_NAME, QZONE_SHARE_ACTIVITY)
            intent.action = Intent.ACTION_SEND
            intent.type = "image/*"

            val shareStr = """
                ${message.title}
                ${message.description}
                ${message.shareUrl}
                """.trimIndent()
            intent.putExtra(Intent.EXTRA_TEXT, shareStr)
            intent.putExtra(Intent.EXTRA_STREAM, message.thumbnail)

            context.startActivity(Intent.createChooser(intent, "分享"))
        }catch (e: Exception) {
            e.printStackTrace()
            showToast(context, "分享失败")
        }
    }

    /**
     * 分享文字到钉钉
     *
     * @param context 上下文
     * @param text 文字
     */
    fun shareToDingDing(context: Context, text: String) {
        if (!isAppInstall(context, DINGDING_PACKAGE_NAME)) {
            showToast(context, "您还没有安装钉钉")
            return
        }

        shareText(context, text, DINGDING_PACKAGE_NAME, DINGDING_SHARE_ACTIVITY)
    }

    /**
     * 分享图片到钉钉，单图
     *
     * @param context 上下文
     * @param message 内容
     */
    fun shareToDingDing(context: Context, message: Message) {
        if (!isAppInstall(context, DINGDING_PACKAGE_NAME)) {
            showToast(context, "您还没有安装钉钉")
            return
        }

        try {
            val intent = Intent()
            intent.component = ComponentName(DINGDING_PACKAGE_NAME, DINGDING_SHARE_ACTIVITY)
            intent.action = Intent.ACTION_SEND
            intent.type = "image/*"

            val shareStr = """
                ${message.title}
                ${message.description}
                ${message.shareUrl}
                """.trimIndent()
            intent.putExtra(Intent.EXTRA_TEXT, shareStr)
            intent.putExtra(Intent.EXTRA_STREAM, message.thumbnail)

            context.startActivity(Intent.createChooser(intent, "分享"))
        }catch (e: Exception) {
            e.printStackTrace()
            showToast(context, "分享失败")
        }
    }

    private fun shareText(context: Context, text: String, pkg: String, cls: String) {
        if (TextUtils.isEmpty(text)) {
            showToast(context, "内容不能为空")
            return
        }

        try {
            val intent = Intent()
            intent.component = ComponentName(pkg, cls)
            intent.action = Intent.ACTION_SEND
            intent.putExtra(Intent.EXTRA_TEXT, text)
            intent.type = "text/plain"
            context.startActivity(Intent.createChooser(intent, "分享"))
        }catch (e: Exception) {
            e.printStackTrace()
            showToast(context, "分享失败")
        }
    }

    /**
     * 检测手机是否安装某个应用
     *
     * @param context
     * @param appPackageName 应用包名
     * @return true-安装，false-未安装
     */
    @SuppressLint("QueryPermissionsNeeded")
    private fun isAppInstall(context: Context, appPackageName: String): Boolean {
        val packageManager = context.packageManager // 获取packagemanager
        val info = packageManager.getInstalledPackages(0) // 获取所有已安装程序的包信息
        for (i in info.indices) {
            val pn = info[i].packageName
            if (appPackageName == pn) {
                return true
            }
        }
        return false
    }

    private fun showToast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    /**
     * 数据类
     * shareStr = title + description + shareUrl
     *
     * @param title 标题
     * @param description 内容
     * @param shareUrl 链接
     * @param thumbnail 图标
     */
    data class Message(
        var title: String = "",
        var description: String = "",
        var shareUrl: String = "",
        var thumbnail: Uri? = null
    )
}