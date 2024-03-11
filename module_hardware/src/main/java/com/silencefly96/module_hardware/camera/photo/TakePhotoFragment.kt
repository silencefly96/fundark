package com.silencefly96.module_hardware.camera.photo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toBitmap
import androidx.core.util.Consumer
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_base.base.IPermissionHelper
import com.silencefly96.module_base.utils.BitmapFileUtil
import com.silencefly96.module_hardware.R
import com.silencefly96.module_hardware.databinding.FragmentCameraTakePhotoBinding


class TakePhotoFragment : BaseFragment() {

    private var _binding: FragmentCameraTakePhotoBinding? = null
    private val binding get() = _binding!!

    // 选中的相机API
    private var mSelectCameraType = R.id.type_cameraX

    // 系统相机、相册选图片辅助类
    private lateinit var photoHelper: PhotoHelper

    // 旧版Camera接口，已被废弃
    private lateinit var camera1Helper: Camera1Helper

    // Camera2接口，Android 5.0以上升级方案
    private lateinit var camera2Helper: Camera2Helper

    // JetPack的CameraX，基与Cmaera2 API封装，简化了开发流程，并增加生命周期控制
    private lateinit var cameraXHelper: CameraXHelper

    // 绑定布局
    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentCameraTakePhotoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {

        // 使用系统相机拍照
        binding.takePhoto.setOnClickListener {
            // 如果 AndroidManifest.xml 里面有注册Camera权限(包含SDK)，则必须申请=>默认申请最好
            requestPermission {
                photoHelper.openCamera(this)
            }
        }

        // 从相册选取
        binding.pickPhoto.setOnClickListener {
            photoHelper.openAlbum(this)
        }

        // 更新选中相机的API
        binding.cameraType.setOnCheckedChangeListener { _, checkedId ->
            mSelectCameraType = checkedId
        }

        // 启动预览
        binding.startPreview.setOnClickListener {
            requestPermission {
                // 预览前关闭所有相机资源
                camera1Helper.release()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    camera2Helper.release()
                    cameraXHelper.release()
                }

                // 再根据选择的API预览
                when(mSelectCameraType) {
                    R.id.type_camera -> {
                        // surface可见时才能使用相机
                        binding.surface.visibility = View.VISIBLE
                        binding.surface.bringToFront()
                        camera1Helper.startPreview(requireActivity(), binding.surface)
                    }
                    R.id.type_camera2 -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            binding.textureView.visibility = View.VISIBLE
                            binding.textureView.bringToFront()
                            camera2Helper.startPreview(requireActivity(), binding.textureView)
                        }
                    }
                    else -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            binding.preview.visibility = View.VISIBLE
                            binding.preview.bringToFront()
                            cameraXHelper.startPreview(requireActivity(), binding.preview)
                        }
                    }
                }
            }
        }

        // 点击拍照
        binding.takeCapture.setOnClickListener {
            requestPermission {
                // 拍照后的回调
                val callback = Consumer<Bitmap> {
                    binding.image.setImageBitmap(it)
                    binding.image.bringToFront()
                }

                when(mSelectCameraType) {
                    R.id.type_camera -> {
                        camera1Helper.takePhoto(requireActivity(), binding.surface, callback)
                    }
                    R.id.type_camera2 -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            camera2Helper.takePhoto(requireActivity(), binding.textureView, callback)
                        }
                    }
                    else -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            cameraXHelper.takePhoto(requireActivity(), binding.preview, callback)
                        }
                    }
                }
            }
        }

        // 取消拍照
        binding.cancelCapture.setOnClickListener {
            when(mSelectCameraType) {
                R.id.type_camera -> {
                    binding.surface.visibility = View.VISIBLE
                    binding.surface.bringToFront()
                    try {
                        // 如果
                        camera1Helper.continuePreview()
                    }catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                R.id.type_camera2 -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        binding.textureView.visibility = View.VISIBLE
                        binding.textureView.bringToFront()
                    }
                }
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        binding.preview.visibility = View.VISIBLE
                        binding.preview.bringToFront()
                    }
                }
            }
        }

        // 保存到相册
        binding.insertPictures.setOnClickListener {
            insert2Pictures(requireContext())
        }

        // 清除本地缓存
        binding.clearCache.setOnClickListener {
            clearCachePictures(requireContext())
        }

        // 删除APP的相册图片
        binding.clearPictures.setOnClickListener {
            clearAppPictures(requireContext())
        }

        // 裁切开关
        binding.cropSwitch.setOnCheckedChangeListener { _, isChecked ->
            photoHelper.enableCrop = isChecked
        }

        // 几个相机辅助类
        photoHelper = PhotoHelper()
        camera1Helper = Camera1Helper()
        // Android 5.0开始支持Camera2
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            camera2Helper = Camera2Helper()
            cameraXHelper = CameraXHelper()
        }
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
                    // consumer.accept(false)
                }

                override fun onDenied(deniedPermission: List<String>?) {
                    // consumer.accept(false)
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

        // 释放资源
        camera1Helper.release()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            camera2Helper.release()
            cameraXHelper.release()
        }

        _binding = null
    }
}