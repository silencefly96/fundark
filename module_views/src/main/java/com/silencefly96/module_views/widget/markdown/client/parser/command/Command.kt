package com.silencefly96.module_views.widget.markdown.client.parser.command

import android.text.SpannableString
import android.text.TextUtils
import com.silencefly96.module_views.widget.markdown.client.parser.Types
import com.silencefly96.module_views.widget.markdown.client.parser.regex.block.BlockRegex
import com.silencefly96.module_views.widget.markdown.client.parser.section.SectionCache
import com.silencefly96.module_views.widget.markdown.client.parser.strategies.StrategyContext
import com.silencefly96.module_views.widget.markdown.client.parser.strategies.StrategyFactory

interface Command {
    fun execute()
}

/**
 * 获取markdown文本的命令
 */
class ObtainContentCommand(
    private val request: ParseRequest,
    private val content: String
): Command {
    override fun execute() {
        request.content = content
    }
}

/**
 * 将文本数据切割成行的命令
 */
class ParseToLinesCommand(private val request: ParseRequest): Command {
    override fun execute() {
        request.notifyStateChange(ParseRequest.STATE_READING)
        val rawLines = request.content.reader().readLines()
        request.lines.apply { addAll(rawLines) }
    }
}

/**
 * 执行将行处理成section的命令
 */
class ParseToSectionCommand(
    private val request: ParseRequest,
    private val blockRegexesMap: MutableMap<Int, BlockRegex>,
    private val strategyFactory: StrategyFactory
): Command {
    override fun execute() {
        request.notifyStateChange(ParseRequest.STATE_PARSING)
        with(request) {
            // LinkedList的size是单独变量记录的，不需要遍历链表
            while (lines.size > 0) {
                val first = lines.peek()!!

                // 解析类型
                var type = Types.BLOCK.PARAGRAPH
                for (entry in blockRegexesMap) {
                    if (first.matches(Regex(entry.value.getRegex()))) {
                        type = entry.key
                        break
                    }
                }

                // 通过策略分割内容，因为一个section可能跨多行
                val strategy = strategyFactory.getStrategy(type)
                // 通过策略拿到可能多行的字符串，并从LinkedList种pop对应的多行
                val sectionStr = StrategyContext.executeStrategy(strategy, lines)
                if (!TextUtils.isEmpty(sectionStr)) {

                    // 通过原型模式复制对象，并重新赋值
                    SectionCache.getSectionPrototype(type)?.also {
                        it.rawContent = sectionStr
                        it.spannableStr = it.transformer.transform(SpannableString(sectionStr))
                        sections.add(it)
                    }
                }
            }
        }

        request.notifyStateChange(ParseRequest.STATE_LOADED)
    }
}