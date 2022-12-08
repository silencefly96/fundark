package com.silencefly96.module_mvvm.plan

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_mvvm.databinding.FragmentPlanTestBinding

class PlanTestFragment(val viewModel: PlanViewModel): BaseFragment() {

    private var _binding: FragmentPlanTestBinding? = null
    private val binding get() = _binding!!

    // 绑定布局
    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentPlanTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    // 业务
    override fun doBusiness(context: Context?) {
        binding.viewmodel = viewModel
        binding.listener = object : OnOperateListener {

            override fun all() {
                Log.e("TAG", "all: ")
                viewModel.getAll().observe(activity as AppCompatActivity) { result ->
                    if (result.isFailure) {
                        binding.output.append("get all plan: fail\n${result.exceptionOrNull()?.message}\n")
                    }

                    val plans = result.getOrNull()
                    plans?.let {
                        binding.output.append("get all plan:\n")
                        for (plan in it)
                            binding.output.append(plan.toString() + "\n")
                    }
                }
            }

            override fun add() {
                Log.e("TAG", "add: ")
                viewModel.add(
                    binding.id.text.toString().toLong(),
                    binding.title.text.toString(),
                    binding.content.text.toString()
                ).observe(activity as AppCompatActivity) {
                    if (it.isFailure) {
                        binding.output.append("add: fail\n${it.exceptionOrNull()?.message}\n")
                    }

                    val result = it.getOrNull()
                    result?.let { id ->
                        binding.output.append("add id = $id\n")
                    }
                }
            }

            override fun delete() {
                Log.e("TAG", "delete: ")
                viewModel.delete(binding.id.text.toString().toLong())
                    .observe(activity as AppCompatActivity) {
                        if (it.isFailure) {
                            binding.output.append("delete: fail\n${it.exceptionOrNull()?.message}\n")
                        }

                        val result = it.getOrNull()
                        result?.let { id ->
                            binding.output.append("delete id = $id\n")
                        }
                    }
            }

            override fun query() {
                Log.e("TAG", "query: ")
                viewModel.query(binding.id.text.toString().toLong())
                    .observe(activity as AppCompatActivity) { result ->
                        if (result.isFailure) {
                            binding.output.append("query: fail\n${result.exceptionOrNull()?.message}\n")
                        }

                        val plan = result.getOrNull()
                        plan?.let {
                            binding.output.append("query plan:\n$it\n")
                        }
                    }
            }

            override fun update() {
                Log.e("TAG", "update: ")
                viewModel.update(
                    binding.id.text.toString().toLong(),
                    binding.title.text.toString(),
                    binding.content.text.toString()
                ).observe(activity as AppCompatActivity) {
                    if (it.isFailure) {
                        binding.output.append("update: fail\n${it.exceptionOrNull()?.message}\n")
                    }

                    val result = it.getOrNull()
                    result?.let { id ->
                        binding.output.append("update id = $id\n")
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(viewModel: PlanViewModel) = PlanTestFragment(viewModel)
    }

    interface OnOperateListener {
        fun all()
        fun add()
        fun delete()
        fun query()
        fun update()
    }
}