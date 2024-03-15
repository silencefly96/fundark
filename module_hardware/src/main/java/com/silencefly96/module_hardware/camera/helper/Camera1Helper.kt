package com.silencefly96.module_hardware.camera.helper

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.Camera
import android.media.MediaRecorder
import android.os.Environment
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.core.util.Consumer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import kotlin.math.abs


class Camera1Helper(
    private var mFacingType: Int = Camera.CameraInfo.CAMERA_FACING_BACK
): ICameraHelper<SurfaceView> {

    // 相机
    private var mCamera: Camera? = null

    // 弱引用持有SurfaceView
    private var mSurfaceViewRef: WeakReference<SurfaceView>? = null

    // 录制视频
    private var mMediaRecorder: MediaRecorder? = null

    // 创建surface的回调
    private val mSurfaceCallback = object : SurfaceHolder.Callback {

        override fun surfaceCreated(holder: SurfaceHolder) {
            // 创建预览 Surface (默认使用工具类时已经Created)
            // val previewSurface = holder.surface
            // camera.setPreviewDisplay(previewSurface)
            // camera.startPreview()
        }

        override fun surfaceChanged(holder: SurfaceHolder,
                                    format: Int, width: Int, height: Int) {
            // 调整预览 Surface 的大小或格式(有问题)
            // mCamera?.stopPreview()
            // mCamera?.setPreviewDisplay(holder)
            // mCamera?.startPreview()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            // 释放预览 Surface
            // mCamera?.stopPreview()
            // mCamera?.setPreviewDisplay(null)
        }
    }

    /**
     * 使用Camera API进行预览
     *
     * @param activity 带lifecycle的activity，提供context，并且便于使用协程
     * @param view Camera API使用的 SurfaceView
     */
    override fun startPreview(
        activity: ComponentActivity,
        view: SurfaceView
    ) {
        // 持有SurfaceViewRef，添加回调后要及时清除，避免内存泄漏
        mSurfaceViewRef = WeakReference(view)

        // IO协程中执行，
        activity.lifecycleScope.launch(Dispatchers.IO) {

            // 1、获取后置摄像头ID
            val cameraId = getCameraId(mFacingType)

            // 2、获取相机实例
            if (mCamera == null) {
                mCamera = Camera.open(cameraId)
            }

            // 3、设置和屏幕方向一致
            setCameraDisplayOrientation(activity, mCamera!!, cameraId)

            // 4、设置相机参数
            setCameraParameters(activity, mCamera!!)

            // 5、在startPreview前设置holder(有前提: surfaceCreated已完成)
            // 不要在surfaceCreated设置，不然有问题，使用工具类没法收到surfaceCreated回调
            mCamera!!.setPreviewDisplay(view.holder)

            // 6、设置SurfaceHolder回调
            view.holder.addCallback(mSurfaceCallback)

            // 7、开始预览
            mCamera!!.startPreview()
        }
    }

    /**
     * 继续预览，camera1 APU拍照后会暂停预览
     */
    fun continuePreview() {
        mCamera?.startPreview()
    }

    /** 获取前置或者后置摄像头 **/
    @Suppress("SameParameterValue")
    private fun getCameraId(facing: Int): Int {
        // 后置: Camera.CameraInfo.CAMERA_FACING_BACK
        // 前置: Camera.CameraInfo.CAMERA_FACING_FRONT
        val numberOfCameras = Camera.getNumberOfCameras()
        val info = Camera.CameraInfo()
        for (cameraId in 0 until numberOfCameras) {
            Camera.getCameraInfo(cameraId, info)
            if (info.facing == facing) {
                return cameraId
            }
        }
        return -1 // 未找到符合条件的摄像头
    }

    private fun setCameraParameters(activity: ComponentActivity, camera: Camera) {
        val params = camera.parameters

        // 设置图像格式
        params.previewFormat = ImageFormat.NV21

        // 设置预览尺寸
        val previewSize = getOptimalPreviewSize(params.supportedPreviewSizes,
            activity.resources.displayMetrics.widthPixels,
            activity.resources.displayMetrics.heightPixels)
        params.setPreviewSize(previewSize.width, previewSize.height)

        // 设置图片尺寸
        val pictureSize = getOptimalPictureSize(params.supportedPictureSizes,
            activity.resources.displayMetrics.widthPixels,
            activity.resources.displayMetrics.heightPixels)
        params.setPictureSize(pictureSize.width, pictureSize.height)

        // 设置对焦模式
        params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE

        // 设置闪光灯模式
        params.flashMode = Camera.Parameters.FLASH_MODE_AUTO

        // 设置场景模式
        params.sceneMode = Camera.Parameters.SCENE_MODE_AUTO

        // 应用参数设置
        camera.parameters = params
    }

    private fun getOptimalPreviewSize(sizes: List<Camera.Size>, w: Int, h: Int): Camera.Size {
        val targetRatio = w.toDouble() / h
        return sizes.minByOrNull { abs(it.width.toDouble() / it.height - targetRatio) } ?: sizes[0]
    }

    private fun getOptimalPictureSize(sizes: List<Camera.Size>, w: Int, h: Int): Camera.Size {
        val targetRatio = w.toDouble() / h
        return sizes.maxByOrNull { it.width.coerceAtMost(it.height) } ?: sizes[0]
    }

    private fun setCameraDisplayOrientation(activity: Activity, camera: Camera, cameraId: Int) {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, info)
        val rotation = activity.windowManager?.defaultDisplay?.rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }

        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360 // 前置摄像头需要镜像旋转
        } else {
            result = (info.orientation - degrees + 360) % 360
        }

        camera.setDisplayOrientation(result)
    }

    /**
     * 使用相机API无感拍照，没有预览、静音
     *
     * @param activity 带lifecycle的activity，提供context，并且便于使用协程
     * @param view SurfaceView
     * @param callback 结果回调
     */
    fun takePhotoNoFeeling (
        activity: ComponentActivity,
        view: SurfaceView,
        callback: Consumer<Bitmap>
    ) {
        // 设置surface大小为1dp
        view.layoutParams.apply {
            width = 1
            height = 1
            view.layoutParams = this
        }

        // 静音拍摄
        mCamera!!.enableShutterSound(false)

        // 拍照
        takePhoto(activity, view, callback)
    }

    /**
     * 使用相机API拍照
     *
     * @param activity 带lifecycle的activity，提供context，并且便于使用协程
     * @param view SurfaceView
     * @param callback 结果回调
     */
    override fun takePhoto(
        activity: ComponentActivity,
        view: SurfaceView,
        callback: Consumer<Bitmap>
    ) {
        // camera1 API需要先预览才能拍照
        if (mCamera == null) {
            throw IllegalStateException("camera not prepared!!!")
        }

        // IO协程中执行，
        activity.lifecycleScope.launch(Dispatchers.IO) {
            mCamera!!.takePicture(null, null) { data, _ ->

                // 处理拍照结果
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                val cameraInfo = Camera.CameraInfo()
                Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, cameraInfo)
                val rotation = cameraInfo.orientation

                // 将结果投递到UI线程
                activity.lifecycleScope.launch(Dispatchers.Main) {
                    callback.accept(rotateBitmap(bitmap, rotation))
                }
            }
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * 使用相机API拍视频
     *
     * @param activity 带lifecycle的activity，提供context，并且便于使用协程
     * @param view 使用 SurfaceView 拍视频
     * @param callback 结果回调
     */
    override fun takeVideo(
        activity: ComponentActivity,
        view: SurfaceView,
        callback: Consumer<String>
    ){
        // 创建一个 MediaRecorder 对象
        if (mMediaRecorder == null) {
            mMediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                setVideoSource(MediaRecorder.VideoSource.CAMERA)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
        }

        try {
            // 设置 MediaRecorder 的属性
            mMediaRecorder?.apply {

                // 设置输出文件路径
                setOutputFile(getTempVideoPath(activity).absolutePath)

                // 准备 MediaRecorder
                prepare()

                // 开始录制
                start()
            }
            
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun getTempVideoPath(activity: ComponentActivity): File {
        // 临时文件，后面会加long型随机数
        return File.createTempFile(
            "video_",
            ".mp4",
            activity.getExternalFilesDir(Environment.DIRECTORY_DCIM)
        )
    }

    /**
     * 释放资源
     */
    override fun release() {
        mSurfaceViewRef?.get()?.holder?.removeCallback(mSurfaceCallback)
        mCamera?.stopPreview()
        mCamera?.release()
        mCamera = null
    }
}