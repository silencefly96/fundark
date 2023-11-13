package com.silencefly96.module_views.widget.markdown.client.parser.transformer

import android.text.SpannableString

/**
 * 将纯文字内容转换成带span的数据
 */
open class Transformer {

    /**
     * 组合模式，一个转换包含多个转换过程
     */
    var transformers: MutableList<Transformer> = ArrayList()

    /**
     * 多次转换
     */
    open fun transform(content: SpannableString): SpannableString {
        var result = content
        transformers.forEach {
            result = it.transform(result)
        }
        return result
    }
}

