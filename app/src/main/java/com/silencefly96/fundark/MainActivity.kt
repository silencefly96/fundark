package com.silencefly96.fundark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.View
import com.silencefly96.fundark.databinding.ActivityMainBinding
import com.silencefly96.module_base.base.BaseActivity

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun bindView(): View {
        binding = ActivityMainBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    fun onTestRegisterZxyReceiver() {
        val cw: ContextWrapper = object : ContextWrapper(this) {
            override fun registerReceiver(
                receiver: BroadcastReceiver?,
                filter: IntentFilter
            ): Intent? {
                Log.d("TAG", "ContextWrapper registerReceiver: ")
                return super.registerReceiver(receiver, filter)
            }
        }
        val receiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d("TAG", "onReceive: " + intent.action)
            }
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        Log.d("TAG", "registerZxyReceiver: invoke before")
        cw.registerReceiver(receiver, intentFilter)
    }
}