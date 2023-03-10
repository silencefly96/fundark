package com.silencefly96.module_views.widget.utils

import android.graphics.BitmapFactory

import android.graphics.Bitmap

import com.silencefly96.module_views.widget.LazyImageView
import com.silencefly96.module_views.widget.LazyImageView.IBitmapHelper
import java.io.File
import java.io.FileInputStream
import java.lang.Exception
import java.lang.IllegalArgumentException


/**
 * @author: silence
 * @date: 2021-05-27
 * @description: 简单图片处理工具
 */
class BitmapHelper : IBitmapHelper {
    override fun handle(file: File): Bitmap {
        return decodePhotoFile(file)
    }

    //根据文件生成bitmap
    private fun decodePhotoFile(file: File): Bitmap {
        var bitmap: Bitmap? = null
        val options = BitmapFactory.Options()
        try {
            FileInputStream(file).use { stream ->
                bitmap = BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (bitmap == null) throw IllegalArgumentException("bitmap file error")
        return bitmap!!
    }
}
