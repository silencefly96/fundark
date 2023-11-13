package com.silencefly96.module_views.widget.markdown.client.parser.regex.inline

import com.silencefly96.module_views.widget.markdown.client.parser.Types
import com.silencefly96.module_views.widget.markdown.client.parser.regex.RegexFactory
import com.silencefly96.module_views.widget.markdown.client.parser.regex.block.BlockRegex

class InlineRegexFactory: RegexFactory() {
    override fun getBlockRegex(type: Int): BlockRegex? {
        return null
    }

    override fun getInlineRegex(type: Int): InlineRegex {
        return when(type) {
            // 图片
            Types.INLINE.IMAGE -> ImageRegex()
            // 链接
            Types.INLINE.LINK -> LinkRegex()
            // 加粗
            Types.INLINE.BOLD -> BoldRegex()
            // 斜体
            Types.INLINE.ITALIC -> ItalicRegex()
            else -> throw IllegalArgumentException("getBlockRegex error type: $type")
        }
    }
}