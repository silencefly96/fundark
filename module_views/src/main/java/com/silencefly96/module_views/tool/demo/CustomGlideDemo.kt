@file:Suppress("unused")

package com.silencefly96.module_views.tool.demo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_views.R
import com.silencefly96.module_views.databinding.FragmentCustomGlideBinding
import com.silencefly96.module_views.tool.glide.CustomDrawable
import com.silencefly96.module_views.tool.glide.CustomDrawableBufferDecoder
import com.silencefly96.module_views.tool.glide.CustomDrawableDecoder
import com.silencefly96.module_views.tool.glide.GlideAppExt
import java.io.InputStream
import java.nio.ByteBuffer

class CustomGlideDemo: BaseFragment() {

    private var _binding: FragmentCustomGlideBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentCustomGlideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        // binding.image.setImageDrawable(CustomDrawable("===100,100,red==="))
        requireContext().let {
            // Glide.get(it).registry.append(InputStream::class.java, CustomDrawable::class.java, CustomDrawableDecoder())
            Glide.get(it).registry.append(ByteBuffer::class.java, CustomDrawable::class.java, CustomDrawableBufferDecoder())
            Glide.with(it)
                .`as`(CustomDrawable::class.java)
                // .load("file:///android_asset/pic.custom")
                .load(R.raw.pic)
                .into(binding.image)
//            GlideAppExt.with(it)
//                .asCustom()
//                .load(R.raw.pic)
//                .into(binding.image)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}