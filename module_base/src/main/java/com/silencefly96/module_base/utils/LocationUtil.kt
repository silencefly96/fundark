package com.silencefly96.module_base.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.text.TextUtils
import androidx.core.app.ActivityCompat

/**
 * 位置获取的工具类，包含GPS、网络及系统最有位置获取
 * 推荐使用 addLocationListener 方法，通过监听获取位置
 * @author: fdk
 * @data: 2021/7/9
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
object LocationUtil {

    /**
     * 定位监听间隔，5秒
     */
    private const val REFRESH_TIME = 5000L

    /**
     * 定位精度，10米
     */
    private const val METER_POSITION = 10.0f

    /**
     * 内部使用，用来接收系统的位置变化
     */
    private var locationCallback: LocationListener = MyLocationListener()

    /**
     * 用户自定义 -- 定位改变监听回调接口，可获取 Location
     */
    private var mLocationListener: ILocationListener? = null

    /**
     * GPS获取定位方式
     * @param context 上下文
     * @return GPS位置
     */
    fun getGPSLocation(context: Context) = getLocation(context, LocationManager.GPS_PROVIDER)

    /**
     * 网络获取定位方式
     * @param context 上下文
     * @return 网络位置
     */
    fun getNetWorkLocation(context: Context) =
        getLocation(context, LocationManager.NETWORK_PROVIDER)

    /**
     * 获取定位
     * @param context 上下文
     * @param type 类型，LocationManager.NETWORK_PROVIDER，LocationManager.GPS_PROVIDER
     * @return 位置
     */
    @SuppressLint("MissingPermission")
    fun getLocation(context: Context, type: String): Location? {
        var location: Location? = null

        //高版本的权限检查
        val permission = if(LocationManager.NETWORK_PROVIDER == type) {
            Manifest.permission.ACCESS_COARSE_LOCATION
        }else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (ActivityCompat.checkSelfPermission(context, permission)
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val manager = getLocationManager(context)
        //是否支持Network定位
        if (manager.isProviderEnabled(type)) {
            //获取最后的 GPS/网络 定位信息，如果是第一次打开，一般会拿不到定位信息，
            //一般可以请求监听，在有效的时间范围可以获取定位信息
            location = manager.getLastKnownLocation(type)
        }
        return location
    }

    /**
     * 获取最好的定位方式
     * @param context 上下文对象
     * @param criteria 一组筛选条件，可参考 https://www.jianshu.com/p/755170c47164
     * @return 位置
     */
    @SuppressLint("MissingPermission")
    fun getBestLocation(context: Context, criteria: Criteria = Criteria()): Location? {
        val manager = getLocationManager(context)
        //根据系统自动判断最佳方式
        val provider = manager.getBestProvider(criteria, true)
        return if (TextUtils.isEmpty(provider)) {
            //如果找不到最适合的定位，使用network定位
            getNetWorkLocation(context)
        } else {
            //高版本的权限检查
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                != PackageManager.PERMISSION_GRANTED
            ) {
                return null
            }
            //获取最适合的定位方式的最后的定位权限
            manager.getLastKnownLocation(provider!!)
        }
    }

    /**
     * 添加定位监听回调接口
     * @param context 上下文对象
     * @param provider 类型，LocationManager.NETWORK_PROVIDER，LocationManager.GPS_PROVIDER
     * @param locationListener 监听位置变化回调接口
     */
    fun addLocationListener(
        context: Context,
        provider: String,
        locationListener: ILocationListener
    ) {
        addLocationListener(context, provider, REFRESH_TIME, METER_POSITION, locationListener)
    }

    /**
     * 添加定位监听回调接口
     * @param context 上下文对象
     * @param provider 类型，LocationManager.NETWORK_PROVIDER，LocationManager.GPS_PROVIDER
     * @param time 定位扫描时间，单位毫秒
     * @param meter 定位精度，单位米
     * @param locationListener 监听位置变化回调接口
     */
    @SuppressLint("MissingPermission")
    fun addLocationListener(
        context: Context,
        provider: String,
        time: Long,
        meter: Float,
        locationListener: ILocationListener
    ) {
        //判断权限
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        //设置用户监听接口
        mLocationListener = mLocationListener ?: locationListener

        //注册定位更新监听
        val manager = getLocationManager(context)
        manager.requestLocationUpdates(provider, time, meter, locationCallback)
    }

    /**
     * 取消定位监听
     * @param context 上下文对象
     */
    @SuppressLint("MissingPermission")
    fun unRegisterListener(context: Context) {
        //权限判断
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&

            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        //移除定位监听
        val manager = getLocationManager(context)
        manager.removeUpdates(locationCallback)
    }

    /**
     * 获取 LocationManager
     */
    private fun getLocationManager(context: Context): LocationManager {
        return context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    /**
     * LocationManager需要的监听回调接口
     */
    private class MyLocationListener : LocationListener {

        /**
         * 定位改变监听
         */
        override fun onLocationChanged(location: Location) {
            if (mLocationListener != null) {
                mLocationListener!!.onSuccessLocation(location)
            }
        }

        /**
         * 定位状态监听
         */
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}

        /**
         * 定位状态可用监听
         */
        override fun onProviderEnabled(provider: String) {}

        /**
         * 定位状态不可用监听
         */
        override fun onProviderDisabled(provider: String) {}
    }

    /**
     * 自定义接口，用于监听位置变化
     */
    interface ILocationListener {

        /**
         * 获取位置成功
         * @param location 位置
         */
        fun onSuccessLocation(location: Location?)
    }
}