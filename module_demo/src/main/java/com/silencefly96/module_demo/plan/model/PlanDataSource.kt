package com.silencefly96.module_demo.plan.model

import com.silencefly96.module_demo.plan.model.Plan

interface PlanDataSource {

    suspend fun queryAll(): List<Plan>

    suspend fun query(id: Long): Plan?

    suspend fun add(plan: Plan): Int

    suspend fun delete(id: Long): Int

    suspend fun update(plan: Plan): Int
}