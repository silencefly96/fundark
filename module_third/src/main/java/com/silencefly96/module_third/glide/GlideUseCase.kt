package com.silencefly96.module_third.glide

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import com.bumptech.glide.Glide

class GlideUseCase {
    fun useGlide(context: Context, url: String, imgView: ImageView) {
        Glide.with(context)
            .load(url)
            .into(imgView)

        Glide.with(context)
            .load(url)
            .centerCrop() //centerCrop缩放模式
            .circleCrop() //裁剪为圆形
            .placeholder(ColorDrawable(Color.RED)) //占位drawable
            .into(imgView)
    }
}