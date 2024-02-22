package com.silencefly96.module_hardware.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toBitmap
import androidx.core.util.Consumer
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_base.base.IPermissionHelper
import com.silencefly96.module_base.utils.BitmapFileUtil
import com.silencefly96.module_hardware.databinding.FragmentTakePhotoBinding


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
            requestPermission {
                photoHelper.openCamera(this)
            }
        }

        binding.pickPhoto.setOnClickListener {
            photoHelper.openAlbum(this)
        }

        binding.takePhotoByCamera.setOnClickListener {
            // 调用拍照
            cameraHelper.takePhotoByCamera(requireActivity(), binding.surface, {
                binding.image.setImageBitmap(it)
                binding.image.bringToFront()
            })
        }

        binding.takePhotoNoFeeling.setOnClickListener {
            // 无感调用拍照
            cameraHelper.takePhotoNoFeeling(requireActivity(), binding.surface) {
                binding.image.setImageBitmap(it)
                binding.image.bringToFront()
            }
        }

        binding.takePhotoByCamera2.setOnClickListener {
            camera2Helper.takePhotoByCamera2()
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
        camera2Helper = Camera2Helper()
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