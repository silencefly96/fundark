package com.silencefly96.module_views.widget.markdown.client.adapter

import android.view.View
import com.silencefly96.module_views.widget.markdown.client.parser.section.Section

/**
 * 装饰器模式，为SectionAdapter增加换成功能
 */
class AdapterDecorator: SectionAdapter() {

    // 和代理的区别是，装饰器模式是增加功能，更像一对多，代理模式是一对一的功能控制
    var adapter: SectionAdapter? = null

    override fun getView(section: Section): View {
        // 必须先设置adapter才能进行装饰
        if (null == adapter) {
            throw IllegalStateException("have you set the adapter before decorate?")
        }

        // 装饰: 缓存adapter
        cache(section.type, adapter!!)

        return adapter!!.getView(section)
    }
}