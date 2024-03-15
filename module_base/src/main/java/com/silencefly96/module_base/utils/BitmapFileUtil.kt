@file:Suppress("RedundantVisibilityModifier", "unused")

package com.silencefly96.module_base.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream


object BitmapFileUtil {

    // 相册下APP的单独目录
    private const val PATH = "Fundark"

    /**
     * 保存到外部储存-公有目录-Picture内，并且无需储存权限
     *
     * @param context 上下文
     * @param bitmap 图片
     * @return 插入图片的uri
     * @exception IllegalStateException 过程错误异常
     */
    @Throws(IllegalStateException::class)
    public fun insert2Album(context: Context, bitmap: Bitmap): Uri {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val bais = ByteArrayInputStream(baos.toByteArray())
        return insert2Album(context, bais, "Media")
    }


    // 使用MediaStore方式将流写入相册
    @Suppress("SameParameterValue")
    @Throws(IllegalStateException::class)
    private fun insert2Album(context: Context, inputStream: InputStream, type: String): Uri {
        // 获取写入的uri
        val uri = getUriForPublicPictures(context, type) ?:
            throw IllegalStateException("get uri fail")

        // 写入文件
        val result = write2File(context, uri, inputStream)
        if (!result) {
            throw IllegalStateException("write to file fail")
        }

        return uri
    }

    // 写入文件
    private fun write2File(context: Context, uri: Uri, inputStream: InputStream): Boolean {
        // 从Uri构造输出流
        context.contentResolver.openOutputStream(uri)?.use { outputStream->
            val byteArray = ByteArray(1024)
            var len: Int
            do {
                //从输入流里读取数据
                len = inputStream.read(byteArray)
                if (len != -1) {
                    outputStream.write(byteArray, 0, len)
                    outputStream.flush()
                }
            } while (len != -1)
            return true
        }
        return false
    }

    /**
     * 获取公有目录图片uri
     *
     * @param context 上下文
     * @param type 类型，图片名称前缀
     * @return 插入图片的uri
     */
    public fun getUriForPublicPictures(context: Context, type: String): Uri? {
        val fileName = "${type}_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues()
        contentValues.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, fileName)
        // Android 10，路径保存在RELATIVE_PATH
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //RELATIVE_PATH 字段表示相对路径，Fundark为相册下专有目录
            contentValues.put(
                MediaStore.Images.ImageColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + PATH
            )
        } else {
            val dstDir = StringBuilder().let { sb ->
                sb.append(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!.path)
                sb.append(File.separator)
                sb.append(PATH)
                sb.toString()
            }
            val dstPath = dstDir + (File.separator) + fileName
            File(dstDir).also {
                if (!it.exists()) it.mkdirs()
            }

            //DATA字段在Android 10.0 之后已经废弃（Android 11又启用了，但是只读）
            contentValues.put(MediaStore.Images.ImageColumns.DATA, dstPath)
        }

        // 插入相册
        return context.contentResolver
            .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    }

    /**
     * 获取所有当前应用在公有目录保存的图片
     *
     * @param context 上下文
     * @return 删除图片的数量
     */
    public fun getAllPublicPictures(context: Context): List<Pair<String, Uri>> {
        val result: MutableList<Pair<String, Uri>> = ArrayList()

        // 图片类型uri
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        // 查询条目
        val projection = arrayOf(
            MediaStore.Images.Media.TITLE,
            MediaStore.Images.Media._ID
        )

        // 查询条件
        val selection: String
        val selectionArg: String
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Images.ImageColumns.RELATIVE_PATH} like ?"
            selectionArg = "%" + Environment.DIRECTORY_PICTURES + File.separator + PATH + "%"
        } else {
            val dstPath = StringBuilder().let { sb ->
                sb.append(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!.path)
                sb.append(File.separator)
                sb.append(PATH)
                sb.toString()
            }
            selection = "${MediaStore.Images.ImageColumns.DATA} like ?"
            selectionArg = "%$dstPath%"
        }

        // 排序
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        // 查询
        val cursor = context.contentResolver.query(
            uri,
            projection,
            selection,
            arrayOf(selectionArg),
            sortOrder
        )

        // 处理结果
        cursor?.let {
            while (it.moveToNext()) {
                try {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    result.add(
                        Pair(
                            it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.TITLE)),
                            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        )
                    )
                }catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        cursor?.close()
        return result
    }

    /**
     * 清除当前应用在公有目录保存的图片
     *
     * @param context 上下文
     * @return 删除图片的数量
     */
    public fun clearPublicPictures(context: Context): Int {
        val selection: String
        val selectionArgs: String
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Images.ImageColumns.RELATIVE_PATH} like ?"
            selectionArgs = "%" + Environment.DIRECTORY_PICTURES + File.separator + PATH + "%"
        } else {
            val dstPath = StringBuilder().let { sb ->
                sb.append(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!.path)
                sb.append(File.separator)
                sb.append(PATH)
                sb.toString()
            }
            selection = "${MediaStore.Images.ImageColumns.DATA} like ?"
            selectionArgs = "%$dstPath%"
        }

        // 返回删除图片数量
        return context.contentResolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            selection,
            arrayOf(selectionArgs)
        )
    }

    /**
     * 保存到外部私有目录的文件
     *
     * @param context 上下文
     * @param bitmap 图片
     * @return 插入图片的uri
     * @exception IllegalStateException 过程错误异常
     */
    @Throws(IllegalStateException::class)
    public fun saveInApp(context: Context, bitmap: Bitmap): Uri {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val bais = ByteArrayInputStream(baos.toByteArray())
        return saveInApp(context, bais, "Media")
    }


    // 使用MediaStore方式将流写入相册
    @Suppress("SameParameterValue")
    @Throws(IllegalStateException::class)
    private fun saveInApp(context: Context, inputStream: InputStream, type: String): Uri {
        // 获取写入的uri
        val uri = getUriForAppPictures(context, type)

        // 写入文件
        val result = write2File(context, uri, inputStream)
        if (!result) {
            throw IllegalStateException("write to file fail")
        }

        return uri
    }

    /**
     * 获取一个外部私有目录的文件Uri进行写入
     *
     * @param context 上下文
     * @param type 图片类型
     * @return uri
     */
    public fun getUriForAppPictures(context: Context, type: String): Uri {
        val file = createAppPicture(context, type)
        return getUriForFile(file, context)
    }

    /**
     * 获取一个外部私有目录的文件进行写入
     *
     * @param context 上下文
     * @param type 图片类型
     * @return 私有目录文件
     */
    public fun createAppPicture(context: Context, type: String): File {
        // 在相册创建一个临时文件
        val picFile = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "${type}_${System.currentTimeMillis()}.jpg")
        try {
            if (picFile.exists()) {
                picFile.delete()
            }
            picFile.createNewFile()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // 临时文件，后面会加long型随机数
//        return File.createTempFile(
//            type,
//            ".jpg",
//            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
//        )

        return picFile
    }

    // 获取一个外部私有目录的文件的uri
    private fun getUriForFile(file: File, context: Context): Uri {
        // 转换为uri
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //适配Android 7.0文件权限，通过FileProvider创建一个content类型的Uri
            FileProvider.getUriForFile(
                context,
                context.packageName + ".fileProvider", file
            )
        } else {
            Uri.fromFile(file)
        }
    }

    /**
     * 获取应用外部私有目录内PICTURES目录所有图片
     *
     * @param context 上下文
     * @return 是否成功
     */
    public fun getAllAppPictures(context: Context): List<Pair<String, Uri>> {
        val result: MutableList<Pair<String, Uri>> = ArrayList()
        // 外部储存-私有目录-files-Pictures目录
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { dir->
            // 删除其中的图片
            try {
                val pics = dir.listFiles()
                pics?.forEach { pic ->
                    result.add(Pair(pic.name, getUriForFile(pic, context)))
                }
            }catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return result
    }

    /**
     * 清除应用外部私有目录内PICTURES目录所有图片
     *
     * @param context 上下文
     * @return 是否成功
     */
    public fun clearAppPictures(context: Context): Boolean {
        // 外部储存-私有目录-files-Pictures目录
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { dir->
            // 删除其中的图片
            try {
                val pics = dir.listFiles()
                pics?.forEach { pic ->
                    pic.delete()
                }
                return true
            }catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }


    /**
     * 根据uri删除图片
     *
     * @param context 上下文
     * @param uri 要删除的Uri
     * @return 删除结果
     */
    public fun deletePictureByUri(context: Context, uri: Uri): Int {
        var result: Int = -1
        if (Build.VERSION.SDK_INT >= 24) {
            result = context.contentResolver.delete(uri, null, null)
        } else {
            getRealFilePath(context, uri)?.let {
                val file = File(it)
                if (file.exists()) {
                    result = if (file.delete()) 1 else -1
                }
            }
        }
        return result
    }

    // Android 7.0以下从Uri中获取文件路径
    private fun getRealFilePath(context: Context, uri: Uri?): String? {
        if (null == uri) return null
        val scheme = uri.scheme
        var data: String? = null
        if (scheme == null) {
            data = uri.path
        } else if (ContentResolver.SCHEME_FILE == scheme) {
            data = uri.path
        } else if (ContentResolver.SCHEME_CONTENT == scheme) {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.ImageColumns.DATA),
                null,
                null,
                null
            )
            if (null != cursor) {
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
                    if (index > -1) {
                        data = cursor.getString(index)
                    }
                }
                cursor.close()
            }
        }
        return data
    }

    /**
     * 资源drawable转成bitmap
     *
     * @param context 上下文
     * @param id 资源id
     */
    fun drawable2Bitmap(context: Context, id: Int): Bitmap {
        val drawable = ResourcesCompat.getDrawable(context.resources, id, null)
        return drawable2Bitmap(drawable!!)
    }

    /**
     * 资源drawable转成bitmap
     *
     * @param drawable 图片
     */
    fun drawable2Bitmap(drawable: Drawable): Bitmap {
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        val config = Bitmap.Config.ARGB_8888
        val bitmap = Bitmap.createBitmap(w, h, config)
        //注意，下面三行代码要用到，否则在View或者SurfaceView里的canvas.drawBitmap会看不到图
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bitmap
    }
}