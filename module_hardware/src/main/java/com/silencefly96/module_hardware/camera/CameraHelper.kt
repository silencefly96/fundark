package com.silencefly96.module_hardware.camera

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.Camera
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.core.util.Consumer

class CameraHelper {

    private var mCamera: Camera? = null

    private var mSurfaceCallback: SurfaceHolder.Callback? = null

    public fun takePhotoByCamera(
        activity: Activity,
        surface: SurfaceView,
        callback: Consumer<Bitmap>,
        sound: Boolean = true
    ) {
        if (mCamera == null) {
            initCamera(activity, surface)
        }else {
            var rotateBitmap: Bitmap? = null
            mCamera!!.startPreview()
            mCamera!!.enableShutterSound(sound)
            mCamera!!.takePicture(null, null, Camera.PictureCallback { data, _ ->
                // 处理拍照结果
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                val cameraInfo = Camera.CameraInfo()
                Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, cameraInfo)
                val rotation = cameraInfo.orientation
                callback.accept(rotateBitmap(bitmap, rotation))
            })
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    public fun takePhotoNoFeeling (
        activity: Activity,
        surface: SurfaceView,
        callback: Consumer<Bitmap>
    ) {
        // 设置surface大小为1dp
        surface.layoutParams.apply {
            width = 1
            height = 1
            surface.layoutParams = this
        }
        // 静音拍摄
        takePhotoByCamera(activity, surface, callback, false)
    }

    private fun initCamera(activity: Activity, surface: SurfaceView) {
        // surface可见时才能使用相机
        surface.visibility = View.VISIBLE
        Thread {
            // 获取后置摄像头ID
            val cameraId = getCameraId(Camera.CameraInfo.CAMERA_FACING_BACK)
            // 获取相机实例
            mCamera = Camera.open(cameraId)
            // 设置和屏幕方向一致
            setCameraDisplayOrientation(activity, mCamera!!, cameraId)
            // 设置holder，不要在surfaceCreated设置，不然有问题
            mCamera!!.setPreviewDisplay(surface.holder)
            // 设置回调
            surface.holder.addCallback(object : SurfaceHolder.Callback {

                override fun surfaceCreated(holder: SurfaceHolder) {}

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {}

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    mCamera!!.stopPreview()
                    mCamera!!.release()
                }
            })
            // 开始预览
            mCamera!!.startPreview()
        }.start()
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
}