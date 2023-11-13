package com.silencefly96.module_views.widget.markdown.client.parser.strategies

import java.util.LinkedList


interface Strategy {
    fun handle(source: LinkedList<String>): String
}

/**
 * 标题
 */
class HeaderStrategy: Strategy {
    override fun handle(source: LinkedList<String>): String {
        TODO()
    }
}

/**
 * 段落
 */
class ParagraphStrategy: Strategy {
    override fun handle(source: LinkedList<String>): String {
        TODO()
    }
}

/**
 * 引用
 */
class ReferenceStrategy: Strategy {
    override fun handle(source: LinkedList<String>): String {
        TODO()
    }
}

/**
 * 代码
 */
class CodeStrategy: Strategy {
    override fun handle(source: LinkedList<String>): String {
        TODO()
    }
}

/**
 * 有序列表
 */
class OlStrategy: Strategy {
    override fun handle(source: LinkedList<String>): String {
        TODO()
    }
}

/**
 * 无序列表
 */
class UlStrategy: Strategy {
    override fun handle(source: LinkedList<String>): String {
        TODO()
    }
}