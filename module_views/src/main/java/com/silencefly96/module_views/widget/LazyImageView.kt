package com.silencefly96.module_views.widget

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.util.Consumer
import java.io.File
import java.util.*


/**
 * @author: silence
 * @date: 2021-05-27
 * @description: 简单图片懒加载
 */
class LazyImageView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attributeSet, defStyleAttr) {

    //图片链接
    private var mUrl = ""

    //设置
    fun show(url: String) {
        Log.d("TAG", "show: $url")
        if (mUrl != url) {
            mUrl = url
            //取内存缓存，无内存缓存设为默认图
            display(if (null != sBitmapCache[url]) sBitmapCache[url] else sDefaultBitmap)
            //加载链接
            load { file ->
                if (null != file) {
                    val bitmap = handle(file)
                    cache(bitmap)
                    display(bitmap)
                } else {
                    display(sErrorBitmap)
                }
            }
        }
    }

    //加载
    private fun load(resultHandler: Consumer<File>) {
        Log.d("TAG", "load: ")
        sFileHelper!!.download(mUrl, resultHandler)
    }

    //处理
    private fun handle(file: File): Bitmap {
        Log.d("TAG", "handle: ")
        return sBitmapHelper!!.handle(file)
    }

    //缓存
    private fun cache(bitmap: Bitmap) {
        Log.d("TAG", "cache: ")
        sBitmapCache[mUrl] = bitmap
    }

    //显示
    private fun display(bitmap: Bitmap?) {
        Log.d("TAG", "display: ")
        post { setImageBitmap(bitmap) }
    }

    //文件处理，解耦，如有需要重写 download 即可
    interface IFileHelper {
        fun download(url: String, resultHandler: Consumer<File>)
        fun clearCache(url: String)
    }

    //图片处理，解耦，如有需要重写handle函数
    interface IBitmapHelper {
        fun handle(file: File): Bitmap
    }

    companion object {
        //默认图片
        private var sDefaultBitmap: Bitmap? = null

        //失败图片
        private var sErrorBitmap: Bitmap? = null

        //文件处理工具
        private var sFileHelper: IFileHelper? = null

        //图片处理工具
        private var sBitmapHelper: IBitmapHelper? = null

        //内存缓存 - 10条，自动删除最老数据，Bitmap会自动回收
        private val sBitmapCache: MutableMap<String, Bitmap?> =
            object : LinkedHashMap<String, Bitmap?>() {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap?>?): Boolean {
                    return size >= 10
                }
            }

        //初始化
        fun init(
            defaultBitmap: Bitmap?,
            errorBitmap: Bitmap?,
            fileHelper: IFileHelper?,
            bitmapHelper: IBitmapHelper?
        ) {
            // BitmapFactory.decodeResource(getResources(), R.mipmap.img_def);
            sDefaultBitmap = defaultBitmap
            sErrorBitmap = errorBitmap
            sFileHelper = fileHelper
            sBitmapHelper = bitmapHelper
        }

        fun clearMemoryCache(url: String) {
            sBitmapCache.remove(url)
        }

        fun clearDiskCache(url: String) {
            sFileHelper?.clearCache(url)
        }

    }
}

