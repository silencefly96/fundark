@file:Suppress("unused")

package com.silencefly96.module_views.widget.markdown.client.parser

@Suppress("MemberVisibilityCanBePrivate")
object Types {
    // 独占一行
    object BLOCK {
        const val BASE = 100

        // 段落
        const val PARAGRAPH = BASE + -1
        // 标题
        const val HEADER = BASE + 0
        // 引用
        const val REFERENCE = BASE + 1
        // 代码
        const val CODE = BASE + 2
        // 有序列表
        const val OL = BASE + 3
        // 无序列表
        const val UL = BASE + 4

        val RANGES = intArrayOf(HEADER, REFERENCE, CODE, OL, UL)
    }

    // 行内
    object INLINE {
        const val BASE = 200

        // 图片
        const val IMAGE = BLOCK.BASE + 0
        // 链接
        const val LINK = BLOCK.BASE + 1
        // 加粗
        const val BOLD = BLOCK.BASE + 2
        // 斜体
        const val ITALIC = BLOCK.BASE + 3

        val RANGES = intArrayOf(IMAGE, LINK, BOLD, ITALIC)
    }
}