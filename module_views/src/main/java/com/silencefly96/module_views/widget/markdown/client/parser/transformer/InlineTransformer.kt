package com.silencefly96.module_views.widget.markdown.client.parser.transformer

import android.text.SpannableString

class InlineTransformer: Transformer() {

    init {
        // 添加子transform
        transformers = ArrayList<Transformer>(4).apply {
            add(ImageTransformer())
            add(LinkTransformer())
            add(BoldTransformer())
            add(ItalicTransformer())
        }
    }
}

class ImageTransformer: Transformer() {
    override fun transform(content: SpannableString): SpannableString {
        TODO("Not yet implemented")
    }
}

class LinkTransformer: Transformer() {
    override fun transform(content: SpannableString): SpannableString {
        TODO("Not yet implemented")
    }
}

class BoldTransformer: Transformer() {
    override fun transform(content: SpannableString): SpannableString {
        TODO("Not yet implemented")
    }
}

class ItalicTransformer: Transformer() {
    override fun transform(content: SpannableString): SpannableString {
        TODO("Not yet implemented")
    }
}