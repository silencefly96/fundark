@file:Suppress("unused")

package com.silencefly96.module_views.game.airplane

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import com.silencefly96.module_views.R

/**
 * 飞机大战 GameView
 *
 * 1，载入界面配置，设置界面信息view
 * 2，载入精灵配置，
 * 3，启动游戏controller
 * 4，启动手势控制逻辑
 */
class AirplaneFightGameView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
): ViewGroup(context, attributeSet, defStyleAttr) {

    // 界面信息view
    private val gameInfoView: GameInfoView

    // 默认生命值大小
    private val defaultLiveSize: Int


    init {
        // 读取配置
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.AirplaneFightGameView)
        defaultLiveSize = typedArray.getInteger(R.styleable.AirplaneFightGameView_liveSize, 3)
        typedArray.recycle()

        // 创建游戏信息view并设置
        gameInfoView = GameInfoView(context)
        gameInfoView.liveSize = defaultLiveSize
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }


    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {

    }
}