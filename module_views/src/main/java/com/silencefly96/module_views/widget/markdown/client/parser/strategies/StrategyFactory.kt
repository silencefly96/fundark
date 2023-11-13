package com.silencefly96.module_views.widget.markdown.client.parser.strategies

import com.silencefly96.module_views.widget.markdown.client.parser.Types

/**
 * 根据类型生成获取section的Strategy
 */
open class StrategyFactory {

    companion object{
        // 饿汉式单例
        private var INSTANCE = StrategyFactory()
        fun getInstance() = INSTANCE
    }

    open fun getStrategy(type: Int): Strategy {
        return when(type) {
            // 标题
            Types.BLOCK.HEADER -> HeaderStrategy()
            // 段落
            Types.BLOCK.PARAGRAPH -> ParagraphStrategy()
            // 引用
            Types.BLOCK.REFERENCE -> ReferenceStrategy()
            // 代码
            Types.BLOCK.CODE -> CodeStrategy()
            // 有序列表
            Types.BLOCK.OL -> OlStrategy()
            // 无序列表
            Types.BLOCK.UL -> UlStrategy()
            else -> throw IllegalArgumentException("getBlockRegex error type: $type")
        }
    }
}