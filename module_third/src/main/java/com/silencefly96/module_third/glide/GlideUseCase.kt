package com.silencefly96.module_third.glide

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide

class GlideUseCase {
    fun useGlide(context: Context, url: String, imgView: ImageView) {
        Glide.with(context)
            .load(url)
            .into(imgView)
    }
}