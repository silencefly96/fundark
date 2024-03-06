@file:Suppress("unused")

package com.silencefly96.module_tech.practice.demo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_tech.R
import com.silencefly96.module_tech.databinding.FragmentCustomGlideBinding
import com.silencefly96.module_tech.practice.glide.CustomDrawable
import com.silencefly96.module_tech.practice.glide.CustomDrawableBufferDecoder
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