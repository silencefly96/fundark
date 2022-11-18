@file:Suppress("unused")

package com.example.module_views.custom.demo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.module_views.databinding.FragmentScrollSelectBinding
import com.silencefly96.module_base.base.BaseFragment

class ScrollSelectViewDemo: BaseFragment() {

    private var _binding: FragmentScrollSelectBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentScrollSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        binding.hhView.mData = ArrayList<String>().apply{
            add("第一个")
            add("第二个")
            add("第三个")
            add("第四个")
            add("第五个")
        }
        binding.hhView.mCurrentIndex = 2
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}