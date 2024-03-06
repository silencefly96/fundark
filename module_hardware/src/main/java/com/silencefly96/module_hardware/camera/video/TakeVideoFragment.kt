package com.silencefly96.module_hardware.camera.video

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_hardware.databinding.FragmentCameraTakeVideoBinding

class TakeVideoFragment: BaseFragment() {

    private var _binding: FragmentCameraTakeVideoBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View? {
        _binding = FragmentCameraTakeVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        super.doBusiness(context)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}