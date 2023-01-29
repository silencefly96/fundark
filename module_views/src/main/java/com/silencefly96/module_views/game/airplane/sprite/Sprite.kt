package com.silencefly96.module_views.game.airplane.sprite

import android.view.View

abstract class Sprite(
    var live: Int = 1,
    var posX: Int = 0,
    var posY: Int = 0,
    var mask: View
){
    // 出现
    abstract fun appear()

    // 移动
    abstract fun move()

    // 被攻击
    abstract fun attacked()

    // 碰撞
    abstract fun collide()

    // 死亡
    abstract fun die()
}