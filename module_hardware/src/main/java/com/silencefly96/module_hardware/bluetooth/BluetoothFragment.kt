package com.silencefly96.module_hardware.bluetooth

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.findNavController
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_hardware.R
import com.silencefly96.module_hardware.databinding.FragmentBluetoothBinding

class BluetoothFragment: BaseFragment() {
    private var _binding: FragmentBluetoothBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentBluetoothBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        binding.openClient.setOnClickListener {
            // navigation跳转
            view?.findNavController()?.navigate(R.id.action_bluetooth_to_client)
        }

        binding.openServer.setOnClickListener {
            // navigation跳转
            view?.findNavController()?.navigate(R.id.action_bluetooth_to_server)
        }
    }
}