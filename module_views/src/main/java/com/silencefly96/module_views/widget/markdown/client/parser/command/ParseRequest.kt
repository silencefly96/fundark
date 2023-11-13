package com.silencefly96.module_views.widget.markdown.client.parser.command

import com.silencefly96.module_views.widget.markdown.client.parser.section.SectionContainer
import com.silencefly96.module_views.widget.markdown.client.util.Observable
import java.util.LinkedList

class ParseRequest(): Observable() {

    companion object{
        // 数据加载状态
        const val STATE_IDLE            = 0
        const val STATE_READING         = 1
        const val STATE_PARSING         = 2
        const val STATE_LOADED          = 3
    }

    var content = ""
    var lines = LinkedList<String>()
    val sections: SectionContainer = SectionContainer()

    fun getResult(): SectionContainer {
        return sections
    }
}