package com.silencefly96.module_hardware.camera.helper

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
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
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class Camera2CaptureHelper(
    private var mFacingType: Int = CameraCharacteristics.LENS_FACING_BACK
): ICameraCaptureHelper<TextureView> {

    // 用于处理相机结果的handler(主线程handler)
    private val mHandler = Handler(Looper.getMainLooper())

    // TextureView的弱引用
    private var mTextureViewRef: WeakReference<TextureView>? = null

    // 摄像头ID
    private var mCameraId: String? = null

    // 摄像头信息
    private var mCameraCharacteristics: CameraCharacteristics? = null

    // 相机最大尺寸
    private var mLargestSize: Size? = null

    // TextureView回调
    private var mTextureViewCallback =
        object : TextureView.SurfaceTextureListener {

            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture, width: Int, height: Int
            ) {
                // 应该在这开启相机并创建对话的，使用工具类不会收到这个回调，默认已经Available了
                // val previewSurface = Surface(surfaceTexture)
                // cameraDevice.createCaptureSession(...)
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture, width: Int, height: Int
            ) {

            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                Log.d("TAG", "onSurfaceTextureDestroyed:")
                // 在textureView销毁的时候关闭(或者手动关闭)
                mCameraDevice?.close()
                mSession?.close()
                mImageReader?.close()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }

    // 用于读取图片
    private var mImageReader: ImageReader? = null

    // 相机
    private var mCameraDevice: CameraDevice? = null

    // 会话
    private var mSession: CameraCaptureSession? = null

    // 预览请求
    private var mPreviewRequest: CaptureRequest? = null

    // 拍照请求
    private var mCaptureRequest: CaptureRequest? = null

    /**
     * 使用Camera2 API进行预览
     *
     * @param activity 带lifecycle的activity，提供context，并且便于使用协程
     * @param view Camera API2使用的 TextureView(当然也能用SurfaceView)
     */
    override fun startPreview(
        activity: ComponentActivity,
        view: TextureView
    ) {
        // 持有TextureView的弱引用，便于释放资源
        mTextureViewRef = WeakReference(view)

        // IO协程中执行，
        activity.lifecycleScope.launch(Dispatchers.IO) {

            // 1、获取CameraManager
            val cameraManager = ContextCompat.getSystemService(activity, CameraManager::class.java)
                ?: throw IllegalStateException("get cameraManager fail")

            // 2、获取摄像头mCameraId、摄像头信息mCameraCharacteristics
            chooseCameraIdByFacing(mFacingType, cameraManager)

            // 3、开启相机
            mCameraDevice = openCamera(cameraManager)

            // 4、设置如何读取图片的ImageReader
            mImageReader = getImageReader()

            // 5.创建Capture Session
            val surface = getSurface(view)
            mSession = startCaptureSession(mutableListOf(
                // 注意一定要传入使用到的surface，不然会闪退
                surface,
                mImageReader!!.surface
            ),  mCameraDevice!!)

            // 6.设置textureView回调，destroy时释放资源
            view.surfaceTextureListener = mTextureViewCallback

            // 7.开始预览，预览和拍照都用request实现
            preview(surface)
        }
    }

    private fun getSurface(view: TextureView): Surface {
        view.surfaceTexture?.setDefaultBufferSize(mLargestSize!!.width, mLargestSize!!.height)
        return Surface(view.surfaceTexture)
    }

    @Suppress("SameParameterValue")
    private fun chooseCameraIdByFacing(facing: Int, cameraManager: CameraManager) {
        val cameraIdList = cameraManager.cameraIdList
        cameraIdList.forEach { cameraId ->
            // 主要用于获取相机信息，内部携带大量的信息
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
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

        // 获取最大尺寸
        mCameraCharacteristics?.let { info ->
            val map = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: throw IllegalStateException("Cannot get available preview/video sizes")

            val largest = map.getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.width * it.height }

            mLargestSize = largest ?: throw IllegalStateException("Cannot get largest preview size")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(cameraManager: CameraManager) = suspendCoroutine<CameraDevice> {
        cameraManager.openCamera(mCameraId!!, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                Log.d("TAG", "onOpened:")
                // 表示相机打开成功，可以真正开始使用相机，创建Capture会话
                it.resume(camera)
            }

            override fun onDisconnected(camera: CameraDevice) {
                Log.d("TAG", "openCamera onDisconnected:")
                // 当相机断开连接时回调该方法，需要进行释放相机的操作
                // camera.close()
                // mCameraDevice可能是另一个了，别在这里设置为null
                // mCameraDevice = null
                // it.resumeWithException(Exception("onDisconnected"))
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.d("TAG", "openCamera onError: $error")
                // 当相机打开失败时，需要进行释放相机的操作
                camera.close()
                // mCameraDevice可能是另一个了，别在这里设置为null
                // mCameraDevice = null
                it.resumeWithException(Exception("onError: $error"))
            }
        }, mHandler)
    }


    private fun getImageReader(): ImageReader {
        // ImageReader是获取图像数据的重要途径
        return ImageReader.newInstance(
            mLargestSize!!.width,
            mLargestSize!!.height,
            ImageFormat.JPEG,
            // Image对象池的大小，指定了能从ImageReader获取Image对象的最大值，过多获取缓冲区可能导致OOM，
            // 所以最好按照最少的需要去设置这个值
            2
        )
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

    private fun preview(surface: Surface) {
        // 通过模板创建RequestBuilder
        // CaptureRequest还可以配置很多其他信息，例如图像格式、图像分辨率、传感器控制、闪光灯控制、
        // 3A(自动对焦-AF、自动曝光-AE和自动白平衡-AWB)控制等
        val previewRequestBuilder =
            mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

        // 设置预览画面
        previewRequestBuilder.addTarget(surface)

        mPreviewRequest = previewRequestBuilder.build()
        mSession!!.setRepeatingRequest(mPreviewRequest!!, null, mHandler)
    }

    /**
     * 使用相机API拍照
     *
     * @param activity 带lifecycle的activity，提供context，并且便于使用协程
     * @param view Camera2 API使用的 TextureView(当然也能用SurfaceView)
     * @param callback 结果回调
     */
    override fun takePhoto (
        activity: ComponentActivity,
        view: TextureView,
        callback: Consumer<Bitmap>
    ) {
        // IO协程中执行，
        activity.lifecycleScope.launch(Dispatchers.IO) {

            // 1、创建拍照的请求
            val captureRequestBuilder =
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)

            // 2、设置参数
            captureRequestBuilder.addTarget(mImageReader!!.surface)
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

            // 3、设置拍照方向
            val rotation = activity.windowManager.defaultDisplay.rotation
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                getJpegOrientation(mCameraCharacteristics!!, rotation))

            mCaptureRequest = captureRequestBuilder.build()

            // 4、拍照
            // mSession?.stopRepeating() // 这行代码只是为了防止重复请求
            // mSession?.abortCaptures() // 这行代码只是为了防止重复请求
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

            // 5、设置图片回调，拿到结果
            setImageReaderCallback(callback)
        }
    }

    private fun setImageReaderCallback(callback: Consumer<Bitmap>) {
        mImageReader?.setOnImageAvailableListener({
            val image = mImageReader!!.acquireNextImage()
            image?.use {
                val planes = it.planes
                if (planes.isNotEmpty()) {
                    val buffer = planes[0].buffer
                    val data = ByteArray(buffer.remaining())
                    buffer.get(data)

                    // 转成bitmap
                    val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)

                    // 传递结果，mHandler应该是在UI线程了
                    callback.accept(bitmap)
                }
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

    override fun release() {
        // 从 SurfaceTexture 中移除 SurfaceTextureListener
        mTextureViewRef?.get()?.surfaceTextureListener = null
        // 需要关闭这三个
        mCameraDevice?.close()
        mSession?.close()
        mImageReader?.close()
        mHandler.removeCallbacksAndMessages(null)
    }
}