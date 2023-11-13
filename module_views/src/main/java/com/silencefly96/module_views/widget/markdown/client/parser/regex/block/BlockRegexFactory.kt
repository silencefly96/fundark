package com.silencefly96.module_views.widget.markdown.client.parser.regex.block

import com.silencefly96.module_views.widget.markdown.client.parser.Types
import com.silencefly96.module_views.widget.markdown.client.parser.regex.RegexFactory
import com.silencefly96.module_views.widget.markdown.client.parser.regex.inline.InlineRegex
import java.lang.IllegalArgumentException

class BlockRegexFactory: RegexFactory() {
    override fun getBlockRegex(type: Int): BlockRegex {
        return when(type) {
            // 标题
            Types.BLOCK.HEADER -> HeaderRegex()
            // 段落
            Types.BLOCK.PARAGRAPH -> ParagraphRegex()
            // 引用
            Types.BLOCK.REFERENCE -> ReferenceRegex()
            // 代码
            Types.BLOCK.CODE -> CodeRegex()
            // 有序列表
            Types.BLOCK.OL -> OlRegex()
            // 无序列表
            Types.BLOCK.UL -> UlRegex()
            else -> throw IllegalArgumentException("getBlockRegex error type: $type")
        }
    }

    override fun getInlineRegex(type: Int): InlineRegex? {
        return null
    }
}