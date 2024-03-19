package com.silencefly96.module_hardware.camera.helper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraXVideoHelper(
    private var mSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
): ICameraVideoHelper<PreviewView> {

    //
    private var mCameraProvider: ProcessCameraProvider? = null

    // 拍照场景
    private var imageCapture: ImageCapture? = null

    /**
     * 使用CameraX API进行预览
     *
     * @param activity 带lifecycle的activity，提供context，并且便于使用协程
     * @param view Camera API使用的 PreviewView
     */
    override fun startPreview(
        activity: ComponentActivity,
        view: PreviewView
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        cameraProviderFuture.addListener({
            // 用于将相机的生命周期绑定到生命周期所有者
            // 消除了打开和关闭相机的任务，因为 CameraX 具有生命周期感知能力
            mCameraProvider = cameraProviderFuture.get()

            // 预览
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(view.surfaceProvider)
                }

            // 拍照的使用场景
            imageCapture = ImageCapture.Builder()
                .build()

            // 选择摄像头，省去了去判断摄像头ID
            val cameraSelector = mSelector

            try {
                // Unbind use cases before rebinding
                mCameraProvider!!.unbindAll()

                // 将相机绑定到 lifecycleOwner，就不用手动关闭了
                mCameraProvider!!.bindToLifecycle(
                    activity, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Log.e("TAG", "Use case binding failed", exc)
            }

            // 回调代码在主线程处理
        }, ContextCompat.getMainExecutor(activity))
    }

    /**
     * 使用相机API拍视频
     *
     * @param activity 带lifecycle的activity，提供context，并且便于使用协程
     * @param view 使用 PreviewView 拍视频
     * @param callback 结果回调
     */
    override fun takeVideo(
        activity: ComponentActivity,
        view: PreviewView,
        callback: Consumer<String>
    ){

    }

    /**
     * 释放资源
     */
    override fun release() {
        // 取消绑定生命周期观察者
        mCameraProvider?.unbindAll()
    }
}