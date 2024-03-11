package com.silencefly96.module_hardware.camera.photo

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.core.util.Consumer

interface ICameraHelper<in T> {

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
     * 使用相机API拍照
     *
     * @param activity 带lifecycle的activity，提供context，并且便于使用协程
     * @param view 根据不同的API可能传入不同的预览页面: SurfaceView、TextureView、PreviewView
     * @param callback 结果回调
     */
    fun takePhoto(
        activity: ComponentActivity,
        view: T,
        callback: Consumer<Bitmap>
    )

    /**
     * 释放资源
     */
    fun release()
}