package com.silencefly96.module_views.widget.markdown.client.parser.regex.block

import com.silencefly96.module_views.widget.markdown.client.parser.regex.Regex

/**
 * 按块级元素读取元素的正则表达式
 */
interface BlockRegex: Regex {

}

/**
 * 标题
 */
class HeaderRegex: BlockRegex {
    override fun getRegex() = "^(#+)\\s+(.*)"
}

/**
 * 段落
 */
class ParagraphRegex: BlockRegex {
    override fun getRegex() = "^(?!\\s*$).+"
}

/**
 * 引用
 */
class ReferenceRegex: BlockRegex {
    override fun getRegex() = "^>\\s+(.*)"
}

/**
 * 代码
 */
class CodeRegex: BlockRegex {
    override fun getRegex() = "^```(.*)"
}

/**
 * 有序列表
 */
class OlRegex: BlockRegex {
    override fun getRegex() = "^\\d+\\.\\s+(.*)"
}

/**
 * 无序列表
 */
class UlRegex: BlockRegex {
    override fun getRegex() = "^[*-]\\s+(.*)"
}