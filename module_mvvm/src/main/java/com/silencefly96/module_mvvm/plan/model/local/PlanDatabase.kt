package com.silencefly96.module_mvvm.plan.model.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.silencefly96.module_mvvm.plan.model.Plan

@Database(entities = [Plan::class], version = 1, exportSchema=false)
abstract class PlanDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao

    companion object {

        private var INSTANCE: PlanDatabase? = null

        private val lock = Any()

        fun getInstance(context: Context): PlanDatabase {
            synchronized(lock) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.applicationContext,
                            PlanDatabase::class.java, "plan.db")
                            .build()
                }
                return INSTANCE!!
            }
        }
    }
}