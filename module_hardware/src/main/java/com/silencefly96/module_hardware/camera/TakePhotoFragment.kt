package com.silencefly96.module_hardware.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toBitmap
import androidx.core.util.Consumer
import androidx.lifecycle.coroutineScope
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_base.base.IPermissionHelper
import com.silencefly96.module_base.utils.BitmapFileUtil
import com.silencefly96.module_hardware.databinding.FragmentTakePhotoBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class TakePhotoFragment : BaseFragment() {

    private var _binding: FragmentTakePhotoBinding? = null
    private val binding get() = _binding!!

    // 系统相机、相册选图片辅助类
    private lateinit var photoHelper: PhotoHelper

    // 旧版Camera接口，已被废弃
    private lateinit var cameraHelper: CameraHelper

    // Camera2接口，Android 5.0以上升级方案
    private lateinit var camera2Helper: Camera2Helper

    // JetPack的CameraX，基与Cmaera2 API封装，简化了开发流程，并增加生命周期控制
    private lateinit var cameraXHelper: CameraXHelper

    // 绑定布局
    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentTakePhotoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        binding.takePhoto.setOnClickListener {
            // 如果 AndroidManifest.xml 里面有注册Camera权限(包含SDK)，则必须申请=>默认申请最好
            requestPermission {
                it ?: photoHelper.openCamera(this)
            }
        }

        binding.pickPhoto.setOnClickListener {
            photoHelper.openAlbum(this)
        }

        binding.takePhotoByCamera.setOnClickListener {
            requestPermission {
                if (it) {
                    // surface可见时才能使用相机
                    binding.surface.visibility = View.VISIBLE
                    // 调用拍照，相机操作最好放异步线程
                    lifecycle.coroutineScope.launch(Dispatchers.IO) {
                        cameraHelper.takePhotoByCamera(requireActivity(), binding.surface, {
                            // UI操作放主线程
                            binding.image.post {
                                binding.image.setImageBitmap(it)
                                binding.image.bringToFront()
                            }
                        })
                    }
                }
            }
        }

        binding.takePhotoNoFeeling.setOnClickListener {
            requestPermission {
                if (it) {
                    // surface可见时才能使用相机
                    binding.surface.visibility = View.VISIBLE
                    // 使用camera2拍照
                    lifecycle.coroutineScope.launch(Dispatchers.IO) {
                        cameraHelper.takePhotoNoFeeling(requireActivity(), binding.surface) {
                            // UI操作放主线程
                            binding.image.post {
                                binding.image.setImageBitmap(it)
                                binding.image.bringToFront()
                            }
                        }
                    }
                }
            }
        }

        binding.takePhotoByCamera2.setOnClickListener {
            requestPermission {
                if (it) {
                    // surface可见时才能使用相机
                    binding.surface.visibility = View.VISIBLE
                    // 无感调用拍照
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        camera2Helper.takePhotoByCamera2(requireActivity(), binding.surface) {
                            // UI操作放主线程
                            binding.image.post {
                                binding.image.setImageBitmap(it)
                                binding.image.bringToFront()
                            }
                        }
                    }
                }
            }
        }

        binding.takePhotoByCameraX.setOnClickListener {
            cameraXHelper.takePhotoByCameraX()
        }

        binding.insertPictures.setOnClickListener {
            insert2Pictures(requireContext())
        }

        binding.clearCache.setOnClickListener {
            clearCachePictures(requireContext())
        }

        binding.clearPictures.setOnClickListener {
            clearAppPictures(requireContext())
        }

        binding.cropSwitch.setOnCheckedChangeListener { _, isChecked ->
            photoHelper.enableCrop = isChecked
        }

        photoHelper = PhotoHelper()
        cameraHelper = CameraHelper()
        // Android 5.0开始支持Camera2
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            camera2Helper = Camera2Helper()
        }
        cameraXHelper = CameraXHelper()
    }

    private fun requestPermission(consumer: Consumer<Boolean>) {
        // 动态申请权限，使用的外部私有目录无需申请权限
        requestRunTimePermission(requireActivity(), arrayOf(
            Manifest.permission.CAMERA,
//            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ),
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 通过辅助类处理
        photoHelper.onActivityResult(this, requestCode, resultCode, data) {
            binding.image.setImageBitmap(it)
            binding.image.bringToFront()
        }
    }

    // 保存到外部储存-公有目录-Picture内，并且无需储存权限
    private fun insert2Pictures(context: Context) {
        binding.image.drawable?.let {
            val bitmap = it.toBitmap()
            try {
                BitmapFileUtil.insert2Pictures(context, bitmap)
                showToast("导出到相册成功")
            }catch (e: Exception) {
                showToast("导出到相册失败")
            }
        }
    }

    private fun clearCachePictures(context: Context) {
        val result = BitmapFileUtil.clearAppPictures(context)
        if (result) {
            showToast("清除缓存成功")
        }else {
            showToast("清除缓存失败")
        }
    }

    private fun clearAppPictures(context: Context) {
        val num = BitmapFileUtil.clearPublicPictures(context)
        showToast("删除本应用相册图片${num}张")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}