package com.silencefly96.module_hardware.bluetooth

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.navigation.findNavController
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_base.base.IPermissionHelper
import com.silencefly96.module_hardware.R
import com.silencefly96.module_hardware.databinding.FragmentBluetoothBinding

class BluetoothFragment: BaseFragment() {

    private var _binding: FragmentBluetoothBinding? = null
    private val binding get() = _binding!!

    // 权限组
    private val permissions = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentBluetoothBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        binding.openClient.setOnClickListener {
            checkPermissions {
                if (it) {
                    // navigation跳转
                    view?.findNavController()?.navigate(R.id.action_bluetooth_to_client)
                }else {
                    showToast("未获取所需权限！")
                }
            }
        }

        binding.openServer.setOnClickListener {
            checkPermissions {
                if (it) {
                    // navigation跳转
                    view?.findNavController()?.navigate(R.id.action_bluetooth_to_server)
                }else {
                    showToast("未获取所需权限！")
                }
            }
        }
    }

    private fun checkPermissions(consumer: Consumer<Boolean>) {
        var hasAllPermission = true
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                != PackageManager.PERMISSION_GRANTED) {
                hasAllPermission = false
            }
        }
        if (hasAllPermission) {
            consumer.accept(true)
        }else {
            AlertDialog.Builder(requireContext())
                .setTitle("警告")
                .setMessage("未获得蓝牙依赖权限！")
                .setPositiveButton("授权") { _, _ ->
                    requestRunTimePermission(requireActivity(), permissions,
                        object : IPermissionHelper.PermissionListener {
                            override fun onGranted() {
                                consumer.accept(true)
                            }

                            override fun onGranted(grantedPermission: List<String>?) {
                                consumer.accept(false)
                            }

                            override fun onDenied(deniedPermission: List<String>?) {
                                consumer.accept(false)
                            }
                        })
                 }
                .setNegativeButton("取消", null)
                .create()
                .show()
        }
    }
}