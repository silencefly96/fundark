package com.silencefly96.module_mvvm.plan.model

import androidx.room.Entity
import androidx.room.PrimaryKey


//8.129.134.62:8800/tasks/query

@Entity(tableName = "plan")
data class Plan(
        @PrimaryKey(autoGenerate = true)
        var id: Long = 0,

        var state: Int = 0,
        var filter: Int = 0,
        var type: Int = 0,
        var startTime: String = "2021-02-24",
        var duration: Long = 0,
        var endTime: String = "2021-02-24",
        var title: String = "",
        var content: String = ""
)