package com.silencefly96.module_views.widget.markdown.client.adapter

import android.view.View
import com.silencefly96.module_views.widget.markdown.client.parser.section.Section

/**
 * 将section适配到view
 */
abstract class SectionAdapter {

    companion object{
        /**
         * 享元模式，大量的需求访问同一个对象
         */
        private val adapters: MutableMap<Int, SectionAdapter> = HashMap()
        fun getAdapter(type: Int): SectionAdapter? = adapters[type]
        fun cache(type: Int, adapter: SectionAdapter) = adapters.put(type, adapter)
    }

    abstract fun getView(section: Section): View
}