@file:Suppress("unused")

package com.silencefly96.module_tech.tech.demo

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_tech.databinding.FragmentRemoteTestBinding

class RemoteViewTestDemo: BaseFragment() {

    private var _binding: FragmentRemoteTestBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentRemoteTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun doBusiness(context: Context?) {


    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}