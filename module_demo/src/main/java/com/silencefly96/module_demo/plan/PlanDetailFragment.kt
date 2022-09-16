package com.silencefly96.module_demo.plan

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.silencefly96.module_demo.databinding.FragmentPlanDetailBinding

class PlanDetailFragment(val viewModel: PlanViewModel): Fragment() {

    private var _binding: FragmentPlanDetailBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlanDetailBinding.inflate(inflater, container, false)

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

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(viewModel: PlanViewModel) = PlanDetailFragment(viewModel)
    }


    interface OnOperateListener {
        fun all()
        fun add()
        fun delete()
        fun query()
        fun update()
    }
}