package com.silencefly96.module_views.widget.markdown.client.parser.regex

import com.silencefly96.module_views.widget.markdown.client.parser.regex.block.BlockRegexFactory
import com.silencefly96.module_views.widget.markdown.client.parser.regex.inline.InlineRegexFactory

@Suppress("MemberVisibilityCanBePrivate")
class RegexProducer {

    companion object{
        const val INLINE = 0
        const val BLOCK = 1

        // 懒汉式单例
        private var INSTANCE: RegexProducer? = null
        fun getInstance(): RegexProducer {
            if (null == INSTANCE) {
                INSTANCE = RegexProducer()
            }
            return INSTANCE!!
        }
    }

    fun getRegexFactory(type: Int): RegexFactory {
        return when(type) {
            INLINE -> InlineRegexFactory()
            BLOCK -> BlockRegexFactory()
            else -> throw IllegalAccessException("getRegexFactory error type: $type")
        }
    }
}