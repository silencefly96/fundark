package com.silencefly96.module_views.widget.markdown.client.parser.extension

import com.silencefly96.module_views.widget.markdown.client.parser.regex.Regex
import com.silencefly96.module_views.widget.markdown.client.parser.regex.block.BlockRegex
import com.silencefly96.module_views.widget.markdown.client.parser.regex.inline.InlineRegex
import com.silencefly96.module_views.widget.markdown.client.parser.section.Section
import com.silencefly96.module_views.widget.markdown.client.parser.strategies.Strategy
import java.lang.Exception
import java.lang.IllegalArgumentException

/**
 * 解释器模式，把自定义str转换成Extension
 */
class StringExtensionParser {
    fun parseString(str: String): Extension {

        val array = str.split(",")

        // 通过反射得到数据类，任何错误都提示非法参数异常
        val data = StringExtensionData()
        try {
            data.type = array[0].toInt()
            data.regex = Class.forName(array[1]).newInstance() as Regex
            data.strategy =  Class.forName(array[2]).newInstance() as Strategy
            data.section =  Class.forName(array[3]).newInstance() as Section
            data.isBlock = array[4].toBoolean()
        }catch (e: Exception) {
            e.printStackTrace()
            throw IllegalArgumentException("parse string fail")
        }

        return if (data.isBlock!!) {
            object: BlockExtension {
                override fun getRegex(): BlockRegex {
                    return data.regex!! as BlockRegex
                }

                override fun getType(): Int {
                    return data.type
                }

                override fun getStrategy(): Strategy {
                    return data.strategy!!
                }

                override fun getSectionPrototype(): Section {
                    return data.section!!
                }
            }
        }else {
            object: InlineExtension {
                override fun getRegex(): InlineRegex {
                    return data.regex!! as InlineRegex
                }

                override fun getType(): Int {
                    return data.type
                }

                override fun getStrategy(): Strategy {
                    return data.strategy!!
                }

                override fun getSectionPrototype(): Section {
                    return data.section!!
                }
            }
        }
    }
}

data class StringExtensionData(
    var type: Int = -1,
    var regex: Regex? = null,
    var strategy: Strategy? = null,
    var section: Section? = null,
    var isBlock: Boolean? = false
)