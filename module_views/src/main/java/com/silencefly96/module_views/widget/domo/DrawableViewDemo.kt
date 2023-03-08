@file:Suppress("unused")

package com.silencefly96.module_views.widget.domo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_views.databinding.FragmentDrawableViewBinding
import java.io.IOException

class DrawableViewDemo: BaseFragment() {

    private var _binding: FragmentDrawableViewBinding? = null
    private val binding get() = _binding!!

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentDrawableViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun doBusiness(context: Context?) {
        //清除画板
        binding.clear.setOnClickListener {
            binding.drawView.clear()
        }

        //导出图像
        binding.output.setOnClickListener{
            //动态申请外部权限
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                outputImage()
            } else {
                ActivityCompat.requestPermissions(requireActivity(),
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            }
        }

        //前进一步
        binding.forward.setOnClickListener {
            binding.drawView.forward()
        }

        //回退一步
        binding.back.setOnClickListener {
            binding.drawView.back()
        }

        //设置笔触粗细
        binding.setWidth.setOnClickListener {
            binding.drawView.mPaintWidth = binding.width.text.toString().toFloat()
        }

        //设置笔触颜色
        binding.setStroke.setOnClickListener {
            binding.drawView.mPaintColor = Color.parseColor(binding.strokeColor.text.toString())
        }

        //设置背景颜色
        binding.setCanvasColor.setOnClickListener {
            binding.drawView.mBackgroundColor = Color.parseColor(binding.canvasColor.text.toString())
        }
    }

    private fun outputImage() = try {
        @Suppress("DEPRECATION")
        val path = Environment.getExternalStorageDirectory().absolutePath + "/" + binding.path.text.toString().trim()
        //设置是否清除边缘空白
        binding.drawView.isClearBlank = true
        binding.drawView.output(path)
        Toast.makeText(requireContext(), "保存成功$path", Toast.LENGTH_SHORT).show()
    }catch (e: IOException) {
        e.printStackTrace()
        Toast.makeText(requireContext(), "保存失败" + e.message, Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    outputImage()
                } else {
                    Toast.makeText(requireContext(), "请通过授权", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}