package com.silencefly96.module_views.widget.markdown.client.util

abstract class Observable {

    // 观察者集合
    private val observers: MutableList<Observer> = ArrayList()

    // 状态
    private var state: Int = -1

    /**
     * 添加观察者
     */
    fun addObserver(observer: Observer){
        observers.add(observer)
    }

    /**
     * 移除观察者，不使用后务必移除
     */
    fun removeObserver(observer: Observer){
        observers.remove(observer)
    }

    fun notifyStateChange(state: Int) {

        observers.forEach {
            it.updateState(state)
        }
    }
}

interface Observer{
    fun updateState(state: Int)
}