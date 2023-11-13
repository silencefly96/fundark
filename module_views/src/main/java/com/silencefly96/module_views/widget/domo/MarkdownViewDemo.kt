package com.silencefly96.module_views.widget.domo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_views.databinding.FragmentMarkDownBinding
import java.io.BufferedReader
import java.io.InputStreamReader

class MarkdownViewDemo: BaseFragment() {

    private var _binding: FragmentMarkDownBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentMarkDownBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        val reader = BufferedReader(InputStreamReader(resources.assets.open("Glide加载自定义图片格式.md")))
        val content = reader.readText()
        binding.hhView.text = content
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}