package com.silencefly96.module_views.widget.markdown.client.parser

import com.silencefly96.module_views.widget.markdown.client.parser.command.CommandExecutor
import com.silencefly96.module_views.widget.markdown.client.parser.command.ObtainContentCommand
import com.silencefly96.module_views.widget.markdown.client.parser.command.ParseToSectionCommand
import com.silencefly96.module_views.widget.markdown.client.parser.command.ParseRequest
import com.silencefly96.module_views.widget.markdown.client.parser.command.ParseToLinesCommand
import com.silencefly96.module_views.widget.markdown.client.parser.extension.Extension
import com.silencefly96.module_views.widget.markdown.client.parser.regex.RegexFactory
import com.silencefly96.module_views.widget.markdown.client.parser.regex.RegexProducer
import com.silencefly96.module_views.widget.markdown.client.parser.regex.block.BlockRegex
import com.silencefly96.module_views.widget.markdown.client.parser.regex.inline.InlineRegex
import com.silencefly96.module_views.widget.markdown.client.parser.section.SectionCache
import com.silencefly96.module_views.widget.markdown.client.parser.section.SectionContainer
import com.silencefly96.module_views.widget.markdown.client.parser.strategies.Strategy
import com.silencefly96.module_views.widget.markdown.client.parser.strategies.StrategyFactory
import com.silencefly96.module_views.widget.markdown.client.util.Observable
import com.silencefly96.module_views.widget.markdown.client.util.Observer

/**
 * 将markdown内容转换成section段落集合
 */
@Suppress("unused")
class MarkdownParser: Observable {

    // 生成块级正则表达式的工厂
    private val blockRegexFactory: RegexFactory

    // 生成内联型正则表达式的工厂
    private val inlineRegexFactory: RegexFactory

    // 块级正则表达式对应表
    private val blockRegexesMap: MutableMap<Int, BlockRegex>

    // 内联型正则表达式对应表
    private val inlineRegexesMap: MutableMap<Int, InlineRegex>

    // 生成按行处理策略的工厂
    private val strategyFactory: StrategyFactory

    // 自定义扩展
    private val extensions: List<Extension>

    // 处理状态
    private var parserState: Int = ParseRequest.STATE_IDLE

    constructor(): this(Builder())

    constructor(builder: Builder) {
        blockRegexFactory = builder.blockRegexFactory
        inlineRegexFactory = builder.inlineRegexFactory
        blockRegexesMap = builder.blockRegexesMap
        inlineRegexesMap = builder.inlineRegexesMap
        strategyFactory = builder.strategyFactory
        extensions = builder.extensions
    }

    fun parser(content: String): SectionContainer {
        val request = ParseRequest()
        // 添加观察者获取处理状态
        request.addObserver(object : Observer {
            override fun updateState(state: Int) {
                parserState = state
            }
        })

        // 通过命令模式执行
        val commandExecutor = CommandExecutor()
        commandExecutor.takeCommand(ObtainContentCommand(request, content))
        commandExecutor.takeCommand(ParseToLinesCommand(request))
        commandExecutor.takeCommand(
            ParseToSectionCommand(request, blockRegexesMap, strategyFactory)
        )
        commandExecutor.execute()

        return request.getResult()
    }

    /**
     * 建造者模式
     */
    class Builder {
        var blockRegexFactory: RegexFactory
        var inlineRegexFactory: RegexFactory
        var blockRegexesMap: MutableMap<Int, BlockRegex>
        var inlineRegexesMap: MutableMap<Int, InlineRegex>
        var strategyFactory: StrategyFactory
        val extensions: MutableList<Extension>

        init {
            // 获取两种格式的工厂
            val regexProducer = RegexProducer.getInstance()
            blockRegexFactory = regexProducer.getRegexFactory(RegexProducer.BLOCK)
            inlineRegexFactory = regexProducer.getRegexFactory(RegexProducer.INLINE)
            blockRegexesMap = HashMap()
            inlineRegexesMap = HashMap()
            strategyFactory = StrategyFactory.getInstance()
            extensions = ArrayList()
        }

        /**
         * 设置块级正则表达式工厂
         */
        fun blockRegexFactory(blockRegexFactory: RegexFactory): Builder {
            this.blockRegexFactory = blockRegexFactory
            return this
        }

        /**
         * 设置生成内联型正则表达式的工厂
         */
        fun inlineRegexFactory(inlineRegexFactory: RegexFactory): Builder {
            this.inlineRegexFactory = inlineRegexFactory
            return this
        }

        /**
         * 设置生成按行处理策略的工厂
         */
        fun strategyFactory(strategyFactory: StrategyFactory): Builder {
            this.strategyFactory = strategyFactory
            return this
        }

        /**
         * 添加扩展
         */
        fun addExtension(extension: Extension): Builder {
            this.extensions.add(extension)
            return this
        }

        /**
         * 添加扩展集合
         */
        fun addExtension(extensions: List<Extension>): Builder {
            this.extensions.addAll(extensions)
            return this
        }

        fun build(): MarkdownParser {
            // 对策略工厂进行代理，增加扩展功能
            this.strategyFactory = ProxyStrategyFactory(this.strategyFactory)

            // 根据扩展增加section原型
            extensions.forEach {
                SectionCache.addCache(it.getType(), it.getSectionPrototype())
            }

            // 建立type到regex的索引，即Map
            makeRegexIndexes()

            return MarkdownParser(this)
        }

        // 通过代理生成转换section策略的工厂，添加扩展
        inner class ProxyStrategyFactory(private val strategyFactory: StrategyFactory)
            : StrategyFactory() {

            override fun getStrategy(type: Int): Strategy {
                var result: Strategy? = null

                // 优先从扩展里面取
                extensions.forEach {
                    if (it.getType() == type) {
                        result = it.getStrategy()
                    }
                }

                return if (null != result) {
                    result!!
                }else {
                    strategyFactory.getStrategy(type)
                }
            }
        }

        private fun makeRegexIndexes() {
            // 建立type-Regex的对应表
            Types.BLOCK.RANGES.forEach { type ->
                blockRegexesMap[type] = blockRegexFactory.getBlockRegex(type)!!
            }
            Types.INLINE.RANGES.forEach { type ->
                inlineRegexesMap[type] = inlineRegexFactory.getInlineRegex(type)!!
            }

            // 增加扩展，可以覆盖原有设置
            extensions.forEach {
                if (it.getRegex() is BlockRegex) {
                    blockRegexesMap[it.getType()] = it.getRegex() as BlockRegex
                } else {
                    inlineRegexesMap[it.getType()] = it.getRegex() as InlineRegex
                }
            }
        }
    }
}