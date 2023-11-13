package com.silencefly96.module_views.widget.markdown.client.parser.regex

import com.silencefly96.module_views.widget.markdown.client.parser.regex.block.BlockRegex
import com.silencefly96.module_views.widget.markdown.client.parser.regex.inline.InlineRegex


abstract class RegexFactory {
    abstract fun getBlockRegex(type: Int): BlockRegex?
    abstract fun getInlineRegex(type: Int): InlineRegex?
}