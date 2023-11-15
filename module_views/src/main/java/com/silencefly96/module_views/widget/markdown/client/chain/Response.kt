package com.silencefly96.module_views.widget.markdown.client.chain

import android.view.View

class Response {

    val mViews: MutableList<View> = ArrayList()

    fun getViews(): List<View> {
        return mViews
    }
}