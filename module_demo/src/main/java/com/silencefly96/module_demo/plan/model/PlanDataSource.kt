package com.silencefly96.module_demo.plan.model

interface PlanDataSource {

    suspend fun queryAll(): List<Plan>

    suspend fun query(id: Long): Plan?

    suspend fun add(plan: Plan): Int

    suspend fun delete(id: Long): Int

    suspend fun update(plan: Plan): Int
}