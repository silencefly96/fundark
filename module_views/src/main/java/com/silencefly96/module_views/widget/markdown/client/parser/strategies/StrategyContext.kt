package com.silencefly96.module_views.widget.markdown.client.parser.strategies

import java.util.LinkedList

object StrategyContext {
    fun executeStrategy(strategy: Strategy, lines: LinkedList<String>): String {
        return strategy.handle(lines)
    }
}