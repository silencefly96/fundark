package com.silencefly96.module_hardware.camera

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.core.util.Consumer
import androidx.lifecycle.coroutineScope
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_base.base.IPermissionHelper
import com.silencefly96.module_base.utils.BitmapFileUtil
import com.silencefly96.module_hardware.databinding.FragmentTakePhotoBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.*


class TakePhotoFragment : BaseFragment() {

    companion object{
        const val REQUEST_CAMERA_CODE = 1
        const val REQUEST_ALBUM_CODE = 2
        const val REQUEST_CROP_CODE = 3

        const val MAX_WIDTH = 480
        const val MAX_HEIGHT = 720
    }

    private var _binding: FragmentTakePhotoBinding? = null
    private val binding get() = _binding!!

    // 拍照路径
    private var pictureUri: Uri? = null

    // 裁切路径
    private var cropPicUri: Uri? = null

    // 启用裁切
    private var enableCrop: Boolean = true

    private var mCamera: Camera? = null

    private var mSurfaceCallback: SurfaceHolder.Callback? = null

    // 绑定布局
    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentTakePhotoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        binding.takePhoto.setOnClickListener {
            requestPermission { openCamera() }
        }

        binding.pickPhoto.setOnClickListener {
            openAlbum()
        }

        binding.takePhotoByCamera.setOnClickListener {
            takePhotoByCamera()
        }

        binding.takePhotoNoFeeling.setOnClickListener {
            takePhotoNoFeeling()
        }

        binding.insertPictures.setOnClickListener {
            insert2Pictures(requireContext())
        }

        binding.clearCache.setOnClickListener {
            clearCachePictures(requireContext())
        }

        binding.clearPictures.setOnClickListener {
            clearAppPictures(requireContext())
        }

        binding.cropSwitch.setOnCheckedChangeListener { _, isChecked -> enableCrop = isChecked}

//        initCamera()
    }

    private fun requestPermission(consumer: Consumer<Boolean>) {
        // 动态申请权限，使用的外部私有目录无需申请权限
        requestRunTimePermission(requireActivity(), arrayOf(
            Manifest.permission.CAMERA,
//            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ),
            object : IPermissionHelper.PermissionListener {
                override fun onGranted() {
                    consumer.accept(true)
                }

                override fun onGranted(grantedPermission: List<String>?) {
                    consumer.accept(false)
                }

                override fun onDenied(deniedPermission: List<String>?) {
                    consumer.accept(false)
                }
            })
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // 应用外部私有目录：files-Pictures
        val photoUri = BitmapFileUtil.getUriForAppPictures(requireContext(), "Camera")
        // 保存uri
        pictureUri = photoUri
        // 给目标应用一个临时授权
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        //android11以后强制分区存储，外部资源无法访问，所以添加一个输出保存位置，然后取值操作
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        startActivityForResult(intent, REQUEST_CAMERA_CODE)
    }

    private fun openAlbum() {
        val intent = Intent()
        intent.type = "image/*"
        // GET_CONTENT不允许使用裁切，只能简单的读取数据
        // intent.action = "android.intent.action.GET_CONTENT"
        // OPEN_DOCUMENT允许对文件进行编辑，并且权限是长期有效的，先用OPEN_DOCUMENT再GET_CONTENT也是有权限的
        intent.action = "android.intent.action.OPEN_DOCUMENT"
        intent.addCategory("android.intent.category.OPENABLE")
        startActivityForResult(intent, REQUEST_ALBUM_CODE)
    }

    private fun initCamera() {
        // surface可见时才能使用相机
        binding.surface.visibility = View.VISIBLE
        lifecycle.coroutineScope.launch(Dispatchers.IO) {
            // 获取后置摄像头ID
            val cameraId = getCameraId(Camera.CameraInfo.CAMERA_FACING_BACK)
            // 获取相机实例
            mCamera = Camera.open(cameraId)
            // 设置和屏幕方向一致
            setCameraDisplayOrientation(mCamera!!, cameraId)
            // 设置holder，不要在surfaceCreated设置，不然有问题
            mCamera!!.setPreviewDisplay(binding.surface.holder)
            // 设置回调
            mSurfaceCallback = object : SurfaceHolder.Callback {

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
            }
            binding.surface.holder.addCallback(mSurfaceCallback)
            // 开始预览
            mCamera!!.startPreview()
        }
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

    private fun setCameraDisplayOrientation(camera: Camera, cameraId: Int) {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, info)
        val rotation = activity?.windowManager?.defaultDisplay?.rotation
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

    private fun takePhotoByCamera(sound: Boolean = true) {
        if (mCamera == null) {
            initCamera()
        }else {
            mCamera!!.startPreview()
            mCamera!!.enableShutterSound(sound)
            mCamera!!.takePicture(null, null, Camera.PictureCallback { data, _ ->
                // 处理拍照结果
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                val cameraInfo = Camera.CameraInfo()
                Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, cameraInfo)
                val rotation = cameraInfo.orientation
                val rotatedBitmap = rotateBitmap(bitmap, rotation)
                binding.image.setImageBitmap(rotatedBitmap)
                binding.image.bringToFront()
            })
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun takePhotoNoFeeling() {
        // 设置surface大小为1dp
        binding.surface.layoutParams.apply {
            width = 1
            height = 1
            binding.surface.layoutParams = this
        }
        // 静音拍摄
        takePhotoByCamera(false)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when(requestCode) {
                REQUEST_CAMERA_CODE -> {
                    // 通知系统文件更新
//                    requireContext().sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
//                        Uri.fromFile(File(picturePath))))
                    if (!enableCrop) {
                        val bitmap = getBitmap(pictureUri!!)
                        bitmap?.let {
                            // 显示图片
                            binding.image.setImageBitmap(it)
                        }
                    }else {
                        cropImage(pictureUri!!)
                    }
                }
                REQUEST_ALBUM_CODE -> {
                    data?.data?.let { uri ->
                        if (!enableCrop) {
                            val bitmap = getBitmap(uri)
                            bitmap?.let {
                                // 显示图片
                                binding.image.setImageBitmap(it)
                            }
                        }else {
                            cropImage(uri)
                        }
                    }
                }
                REQUEST_CROP_CODE -> {
                    val bitmap = getBitmap(cropPicUri!!)
                    bitmap?.let {
                        // 显示图片
                        binding.image.setImageBitmap(it)
                    }
                }
            }
        }
    }

    private fun getBitmap(uri: Uri): Bitmap? {
        var bitmap: Bitmap?
        val options = BitmapFactory.Options()
        // 先不读取，仅获取信息
        options.inJustDecodeBounds = true
        var input = requireContext().contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(input, null, options)

        // 预获取信息，大图压缩后加载
        val width = options.outWidth
        val height = options.outHeight
        Log.d("TAG", "before compress: width = " +
                options.outWidth + "， height = " + options.outHeight)

        // 尺寸压缩
        var size = 1
        while (width / size >= MAX_WIDTH || height / size >= MAX_HEIGHT) {
            size *= 2
        }
        options.inSampleSize = size
        options.inJustDecodeBounds = false
        input = requireContext().contentResolver.openInputStream(uri)
        bitmap = BitmapFactory.decodeStream(input, null, options)
        Log.d("TAG", "after compress: width = " +
                options.outWidth + "， height = " + options.outHeight)

        // 质量压缩
        val baos = ByteArrayOutputStream()
        bitmap!!.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        val bais = ByteArrayInputStream(baos.toByteArray())
        options.inSampleSize = 1
        bitmap = BitmapFactory.decodeStream(bais, null, options)

        return bitmap
    }

    private fun cropImage(uri: Uri) {
        val intent = Intent("com.android.camera.action.CROP")
        // Android 7.0需要临时添加读取Url的权限
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        intent.setDataAndType(uri, "image/*")
        // 使图片处于可裁剪状态
        intent.putExtra("crop", "true")
        // 裁剪框的比例（根据需要显示的图片比例进行设置）
//        if (Build.MANUFACTURER.contains("HUAWEI")) {
//            //硬件厂商为华为的，默认是圆形裁剪框，这里让它无法成圆形
//            intent.putExtra("aspectX", 9999)
//            intent.putExtra("aspectY", 9998)
//        } else {
//            //其他手机一般默认为方形
//            intent.putExtra("aspectX", 1)
//            intent.putExtra("aspectY", 1)
//        }

        // 设置裁剪区域的形状，默认为矩形，也可设置为圆形，可能无效
        // intent.putExtra("circleCrop", true);
        // 让裁剪框支持缩放
        intent.putExtra("scale", true)
        // 属性控制裁剪完毕，保存的图片的大小格式。太大会OOM（return-data）
//        intent.putExtra("outputX", 400)
//        intent.putExtra("outputY", 400)

        // 获取裁切图片Uri，私有文件无法通过provider来获取uri进行裁切
        val cropUri = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            // Android 11以上应该把裁切所需图片放公有目录
            BitmapFileUtil.getUriForPublicPictures(requireContext(), "Crop")
        }else {
            // 低版本还可以把图片放私有目录进行裁切
            val cropFile = BitmapFileUtil.createAppPicture(requireContext(), "Crop")
            Uri.fromFile(cropFile)
        }
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cropUri)

        // 记录临时uri
        cropPicUri = cropUri

        // 设置图片的输出格式
        intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString())

        // return-data=true传递的为缩略图，小米手机默认传递大图， Android 11以上设置为true会闪退
        intent.putExtra("return-data", false)

        startActivityForResult(intent, REQUEST_CROP_CODE)
    }

    // 保存到外部储存-公有目录-Picture内，并且无需储存权限
    private fun insert2Pictures(context: Context) {
        binding.image.drawable?.let {
            val bitmap = it.toBitmap()
            try {
                BitmapFileUtil.insert2Pictures(context, bitmap)
                showToast("导出到相册成功")
            }catch (e: Exception) {
                showToast("导出到相册失败")
            }
        }
    }

    private fun clearCachePictures(context: Context) {
        val result = BitmapFileUtil.clearAppPictures(context)
        if (result) {
            showToast("清除缓存成功")
        }else {
            showToast("清除缓存失败")
        }
    }

    private fun clearAppPictures(context: Context) {
        val num = BitmapFileUtil.clearPublicPictures(context)
        showToast("删除本应用相册图片${num}张")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mCamera?.release()
        mSurfaceCallback = null
        _binding = null
    }

}