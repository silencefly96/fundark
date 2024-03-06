package com.silencefly96.module_hardware.camera.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
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
import androidx.lifecycle.LifecycleOwner

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraXHelper {

    // 先开预览再拍照(camera2实际不需要这个流程)
    private var isInit = false

    // 拍照场景
    private var imageCapture: ImageCapture? = null

    fun takePhotoByCameraX(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        consumer: Consumer<Bitmap>
    ) {
        if (!isInit) {
            // 打开相机，开始预览
            startCamera(context, lifecycleOwner, previewView)
            isInit = true
        }else {
            takePhoto(context, consumer)
        }
    }

    private fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            // 用于将相机的生命周期绑定到生命周期所有者
            // 消除了打开和关闭相机的任务，因为 CameraX 具有生命周期感知能力
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // 预览
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // 拍照的使用场景
            imageCapture = ImageCapture.Builder()
                .build()

            // 选择摄像头，省去了去判断摄像头ID
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // 将相机绑定到 lifecycleOwner，就不用手动关闭了
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Log.e("TAG", "Use case binding failed", exc)
            }

        // 回调代码在主线程处理
        }, ContextCompat.getMainExecutor(context))
    }

    private fun takePhoto(context: Context, consumer: Consumer<Bitmap>) {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // 直接拍照拿bitmap，存文件可以用 OutputFileOptions
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // 转换为 Bitmap，并传递结果
                    consumer.accept(imageProxyToBitmap(image))
                    image.close()
                }

                override fun onError(exc: ImageCaptureException) {
                    // 处理拍摄过程中的异常
                    Log.e("TAG", "Photo capture failed: ${exc.message}", exc)
                }
            }
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy = image.planes[0]
        val buffer = planeProxy.buffer

        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

}