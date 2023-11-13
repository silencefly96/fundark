package com.silencefly96.module_views.widget.markdown.client.parser.section

import android.text.SpannableString
import com.silencefly96.module_views.widget.markdown.client.parser.transformer.EmptyTransformer
import com.silencefly96.module_views.widget.markdown.client.parser.transformer.Transformer

abstract class Section(
    val type: Int,
    var rawContent: String,

    /**
     * 桥接模式: 增加转换器
     * Section两个变化形式，一是段落的种类，二是段落内要转换的种类
     * 通过适配模式，将 m * n 的变化，转换成 m + n 形式，不用继承，使用成员变量
     */
    open var transformer: Transformer = EmptyTransformer(),
    var spannableStr: SpannableString? = null
): Cloneable {
    override fun clone(): Section {
        return super.clone() as Section
    }
}

/**
 * 标题
 */
class HeaderSection(
    type: Int,
    content: String
): Section(type, content) {

}

/**
 * 段落
 */
class ParagraphSection(
    type: Int,
    content: String,
): Section(type, content) {

}

/**
 * 引用
 */
class ReferenceSection(
    type: Int,
    content: String,
): Section(type, content) {

}

/**
 * 代码
 */
class CodeSection(
    type: Int,
    content: String
): Section(type, content) {

}

/**
 * 有序列表
 */
class OlSection(
    type: Int,
    content: String
): Section(type, content) {

}

/**
 * 无序列表
 */
class UlSection(
    type: Int,
    content: String
): Section(type, content) {

}