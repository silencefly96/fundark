package com.silencefly96.module_demo.plan.model.remote

import com.silencefly96.module_demo.plan.model.Plan
import retrofit2.http.*

interface PlanService {

    @GET("tasks/query")
    suspend fun queryAll(): List<Plan>

    @GET("task/query/{id}")
    suspend fun query(@Path("id") id: Long): Plan?

    @POST("task/add")
    suspend fun add(@Body plan: Plan): Result

    @DELETE("task/delete/{id}")
    suspend fun delete(@Path("id") id: Long): Result

    @PUT("task/update/{id}")
    suspend fun update(@Path("id") id: Long, @Body plan: Plan): Result
}