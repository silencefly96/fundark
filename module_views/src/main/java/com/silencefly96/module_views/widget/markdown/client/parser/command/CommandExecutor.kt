package com.silencefly96.module_views.widget.markdown.client.parser.command

/**
 * 执行命令的类
 */
class CommandExecutor {
    private val commands: MutableList<Command> = ArrayList()

    /**
     * 添加命令
     */
    fun takeCommand(command: Command) {
        commands.add(command)
    }

    /**
     * 执行全部操作
     */
    fun execute() {
        commands.forEach {
            it.execute()
        }
        commands.clear()
    }
}