package com.silencefly96.module_base.base

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.ArrayList

/**
 * 申请权限辅助接口
 *
 * 在 BaseActivity 和 BaseFragment 中使用，携带了请求和回复权限的功能，
 * onRunTimePermissionResult 需要在BaseActivity 和 BaseFragment 中调用下
 *
 * @author fdk
 * @date 2021/07/16
 */
interface IPermissionHelper {

    /**
     * 私有权限接口
     */
    var mPermissionListener: PermissionListener?

    /**
     * 权限申请
     *
     * @param activity 活动
     * @param permissions 权限
     * @param listener 监听接口
     */
    fun requestRunTimePermission(
        activity: Activity,
        permissions: Array<String>,
        listener: PermissionListener
    ) {
        mPermissionListener = listener

        //未同意的权限
        val permissionList: MutableList<String> = ArrayList()

        //遍历要申请的权限
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                != PackageManager.PERMISSION_GRANTED) {
                //未同意的权限加到列表
                permissionList.add(permission)
            }
        }

        if (permissionList.isNotEmpty()) {
            //申请权限
            ActivityCompat.requestPermissions(activity,
                permissionList.toTypedArray(), PERMISSION_REQUEST_CODE
            )
        } else {
            //全部权限已同意
            listener.onGranted()
        }
    }

    /**
     * 申请结果
     *
     * @param requestCode 请求码
     * @param permissions 权限组
     * @param grantResults 结果
     */
    fun onRunTimePermissionResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> if (grantResults.isNotEmpty()) {

                val deniedPermissions: MutableList<String> = ArrayList()
                val grantedPermissions: MutableList<String> = ArrayList()

                var i = 0
                while (i < grantResults.size) {
                    val grantResult = grantResults[i]
                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        //通过的权限
                        val permission = permissions[i]
                        deniedPermissions.add(permission)
                    } else {
                        //未通过权限
                        val permission = permissions[i]
                        grantedPermissions.add(permission)
                    }
                    i++
                }

                if (deniedPermissions.isEmpty()) {
                    //全部通过
                    mPermissionListener!!.onGranted()
                } else {
                    //部分通过
                    mPermissionListener!!.onDenied(deniedPermissions)
                    mPermissionListener!!.onGranted(grantedPermissions)
                }
            }
        }
    }

    companion object{

        /**
         * 申请权限时用到的请求码
         */
        const val PERMISSION_REQUEST_CODE: Int = 128
    }

    /**
     * 权限接口
     */
    interface PermissionListener {

        /**
         * 全部授权成功
         */
        fun onGranted()

        /**
         * 授权成功部分
         */
        fun onGranted(grantedPermission: List<String>?)

        /**
         * 授权拒绝部分
         */
        fun onDenied(deniedPermission: List<String>?)
    }
}