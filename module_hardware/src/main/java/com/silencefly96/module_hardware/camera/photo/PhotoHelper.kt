package com.silencefly96.module_hardware.camera.photo

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.util.Consumer
import androidx.fragment.app.Fragment
import com.silencefly96.module_base.utils.BitmapFileUtil
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PhotoHelper {

    companion object{
        const val REQUEST_CAMERA_CODE = 1
        const val REQUEST_ALBUM_CODE = 2
        const val REQUEST_CROP_CODE = 3

        const val MAX_WIDTH = 480
        const val MAX_HEIGHT = 720
    }

    // 拍照路径
    private var pictureUri: Uri? = null

    // 裁切路径
    private var cropPicUri: Uri? = null

    // 启用裁切
    var enableCrop: Boolean = true

    /**
     * 通过相机获取照片
     *
     * @param fragment
     */
    fun openCamera(fragment: Fragment) {
        // ps. 这里注意下: 如果 AndroidManifest.xml 里面没有声明相机权限，是无需权限，能直接调用系统拍照获取相片
        // but，如果在 AndroidManifest.xml 里面声明了相机权限(包括SDK)，则必须动态申请权限，否则会闪退

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // 应用外部私有目录：files-Pictures
        val photoUri = BitmapFileUtil.getUriForAppPictures(fragment.requireContext(), "Camera")
        // 保存uri
        pictureUri = photoUri
        // 给目标应用一个临时授权
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        //android11以后强制分区存储，外部资源无法访问，所以添加一个输出保存位置，然后取值操作
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        fragment.startActivityForResult(intent, REQUEST_CAMERA_CODE)
    }

    fun openAlbum(fragment: Fragment) {
        val intent = Intent()
        intent.type = "image/*"
        // GET_CONTENT不允许使用裁切，只能简单的读取数据
        // intent.action = "android.intent.action.GET_CONTENT"
        // OPEN_DOCUMENT允许对文件进行编辑，并且权限是长期有效的，先用OPEN_DOCUMENT再GET_CONTENT也是有权限的
        intent.action = "android.intent.action.OPEN_DOCUMENT"
        intent.addCategory("android.intent.category.OPENABLE")
        fragment.startActivityForResult(intent, REQUEST_ALBUM_CODE)
    }

    fun getBitmap(fragment: Fragment, uri: Uri): Bitmap? {
        var bitmap: Bitmap?
        val options = BitmapFactory.Options()
        // 先不读取，仅获取信息
        options.inJustDecodeBounds = true
        var input = fragment.requireActivity().contentResolver.openInputStream(uri)
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
        input = fragment.requireActivity().contentResolver.openInputStream(uri)
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

    /**
     * 根据给定的新宽度和新高度,对传入的Bitmap进行缩放操作
     *
     * @param bitmap 需要进行缩放的原始Bitmap对象
     * @param newWidth 希望缩放后Bitmap的新宽度
     * @param newHeight 希望缩放后Bitmap的新高度
     * @return 缩放后的新Bitmap对象
     */
    fun scaleBitmap(bitmap: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // 创建一个Matrix对象
        val matrix = Matrix()

        // 计算缩放比例
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height

        // 设置Matrix的缩放比例
        matrix.postScale(scaleWidth, scaleHeight)

        // 创建一个新的Bitmap对象，用于存储缩放后的图片
        val newBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)

        // 如果原始Bitmap不再需要,可以回收内存
        bitmap.recycle()

        // 返回新的Bitmap对象
        return newBitmap
    }

    private fun cropImage(fragment: Fragment, uri: Uri) {
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
            BitmapFileUtil.getUriForPublicPictures(fragment.requireContext(), "Crop")
        }else {
            // 低版本还可以把图片放私有目录进行裁切
            val cropFile = BitmapFileUtil.createAppPicture(fragment.requireContext(), "Crop")
            Uri.fromFile(cropFile)
        }
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cropUri)

        // 记录临时uri
        cropPicUri = cropUri

        // 设置图片的输出格式
        intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString())

        // return-data=true传递的为缩略图，小米手机默认传递大图， Android 11以上设置为true会闪退
        intent.putExtra("return-data", false)

        fragment.startActivityForResult(intent, REQUEST_CROP_CODE)
    }

    fun onActivityResult(fragment: Fragment, requestCode: Int, resultCode: Int, data: Intent?, consumer: Consumer<Bitmap>) {
        if (resultCode == Activity.RESULT_OK) {
            when(requestCode) {
                REQUEST_CAMERA_CODE -> {
                    // 通知系统文件更新
//                    requireContext().sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
//                        Uri.fromFile(File(picturePath))))
                    if (!enableCrop) {
                        val bitmap = getBitmap(fragment, pictureUri!!)
                        bitmap?.let {
                            // 显示图片
                            consumer.accept(it)
                        }
                    }else {
                        cropImage(fragment, pictureUri!!)
                    }
                }
                REQUEST_ALBUM_CODE -> {
                    data?.data?.let { uri ->
                        if (!enableCrop) {
                            val bitmap = getBitmap(fragment,uri)
                            bitmap?.let {
                                // 显示图片
                                consumer.accept(it)
                            }
                        }else {
                            cropImage(fragment, uri)
                        }
                    }
                }
                REQUEST_CROP_CODE -> {
                    val bitmap = getBitmap(fragment, cropPicUri!!)
                    bitmap?.let {
                        // 显示图片
                        consumer.accept(it)
                    }
                }
            }
        }
    }
}