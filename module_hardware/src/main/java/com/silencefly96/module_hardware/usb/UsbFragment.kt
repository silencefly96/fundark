package com.silencefly96.module_hardware.usb

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_hardware.databinding.FragmentUsbBinding

class UsbFragment : BaseFragment() {
    private var _binding: FragmentUsbBinding? = null
    private val binding get() = _binding!!

    // 绑定布局
    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentUsbBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}