package com.silencefly96.module_views.game

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import androidx.core.animation.addListener
import com.silencefly96.module_views.R
import com.silencefly96.module_views.game.base.BaseGameView
import kotlin.math.pow

class FlyEggGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
): BaseGameView(context, attrs, defStyleAttr) {

    companion object {
        // 运动类型
        const val TYPE_HERO =   1
        const val TYPE_UNMOVE = 2
        const val TYPE_MOVE =   3

        // 页面状态
        const val STATE_NORMAL = 1
        const val STATE_FLYING = 2
        const val STATE_SCROLL = 3

        // 认定碰撞的距离
        const val COLLISION_DISTANCE = 30

        // 上下左右padding值
        const val VALUE_PADDING = 50

        // 运动倒计时，即3s内运动完
        const val FLY_MOVE_DEFAULT_COUNT = 3000 / GAME_FLUSH_TIME.toInt()
        // TYPE_MOVE运动周期
        const val TYPE_MOVE_DEFAULT_COUNT = 6000 / GAME_FLUSH_TIME.toInt()
    }

    // 需要绘制的sprite
    private val mDisplaySprites: MutableList<Sprite> = ArrayList()

    // 主角
    private val mHeroSprite: Sprite = Sprite().apply {
        mDisplaySprites.add(this)
    }

    // 主角坑位
    private var mHeroBoat: Sprite? = null

    // 主角状态
    private var mState = STATE_NORMAL

    // 主角飞起最大高度
    private var mHeroFlyHeight: Int = 0

    // 移动画面的动画
    private lateinit var mAnimator: ValueAnimator
    private var mScrollVauleLast = 0f
    private var mScorllValue = 0f

    // 两种掩图
    private val mEggMask: Bitmap = drawable2Bitmap(R.drawable.ic_node)
    private val mBoatMask: Bitmap = drawable2Bitmap(R.drawable.ic_boat)

    override fun load(w: Int, h: Int) {
        // 设置主角位置
        mHeroSprite.apply {
            posX = w / 2
            posY = h - VALUE_PADDING
            mask = mEggMask
            type = TYPE_HERO    // 英雄类型
        }

        // 主角初始化坐的船
        mHeroBoat = getBoat(TYPE_UNMOVE, 0, w, h).apply {
            mDisplaySprites.add(this)
        }

        // 主角飞起最大高度，比下一个跳板高一点
        mHeroFlyHeight = (h - VALUE_PADDING * 2) / 2 + VALUE_PADDING * 2

        // 构建地图
        val map = arrayOf(TYPE_UNMOVE, TYPE_MOVE, TYPE_UNMOVE, TYPE_MOVE, TYPE_UNMOVE)
        for (i in 0..4) {
            mDisplaySprites.add(getBoat(map[i], i + 1, w, h))
        }

        // 页面动画
        mAnimator = ValueAnimator.ofFloat(0f, h - VALUE_PADDING * 2f).apply {
            duration = 3000
            addUpdateListener {
                mScorllValue = mScrollVauleLast + it.animatedValue as Float
            }
            addListener(onEnd = {
                mState = STATE_NORMAL
                mScrollVauleLast += h - VALUE_PADDING * 2f
                Log.d("TAG", "onEnd: $mScrollVauleLast")
            })
        }
    }

    private fun getBoat(boatType: Int, index: Int, w: Int, h: Int): Sprite {
        return Sprite().apply {
            // 水平居中
            posX = w / 2
            // 高度从底部往上排列，屏幕放两个boat，上下空出VALUE_PADDING
            posY = (h - VALUE_PADDING) - index * (h - VALUE_PADDING * 2) / 2
            type = boatType
            mask = mBoatMask
            // 对应水平居中
            moveCount = TYPE_MOVE_DEFAULT_COUNT / 2
        }
    }

    override fun reload(w: Int, h: Int) {
        mDisplaySprites.clear()
        mState = STATE_NORMAL
        mDisplaySprites.add(mHeroSprite)
        mScorllValue = 0f
        mScrollVauleLast = 0f
        load(w, h)
    }

    override fun drawGame(canvas: Canvas, paint: Paint) {
        mDisplaySprites.forEach {
            drawSprite(it, canvas, paint)
        }
    }

    // 在原来的基础上增加页面滚动值
    override fun drawSprite(sprite: Sprite, canvas: Canvas, paint: Paint) {
        sprite.mask?.let { mask ->
            canvas.drawBitmap(mask, sprite.posX - mask.width / 2f,
                sprite.posY - mask.height / 2f + mScorllValue, paint)
        }
    }

    override fun onMoveUp(dir: Int): Boolean {
        // 起飞，运动倒计时
        if (mState == STATE_NORMAL) {
            mState = STATE_FLYING
            mHeroSprite.moveCount = FLY_MOVE_DEFAULT_COUNT
        }
        return false
    }

    override fun handleGame(gameView: BaseGameView): Boolean {
        // 如果页面在滚动，不处理逻辑
        if (mState == STATE_SCROLL) {
            return false
        }

        // 移动所有精灵
        for (sprite in mDisplaySprites) {
            moveBoat(sprite)

            if (checkSite(sprite)) {
                // 坐上了船
                // mDisplaySprites.remove(mHeroBoat)
                mHeroBoat = sprite
                mHeroSprite.moveCount = 0
                mState = STATE_NORMAL
                // 每次到最上面一个船时，移动页面
                if (sprite.posY + mScorllValue <= VALUE_PADDING) {
                    mState = STATE_SCROLL
                    mAnimator.start()
                }
                break
            }
        }

        return mState == STATE_FLYING && mHeroSprite.moveCount == 0
    }

    private fun moveBoat(sprite: Sprite) {
        when(sprite.type) {
            TYPE_HERO -> moveHero()
            TYPE_UNMOVE -> {}
            TYPE_MOVE -> {
                // 根据moveCount线性移动
                sprite.moveCount = (sprite.moveCount + 1) % TYPE_MOVE_DEFAULT_COUNT
                sprite.posX = (width - VALUE_PADDING * 2) / TYPE_MOVE_DEFAULT_COUNT * sprite.moveCount
            }
        }
    }

    private fun moveHero() {
        if (mHeroSprite.moveCount > 0) {
            // 飞行时移动
            val moveCount = mHeroSprite.moveCount
            // 每次叠加差值
            mHeroSprite.posY -= ((getFlyHeight(moveCount) - getFlyHeight(moveCount + 1)))
            mHeroSprite.moveCount--
        }else {
            // 不飞行时，跟随坐的船移动
            mHeroSprite.posX = mHeroBoat!!.posX
            mHeroSprite.posY = mHeroBoat!!.posY
        }
    }

    private fun getFlyHeight(moveCount: Int): Int {
        if (moveCount in 0..FLY_MOVE_DEFAULT_COUNT) {
            // 在y轴上执行抛物线
            val half = FLY_MOVE_DEFAULT_COUNT / 2
            val dx = moveCount.toDouble()
            val dy = - (dx - half).pow(2.0) * mHeroFlyHeight / half.toDouble().pow(2.0) + mHeroFlyHeight
            return dy.toInt()
        }
        return 0
    }

    private fun checkSite(sprite: Sprite): Boolean {
        return if (sprite == mHeroSprite || sprite == mHeroBoat) {
            false
        }else {
            // 角色和船一定距离内，认为坐上了船
            getDistance(sprite.posX, sprite.posY,
                mHeroSprite.posX, mHeroSprite.posY) <= COLLISION_DISTANCE
        }
    }

    public override fun recycle() {
        super.recycle()
        mEggMask.recycle()
        mBoatMask.recycle()
    }
}