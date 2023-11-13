package com.silencefly96.module_views.widget.markdown.client.parser.section

import com.silencefly96.module_views.widget.markdown.client.parser.transformer.BoldTransformer
import com.silencefly96.module_views.widget.markdown.client.parser.transformer.InlineTransformer
import com.silencefly96.module_views.widget.markdown.client.parser.transformer.ItalicTransformer
import com.silencefly96.module_views.widget.markdown.client.parser.Types
import com.silencefly96.module_views.widget.markdown.client.parser.transformer.OptimizeTransformer

/**
 * 原型模式，缓存对象用于快速复制
 */
object SectionCache {
    private var hasLoaded = false
    private val sectionMap = HashMap<Int, Section>()

    private fun loadCache() {
        if (hasLoaded) return

        // 载入原型对象
        with(sectionMap) {
            // 段落
            put(Types.BLOCK.PARAGRAPH, ParagraphSection( Types.BLOCK.PARAGRAPH, "").apply {
                transformer.apply {
                    transformers.add(InlineTransformer())
                    transformers.add(OptimizeTransformer())
                }
            })

            // 标题
            put(Types.BLOCK.HEADER, HeaderSection( Types.BLOCK.HEADER, ""))

            // 引用
            put(Types.BLOCK.REFERENCE, ReferenceSection( Types.BLOCK.REFERENCE, "").apply {
                transformer.apply {
                    transformers.add(BoldTransformer())
                    transformers.add(ItalicTransformer())
                }
            })

            // 代码
            put(Types.BLOCK.CODE, CodeSection( Types.BLOCK.CODE, ""))

            // 有序和无序列表
            put(Types.BLOCK.OL, OlSection( Types.BLOCK.OL, "").apply {
                transformer.apply {
                    transformers.add(BoldTransformer())
                    transformers.add(BoldTransformer())
                    transformers.add(ItalicTransformer())
                }
            })
            put(Types.BLOCK.UL, UlSection( Types.BLOCK.UL, "").apply {
                transformer.apply {
                    transformers.add(BoldTransformer())
                    transformers.add(BoldTransformer())
                    transformers.add(ItalicTransformer())
                }
            })
        }
        hasLoaded = true
    }

    fun getSectionPrototype(type: Int): Section? {
        // 加载原型
        if (!hasLoaded) {
            loadCache()
        }
        
        return sectionMap[type]
    }

    fun addCache(type: Int, section: Section) {
        sectionMap[type] = section
    }
}