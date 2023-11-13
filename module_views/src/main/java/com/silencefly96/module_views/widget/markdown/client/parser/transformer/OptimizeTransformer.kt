package com.silencefly96.module_views.widget.markdown.client.parser.transformer

import android.text.SpannableString

class OptimizeTransformer: Transformer() {

    init {
        // 添加子transform
        transformers = ArrayList<Transformer>(4).apply {
            add(StyleTransformer())
            add(TrimTransformer())
        }
    }
}

class StyleTransformer: Transformer() {
    override fun transform(content: SpannableString): SpannableString {
        TODO("Not yet implemented")
    }
}

class TrimTransformer: Transformer() {
    override fun transform(content: SpannableString): SpannableString {
        TODO("Not yet implemented")
    }
}

