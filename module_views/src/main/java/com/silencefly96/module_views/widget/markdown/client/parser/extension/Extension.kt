package com.silencefly96.module_views.widget.markdown.client.parser.extension

import com.silencefly96.module_views.widget.markdown.client.parser.regex.block.BlockRegex
import com.silencefly96.module_views.widget.markdown.client.parser.regex.inline.InlineRegex
import com.silencefly96.module_views.widget.markdown.client.parser.strategies.Strategy
import com.silencefly96.module_views.widget.markdown.client.parser.regex.Regex
import com.silencefly96.module_views.widget.markdown.client.parser.section.Section

/**
 * 使用模板模式增加扩展
 */
sealed interface Extension {
    fun getType(): Int
    fun getRegex(): Regex
    fun getStrategy(): Strategy
    fun getSectionPrototype(): Section
}

interface BlockExtension: Extension {
    override fun getRegex(): BlockRegex
}

interface InlineExtension: Extension {
    override fun getRegex(): InlineRegex
}