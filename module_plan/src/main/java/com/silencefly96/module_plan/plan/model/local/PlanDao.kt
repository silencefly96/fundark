package com.silencefly96.module_plan.plan.model.local

import androidx.room.*
import com.silencefly96.module_plan.plan.model.Plan

@Dao
interface PlanDao {

    @Query("SELECT * FROM `plan`")
    fun queryAll(): List<Plan>

    @Query("SELECT * FROM `plan` WHERE id LIKE :id LIMIT 1")
    fun query(id: Long): Plan?

    @Insert
    fun insert(plan: Plan)

    @Query("DELETE FROM `plan` WHERE id LIKE :id")
    fun delete(id: Long)

    @Update
    fun update(plan: Plan)
}