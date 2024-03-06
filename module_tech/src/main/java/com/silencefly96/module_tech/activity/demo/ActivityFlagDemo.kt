@file:Suppress("unused")

package com.silencefly96.module_tech.activity.demo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.ArrayMap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.silencefly96.module_base.base.BaseFragment
import com.silencefly96.module_tech.databinding.ActivityTestBinding
import com.silencefly96.module_tech.activity.flag.TestActivityA
import okhttp3.OkHttpClient
import java.lang.ref.WeakReference


class ActivityFlagDemo: BaseFragment() {

    companion object{

        // 通过收集activity的形式维护堆栈信息
        val sActivities: MutableList<WeakReference<Activity>> = ArrayList()
        fun addActivity(activity: Activity) {
            sActivities.add(WeakReference(activity))
        }
        fun removeActivity(activity: Activity) {
            var select = -1
            sActivities.forEachIndexed { index, weakReference ->
                if(weakReference.get() == activity) {
                    select = index
                }
            }
            if (select > 0) {
                sActivities.removeAt(select)
            }
        }

        /**
         * 通过反射activityThread获取当前应用所有activity
         *
         * 得到的activity是乱序的，效果不大
         * mActivities收集的逻辑（put）是在onCreate和onStart之后，需要在resume中获取
         */
        @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
        fun getAllActivities(): List<WeakReference<Activity>> {
            // 结果
            val list: MutableList<WeakReference<Activity>> = ArrayList()

            try {
                // 获取主线程对象
                val activityThread = Class.forName("android.app.ActivityThread")
                val currentActivityThread = activityThread.getDeclaredMethod("currentActivityThread")
                currentActivityThread.isAccessible = true
                val activityThreadObject = currentActivityThread.invoke(null)

                // 获取activityThread持有的mActivities
                // 源码如下: final ArrayMap<IBinder, ActivityClientRecord> mActivities = new ArrayMap<>();
                val mActivitiesField = activityThread.getDeclaredField("mActivities")
                mActivitiesField.isAccessible = true
                val mActivities = mActivitiesField.get(activityThreadObject) as ArrayMap<*, *>

                // 将activity加到弱引用列表中
                mActivities.entries.forEach {

                    // 从ActivityClientRecord中取activity
                    val activityClientRecordClass = it.value!!.javaClass
                    val activityField = activityClientRecordClass.getDeclaredField("activity")
                    activityField.isAccessible = true
                    val o = activityField.get(it.value)
                    list.add(WeakReference(o as Activity))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return list
        }

        /**
         * 获取activity信息字符串
         */
        fun getActivitiesInfo(): String {
            val sb = StringBuilder()
//            val activities = getAllActivities()
            val activities = sActivities
            activities.sortedBy {
                it.get()?.taskId
            }
            activities.forEach {
                it.get()?.let {  activity ->
                    sb.append("taskId: ${activity.taskId}")
                    sb.append(", name: ${activity.localClassName.let {name->
                        val index = name.lastIndexOf(".")
                        if (index > 0) name.substring(index) else name
                    }}\n")
                }
            }
            return sb.toString()
        }
    }

    private var _binding: ActivityTestBinding? = null
    private val binding get() = _binding!!

    private lateinit var client: OkHttpClient

    override fun bindView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = ActivityTestBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun doBusiness(context: Context?) {
        binding.button.setOnClickListener {
            startActivity(Intent(getContext(), TestActivityA::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()

        val info = getActivitiesInfo()
        // 输出信息
        binding.content.text =
                "current: MainActivity\n" +
                "next: TestActivityA\n" +
                "taskAffinity: com.test.TestA\n" +
                "launchMode: \n" +
                "flags: \n" +
                "\t\tFLAG_ACTIVITY_NEW_TASK\n" +
                "current activity info:\n\n" +
                info
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}