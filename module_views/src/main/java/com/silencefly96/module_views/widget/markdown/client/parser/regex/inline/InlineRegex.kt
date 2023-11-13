package com.silencefly96.module_views.widget.markdown.client.parser.regex.inline

import com.silencefly96.module_views.widget.markdown.client.parser.regex.Regex

/**
 * 按行内元素读取元素的正则表达式
 */
interface InlineRegex: Regex {

}

/**
 * 图片
 */
class ImageRegex: InlineRegex {
    override fun getRegex() = "!\\[(.*?)\\]\\((.*?)\\)"
}

/**
 * 链接
 */
class LinkRegex: InlineRegex {
    override fun getRegex() = "\\[(.*?)\\]\\((.*?)\\)"
}

/**
 * 加粗
 */
class BoldRegex: InlineRegex {
    override fun getRegex() = "\\*\\*(.*?)\\*\\*"
}

/**
 * 斜体
 */
class ItalicRegex: InlineRegex {
    override fun getRegex() = "\\*(.*?)\\*"
}