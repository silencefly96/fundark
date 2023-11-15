@file:Suppress("unused")

package com.silencefly96.module_views.widget.markdown.client.adapter

import android.view.View
import com.silencefly96.module_views.widget.markdown.client.adapter.adapters.*
import com.silencefly96.module_views.widget.markdown.client.parser.section.*

/**
 * 外观模式提供section适配
 */
open class AdapterPack {

    // 用来装饰adapter的类
    protected val adapterDecorator = AdapterDecorator()

    /**
     * 适配代码型段落
     */
    fun adapterCodeSection(section: CodeSection): View {
        var adapter = SectionAdapter.getAdapter(section.type)
        if (null == adapter) {
            adapter = CodeAdapter()
        }

        // 装饰
        adapterDecorator.adapter = adapter
        return adapterDecorator.getView(section)
    }

    /**
     * 适配标题型段落
     */
    fun adapterHeaderSection(section: HeaderSection): View {
        var adapter = SectionAdapter.getAdapter(section.type)
        if (null == adapter) {
            adapter = HeaderAdapter()
        }

        // 装饰
        adapterDecorator.adapter = adapter
        return adapterDecorator.getView(section)
    }

    /**
     * 适配有序列表型段落
     */
    fun adapterOlSection(section: OlSection): View {
        var adapter = SectionAdapter.getAdapter(section.type)
        if (null == adapter) {
            adapter = OlAdapter()
        }

        // 装饰
        adapterDecorator.adapter = adapter
        return adapterDecorator.getView(section)
    }

    /**
     * 适配段落型段落
     */
    fun adapterParagraphSection(section: ParagraphSection): View {
        var adapter = SectionAdapter.getAdapter(section.type)
        if (null == adapter) {
            adapter = ParagraphAdapter()
        }

        // 装饰
        adapterDecorator.adapter = adapter
        return adapterDecorator.getView(section)
    }

    /**
     * 适配引用型段落
     */
    fun adapterReferenceSection(section: ReferenceSection): View {
        var adapter = SectionAdapter.getAdapter(section.type)
        if (null == adapter) {
            adapter = ReferenceAdapter()
        }

        // 装饰
        adapterDecorator.adapter = adapter
        return adapterDecorator.getView(section)
    }

    /**
     * 适配无序列表型段落
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun adapterUlSection(section: UlSection): View {
        var adapter = SectionAdapter.getAdapter(section.type)
        if (null == adapter) {
            adapter = UlAdapter()
        }

        // 装饰
        adapterDecorator.adapter = adapter
        return adapterDecorator.getView(section)
    }
}