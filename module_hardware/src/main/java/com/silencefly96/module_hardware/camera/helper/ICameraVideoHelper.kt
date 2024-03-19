package com.silencefly96.module_hardware.camera.helper

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.core.util.Consumer

interface ICameraVideoHelper<in T> {

    /**
     * 使用相机API开始预览
     *
     * @param activity 带lifecycle的activity，提供context，并且便于使用协程
     * @param view 根据不同的API可能传入不同的预览页面: SurfaceView、TextureView、PreviewView
     */
    fun startPreview(
        activity: ComponentActivity,
        view: T
    )

    /**
     * 使用相机API 拍视频
     *
     * @param activity 带lifecycle的activity，提供context，并且便于使用协程
     * @param view 根据不同的API可能传入不同的预览页面: SurfaceView、TextureView、PreviewView
     * @param callback 结果回调
     */
    fun takeVideo(
        activity: ComponentActivity,
        view: T,
        callback: Consumer<String>
    )

    /**
     * 释放资源
     */
    fun release()
}