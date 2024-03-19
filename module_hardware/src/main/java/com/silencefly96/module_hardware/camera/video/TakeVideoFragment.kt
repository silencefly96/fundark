package com.silencefly96.module_hardware.camera.video

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.util.Consumer
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_hardware.R
import com.silencefly96.module_hardware.camera.helper.Camera1VideoHelper
import com.silencefly96.module_hardware.camera.helper.Camera2VideoHelper
import com.silencefly96.module_hardware.camera.helper.CameraXVideoHelper
import com.silencefly96.module_hardware.camera.helper.ICameraVideoHelper
import com.silencefly96.module_hardware.databinding.FragmentCameraTakeVideoBinding

class TakeVideoFragment: BaseFragment() {

    private var _binding: FragmentCameraTakeVideoBinding? = null
    private val binding get() = _binding!!

    companion object {
        // 类型
        const val TYPE_CAMERA1 = 0
        const val TYPE_CAMERA2 = 1
        const val TYPE_CAMERAX = 2
    }

    // 当前类型
    private var mCurrentType = TYPE_CAMERA1

    // 辅助类
    private var helper: ICameraVideoHelper<*>? = null

    // 旧版Camera接口，已被废弃
    private lateinit var camera1Helper: Camera1VideoHelper

    // Camera2接口，Android 5.0以上升级方案
    private lateinit var camera2Helper: Camera2VideoHelper

    // JetPack的CameraX，基与Cmaera2 API封装，简化了开发流程，并增加生命周期控制
    private lateinit var cameraXHelper: CameraXVideoHelper

    // 统一处理录制
    private val mRecordCallback = object : RecordButton.RecordCallback {
        override fun onRecordStart(button: RecordButton) {
            when(button.tag) {
                resources.getString(R.string.type_camera) -> {
                    helper = camera1Helper
                }
                resources.getString(R.string.type_camera2) -> {
                    helper = camera2Helper
                }
                resources.getString(R.string.type_cameraX) -> {
                    helper = cameraXHelper
                }
                else -> {}
            }
        }

        override fun onRecordZoom(button: RecordButton, zoom: Float) {
            // 更新放大倍数
            button.zoomSize = String.format("%.2f X", (1 + zoom))
            // 更改相机参数
            when(button.tag) {
                resources.getString(R.string.type_camera) -> {

                }
                resources.getString(R.string.type_camera2) -> {

                }
                resources.getString(R.string.type_cameraX) -> {

                }
                else -> {}
            }
        }

        override fun onRecordEnd() {
            TODO("Not yet implemented")
        }
    }

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View? {
        _binding = FragmentCameraTakeVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        super.doBusiness(context)
        // 初始化几个按钮
        initView()

        // 进入即开始预览(默认第一个)
        preview(TYPE_CAMERA1)
    }

    private fun initView() {
        // 各个按钮设置统一的callback
        binding.camera1Button.recordCallback = mRecordCallback
        binding.camera2Button.recordCallback = mRecordCallback
        binding.cameraXButton.recordCallback = mRecordCallback
        // 设置页面切换监听
        binding.recordGroup.pageChangeListner = Consumer {
            mCurrentType = it
            // 进行预览
            preview(it)
        }
    }

    private fun preview(type: Int) {

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}