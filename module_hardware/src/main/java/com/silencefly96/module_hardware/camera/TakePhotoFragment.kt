package com.silencefly96.module_hardware.camera

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.core.util.Consumer
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_base.base.IPermissionHelper
import com.silencefly96.module_hardware.databinding.FragmentTakePhotoBinding
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException


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

    // 文件路径
    private var picturePath: String = ""

    // 裁切路径
    private var cropPicPath: String = ""

    // 启用裁切
    private var enableCrop: Boolean = true

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
            requestPermission { openAlbum() }
        }

        binding.cropSwitch.setOnCheckedChangeListener { _, isChecked -> enableCrop = isChecked}
    }

    private fun requestPermission(consumer: Consumer<Boolean>) {
        // 动态申请权限
        requestRunTimePermission(requireActivity(), arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
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
        // 相册目录：Pictures，不能使用应用私有目录
        val picFile = createFile("Camera")
        val photoUri = getUriForFile(picFile)
        // 保存路径，不要uri，读取bitmap时麻烦
        picturePath = picFile.absolutePath
        // 给目标应用一个临时授权
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        //android11以后强制分区存储，外部资源无法访问，所以添加一个输出保存位置，然后取值操作
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        startActivityForResult(intent, REQUEST_CAMERA_CODE)
    }

    private fun createFile(type: String): File {
        // 在相册创建一个临时文件
        val picFile =
            File(
                requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "${type}_${System.currentTimeMillis()}.jpg"
            )
        try {
            if (picFile.exists()) {
                picFile.delete()
            }
            picFile.createNewFile()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return picFile
    }

    private fun getUriForFile(file: File): Uri {
        // 转换为uri
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //适配Android 7.0文件权限，通过FileProvider创建一个content类型的Uri
            FileProvider.getUriForFile(
                requireActivity(),
                "com.silencefly96.module_hardware.fileProvider", file
            )
        } else {
            Uri.fromFile(file)
        }
    }

    private fun openAlbum() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = "android.intent.action.GET_CONTENT"
        intent.addCategory("android.intent.category.OPENABLE")
        startActivityForResult(intent, REQUEST_ALBUM_CODE)
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
                        val bitmap = getBitmap(picturePath)
                        bitmap?.let {
                            // 显示图片
                            binding.image.setImageBitmap(it)
                        }
                    }else {
                        cropImage(picturePath)
                    }
                }
                REQUEST_ALBUM_CODE -> {
                    data?.data?.let { uri ->
                        if (!enableCrop) {
                            val bitmap = getBitmap("", uri)
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
                    val bitmap = getBitmap(cropPicPath)
                    bitmap?.let {
                        // 显示图片
                        binding.image.setImageBitmap(it)
                    }
                }
            }
        }
    }

    private fun getBitmap(path: String, uri: Uri? = null): Bitmap? {
        var bitmap: Bitmap?
        val options = BitmapFactory.Options()
        // 先不读取，仅获取信息
        options.inJustDecodeBounds = true
        if (uri == null) {
            BitmapFactory.decodeFile(path, options)
        }else {
            val input = requireContext().contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(input, null, options)
        }

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
        bitmap = if (uri == null) {
            BitmapFactory.decodeFile(path, options)
        }else {
            val input = requireContext().contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(input, null, options)
        }
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

    private fun cropImage(path: String) {
        cropImage(getUriForFile(File(path)))
    }

    private fun cropImage(uri: Uri) {
        val intent = Intent("com.android.camera.action.CROP")
        // Android 7.0需要临时添加读取Url的权限
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
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

        // 生成临时文件
        val cropFile = createFile("Crop")
        // 裁切图片时不能使用provider的uri，否则无法保存
//        val cropUri = getUriForFile(cropFile)
        val cropUri = Uri.fromFile(cropFile)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cropUri)
        // 记录临时位置
        cropPicPath = cropFile.absolutePath

        // 设置图片的输出格式
        intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString())

        // return-data=true传递的为缩略图，小米手机默认传递大图， Android 11以上设置为true会闪退
        intent.putExtra("return-data", false)

        startActivityForResult(intent, REQUEST_CROP_CODE)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}