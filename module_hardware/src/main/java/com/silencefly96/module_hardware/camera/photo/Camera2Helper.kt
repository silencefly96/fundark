package com.silencefly96.module_hardware.camera.photo

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class Camera2Helper {

    private var isInit = false

    private lateinit var mCameraManager: CameraManager

    // 摄像头ID
    private var mCameraId: String? = null

    // 摄像头信息
    private var mCameraCharacteristics: CameraCharacteristics? = null

    // 用于处理相机结果的handler(主线程handler)
    private val mHandler = Handler(Looper.getMainLooper())

        // 用于读取图片
    private var mImageReader: ImageReader? = null

    // 相机
    private var mCamera: CameraDevice? = null

    private var mSession: CameraCaptureSession? = null

    private var mPreviewRequest: CaptureRequest? = null

    private var mCaptureRequest: CaptureRequest? = null

    fun takePhotoByCamera2(context: Activity, lifecycle: Lifecycle, surfaceView: SurfaceView, consumer: Consumer<Bitmap>) {
        // 通过协程在异步线程执行，还方便将回调改成直接返回
        lifecycle.coroutineScope.launch(Dispatchers.IO) {
            if (!isInit) {
                // 1、获取CameraManager
                mCameraManager = ContextCompat.getSystemService(context, CameraManager::class.java)!!

                // 2、获取摄像头相关信息，使用后置摄像头
                chooseCameraIdByFacing(CameraCharacteristics.LENS_FACING_BACK)

                // 3、设置如何读取图片的ImageReader
                mImageReader = getImageReader(surfaceView, consumer)

                // 4、开启相机
                mCamera = openCamera()

                // 5.创建Capture Session
                mSession = startCaptureSession(mutableListOf(
                    // 注意一定要传入使用到的surface，不然会闪退
                    surfaceView.holder.surface,
                    mImageReader!!.surface
                ),  mCamera!!)

                // 6.开始预览，预览和拍照都用request实现
                startPreview(surfaceView.holder.surface)

                // 7.关闭相机，在surfaceView销毁的时候关闭(或者手动关闭)
                surfaceView.holder.addCallback(object : SurfaceHolder.Callback {

                    override fun surfaceCreated(holder: SurfaceHolder) {}

                    override fun surfaceChanged(
                        holder: SurfaceHolder, format: Int, width: Int, eight: Int ) {}

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        closeAll()
                    }
                })

                isInit = true
            }else {
                // 8.拍照
                takePhoto(context)
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun chooseCameraIdByFacing(facing: Int) {
        val cameraIdList = mCameraManager.cameraIdList
        cameraIdList.forEach { cameraId ->
            // 主要用于获取相机信息，内部携带大量的信息
            val characteristics = mCameraManager.getCameraCharacteristics(cameraId)
            val level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            if (level != null &&
                level != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {

                val internal = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (internal == facing) {
                    mCameraId = cameraId
                    mCameraCharacteristics = characteristics
                }
            }
        }
    }

    private fun getImageReader(surfaceView: SurfaceView, consumer: Consumer<Bitmap>): ImageReader {
        // ImageReader是获取图像数据的重要途径
        val imageReader = ImageReader.newInstance(
            surfaceView.width,
            surfaceView.height,
            ImageFormat.JPEG,
            // Image对象池的大小，指定了能从ImageReader获取Image对象的最大值，过多获取缓冲区可能导致OOM，
            // 所以最好按照最少的需要去设置这个值
            2
        )
        // 设置回调
        imageReader.setOnImageAvailableListener({
            val image = imageReader.acquireNextImage()
            image?.use {
                val planes = it.planes
                if (planes.isNotEmpty()) {
                    val buffer = planes[0].buffer
                    val data = ByteArray(buffer.remaining())
                    buffer.get(data)

                    // 转成bitmap
                    val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                    consumer.accept(bitmap)
                }
            }
        }, mHandler)
        return imageReader
    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera() = suspendCoroutine<CameraDevice> {
        mCameraManager.openCamera(mCameraId!!, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                // 表示相机打开成功，可以真正开始使用相机，创建Capture会话
                it.resume(camera)
            }

            override fun onDisconnected(camera: CameraDevice) {
                // 当相机断开连接时回调该方法，需要进行释放相机的操作
                mCamera?.close()
                mCamera = null
                it.resumeWithException(Exception("onDisconnected"))
            }

            override fun onError(camera: CameraDevice, error: Int) {
                // 当相机打开失败时，需要进行释放相机的操作
                mCamera?.close()
                mCamera = null
                it.resumeWithException(Exception("onError: $error"))
            }
        }, mHandler)
    }

    private suspend fun startCaptureSession(outputs: List<Surface> ,cameraDevice: CameraDevice)
        = suspendCoroutine<CameraCaptureSession> {
            cameraDevice.createCaptureSession(outputs, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    it.resume(session)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    it.resumeWithException(Exception("onConfigureFailed"))
                }
            }, mHandler)
    }

    private fun startPreview(surface: Surface) {
        // 通过模板创建RequestBuilder
        // CaptureRequest还可以配置很多其他信息，例如图像格式、图像分辨率、传感器控制、闪光灯控制、
        // 3A(自动对焦-AF、自动曝光-AE和自动白平衡-AWB)控制等
        val previewRequestBuilder =
            mCamera!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

        // 设置预览画面
        previewRequestBuilder.addTarget(surface)

        mPreviewRequest = previewRequestBuilder.build()
        mSession!!.setRepeatingRequest(mPreviewRequest!!, null, mHandler)
    }

    private fun takePhoto(activity: Activity) {
        // 创建拍照的请求
        val captureRequestBuilder =
            mCamera!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)

        // 设置参数
        captureRequestBuilder.addTarget(mImageReader!!.surface)
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

        // 设置拍照方向
        val rotation = activity.windowManager.defaultDisplay.rotation
        captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,
            getJpegOrientation(mCameraCharacteristics!!, rotation))

        mCaptureRequest = captureRequestBuilder.build()

        // 拍照
        mSession!!.stopRepeating()
        mSession!!.capture(mCaptureRequest!!, object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                // 图片已捕获
                // 可选步骤，根据需要进行处理
            }
        }, mHandler)
    }

    private fun getJpegOrientation(
        cameraCharacteristics: CameraCharacteristics,
        deviceOrientation: Int
    ): Int {
        val sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)

        // 根据设备方向调整传感器方向
        val deviceRotation = when (deviceOrientation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        // 计算最终的JPEG方向
        return (sensorOrientation!! + deviceRotation + 360) % 360
    }

    fun closeAll() {
        // 需要关闭这三个
        mCamera?.close()
        mSession?.close()
        mImageReader?.close()
        mHandler.removeCallbacksAndMessages(null)
        isInit = false
    }
}