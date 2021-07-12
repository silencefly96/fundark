package com.silencefly96.module_base.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 获取手机详细信息
 * 详细说明可以看我博客：https://blog.csdn.net/lfq88/article/details/118088991
 * @author fdk
 * @date 2021/07/09
 */
@Suppress("unused")
object DeviceInfoModel {

    /**
     * 机型，格式如 meizu_mx3
     */
    val phoneModel: String
        get() = Build.PRODUCT

    /**
     * 获取操作系统，格式如 Android4.4
     */
    val oS: String
        get() = "Android" + Build.VERSION.RELEASE

    /**
     * 获取手机分辨率
     * @param context 上下文对象
     * @return 手机分辨率，格式如 1920*1080
     */
    fun getResolution(context: Context): String {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        return "$screenWidth*$screenHeight"
    }

    /**
     * 获取 MEID
     * @param context 上下文对象
     * @return 唯一设备号
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    fun getMEID(context: Context): String {
        if (!checkReadPhoneStatePermission(context)) {
            return ""
        }
        val meid: String
        val mTelephonyMgr = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        //Android版本大于o-26-优化后的获取
        meid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mTelephonyMgr.meid
        } else {
            mTelephonyMgr.deviceId
        }
        return meid
    }

    /**
     * 获取唯一设备号
     * @param context 上下文对象
     * @param index 卡槽号，0或1
     * @return 唯一设备号
     */
    @SuppressLint("MissingPermission")
    fun getIMEI(context: Context, index: Int): String {
        if (!checkReadPhoneStatePermission(context)) {
            return ""
        }
        var imei = ""
        val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        //Android版本大于o-26-优化后的获取
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            imei = manager.getImei(index)
        } else {
            //通过反射获取
            try {
                val method = manager.javaClass.getMethod("getDeviceIdGemini", Int::class.javaPrimitiveType)
                imei = method.invoke(manager, index) as String
            } catch (e: Exception) {
                try {
                    val method = manager.javaClass.getMethod("getDeviceId", Int::class.javaPrimitiveType)
                    imei = method.invoke(manager, index) as String
                } catch (e1: Exception) {
                    e1.printStackTrace()
                }
            }
        }
        return imei
    }

    /**
     * 获取卡1的IMEI
     * @param context 上下文对象
     * @return 卡1的IMEI
     */
    fun getIMEI(context: Context): String {
        return getIMEI(context, 0)
    }

    /**
     * 获取卡2的IMEI
     * @param context 上下文对象
     * @return 卡2的IMEI
     */
    fun getIMEI2(context: Context): String {
        return getIMEI(context, 1)
    }

    /**
     * 检查是否有获取手机信息权限
     */
    private fun checkReadPhoneStatePermission(context: Context): Boolean {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                //请求相关权限
                ActivityCompat.requestPermissions(
                    (context as Activity), arrayOf(Manifest.permission.READ_PHONE_STATE),
                    10
                )
                return false
            }
        } catch (e: IllegalArgumentException) {
            return false
        }
        return true
    }

    /**
     * 获取运营商(不推荐，已经有携号转网，并且本身就不准)
     * @param context 上下文对象
     * @return String 运行商名称
     */
    fun getNetOperator(context: Context): String {
        val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return when(manager.simOperator) {
            "46000","46002","46007"->"中国移动"
            "46001","46006","46009"->"中国电信"
            "46003","46005","46011"->"中国联通"
            else -> "未知"
        }
    }

    /**
     * 获取联网方式
     * @param context 上下文对象
     * @return String 联网方式，未知、WIFI、4G、3G、2G、其他
     */
    @SuppressLint("MissingPermission")
    fun getNetMode(context: Context): String {
        var strNetworkType = "未知"
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkInfo = manager.activeNetworkInfo//此处需要权限
        if (networkInfo != null && networkInfo.isConnected) {
            val netMode = networkInfo.type

            //wifi
            if (netMode == ConnectivityManager.TYPE_WIFI) {
                strNetworkType = "WIFI"
            } else if (netMode == ConnectivityManager.TYPE_MOBILE) {
                val networkType = networkInfo.subtype
                strNetworkType = when (networkType) {
                    TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
                    TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
                    TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                    else -> {
                        val strSubTypeName = networkInfo.subtypeName
                        if (strSubTypeName.equals("TD-SCDMA",ignoreCase = true)|| strSubTypeName.equals("WCDMA", ignoreCase = true)|| strSubTypeName.equals("CDMA2000", ignoreCase = true)) {
                            "3G"
                        } else {
                            strSubTypeName
                        }
                    }
                }
            }
        }
        return strNetworkType
    }
}