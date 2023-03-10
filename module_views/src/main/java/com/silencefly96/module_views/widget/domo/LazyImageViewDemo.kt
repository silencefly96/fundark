@file:Suppress("unused")

package com.silencefly96.module_views.widget.domo

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_base.base.IPermissionHelper
import com.silencefly96.module_views.databinding.FragmentLazyImageViewBinding
import com.silencefly96.module_views.widget.LazyImageView
import com.silencefly96.module_views.widget.utils.BitmapHelper
import com.silencefly96.module_views.widget.utils.FileHelper


class LazyImageViewDemo: BaseFragment() {

    companion object{
        const val BING_PIC_LINK = "https://fuss10.elemecdn.com/e/5d/4a731a90594a4af544c0c25941171jpeg.jpeg"
    }

    private var _binding: FragmentLazyImageViewBinding? = null
    private val binding get() = _binding!!

    // 权限组
    private val permissions = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentLazyImageViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        binding.showPic.setOnClickListener {
            checkPermissions {
                if (it) {
                    LazyImageView.init(
                        BitmapFactory.decodeResource(resources, android.R.mipmap.sym_def_app_icon),
                        BitmapFactory.decodeResource(resources, android.R.mipmap.sym_def_app_icon),
                        FileHelper(), BitmapHelper()
                    )
                    binding.image.show(BING_PIC_LINK)
                }else {
                    showToast("未获取所需权限！")
                }
            }
        }
        binding.clearPic.setOnClickListener {
            LazyImageView.clearMemoryCache(BING_PIC_LINK)
            LazyImageView.clearDiskCache(BING_PIC_LINK)
            binding.image.setImageResource(android.R.mipmap.sym_def_app_icon)
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
                .setMessage("未获得存储依赖权限！")
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}