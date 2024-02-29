package com.silencefly96.module_views.game

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.core.animation.addListener
import com.silencefly96.module_views.R
import com.silencefly96.module_views.game.base.BaseGameView
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class FlyEggGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
): BaseGameView(context, attrs, defStyleAttr) {

    companion object {
        // 运动类型
        const val TYPE_HERO =   1   // 小球
        const val TYPE_UNMOVE = 2   // 不移动
        const val TYPE_MOVE =   3   // 扫描移动
        const val TYPE_CIRCLE = 4   // 来回移动
        const val TYPE_BEVAL  = 5   // 斜着来回移动
        const val TYPE_SINY   = 6   // 做正弦移动

        // 页面状态
        const val STATE_NORMAL = 1
        const val STATE_FLYING = 2
        const val STATE_SCROLL = 3

        // 认定碰撞的距离
        const val COLLISION_DISTANCE = 30

        // 上下左右padding值
        const val BUTTOM_PADDING = 50
        const val TOP_PADDING = 200
        const val HORIZONTAL_PADDING = 50

        // 飞起来超过下一个船的高度
        const val HERO_OVER_HEIGHT = 100

        // 起飞运动计时，即3s内运动完
        const val HERO_FLY_COUNT = 3000 / GAME_FLUSH_TIME.toInt()
        // 小船一次移动的计时
        const val BOAT_MOVE_COUNT = 6000 / GAME_FLUSH_TIME.toInt()
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

    // XML 传入配置:
    // 两种掩图
    private val mEggMask: Bitmap
//    = drawable2Bitmap(R.drawable.ic_node)
    private val mBoatMask: Bitmap
//    = drawable2Bitmap(R.drawable.ic_boat)

    // 游戏配置
    private val mGameConfig: Array<Int>
    private val mDefaultConfig = arrayOf(
        TYPE_UNMOVE, TYPE_MOVE,
        TYPE_UNMOVE, TYPE_CIRCLE,
        TYPE_UNMOVE, TYPE_BEVAL,
        TYPE_UNMOVE, TYPE_SINY,
        TYPE_UNMOVE
    )

    // 是否显示辅助线
    private var isShowTip = false

    init{
        // 读取配置
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.FlyEggGameView)

        // 蛋的掩图
        var drawable = typedArray.getDrawable(R.styleable.FlyEggGameView_eggMask)
        mEggMask = drawable2Bitmap(drawable!!)
        // 船的掩图
        drawable = typedArray.getDrawable(R.styleable.FlyEggGameView_boatMask)
        mBoatMask = drawable2Bitmap(drawable!!)

        // 读取目标的布局配置
        val configId = typedArray.getResourceId(R.styleable.FlyEggGameView_gameConfig, -1)
        mGameConfig = if (configId != -1) {
            getGameConfig(configId)
        }else {
            mDefaultConfig
        }

        // 是否显示辅助线
        isShowTip = typedArray.getBoolean(R.styleable.FlyEggGameView_showTip, false)

        typedArray.recycle()
    }

    private fun getGameConfig(resId: Int): Array<Int> {
        val array = resources.getStringArray(resId)
        val result = Array<Int>(array.size) { it }
        for (i in array.indices) {
            result[i] = array[i].toInt()
        }
        return result
    }

    override fun load(w: Int, h: Int) {
        // 设置主角位置
        mHeroSprite.apply {
            posX = w / 2
            posY = h - BUTTOM_PADDING
            mask = mEggMask
            type = TYPE_HERO    // 英雄类型
        }

        // 主角初始化坐的船
        mHeroBoat = getBoat(TYPE_UNMOVE, 0, w, h).apply {
            mDisplaySprites.add(this)
        }

        // 主角飞起最大高度，比下一个跳板高一点
        mHeroFlyHeight = (h - BUTTOM_PADDING - TOP_PADDING) / 2 + HERO_OVER_HEIGHT

        // 构建地图
        val map = arrayOf(
            TYPE_UNMOVE, TYPE_MOVE,
            TYPE_UNMOVE, TYPE_CIRCLE,
            TYPE_UNMOVE, TYPE_BEVAL,
            TYPE_UNMOVE, TYPE_SINY,
            TYPE_UNMOVE
        )
        for (i in map.indices) {
            mDisplaySprites.add(getBoat(map[i], i + 1, w, h))
        }

        // 页面动画
        mAnimator = ValueAnimator.ofFloat(0f, h - BUTTOM_PADDING - TOP_PADDING.toFloat()).apply {
            duration = 3000
            addUpdateListener {
                mScorllValue = mScrollVauleLast + it.animatedValue as Float
            }
            addListener(onEnd = {
                mState = STATE_NORMAL
                mScrollVauleLast += h - BUTTOM_PADDING - TOP_PADDING.toFloat()
            })
        }
    }

    private fun getBoat(boatType: Int, index: Int, w: Int, h: Int): Sprite {
        return Sprite().apply {
            // 水平居中
            posX = w / 2
            // 高度从底部往上排列，屏幕放两个boat，上下空出VALUE_PADDING
            posY = (h - BUTTOM_PADDING) - index * (h - BUTTOM_PADDING - TOP_PADDING) / 2
            type = boatType
            mask = mBoatMask
            // 对应水平居中
            moveCount = BOAT_MOVE_COUNT / 2
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
            mHeroSprite.moveCount = HERO_FLY_COUNT
        }
        return false
    }

    override fun handleGame(gameView: BaseGameView): Boolean {
        // 如果页面在滚动，不处理逻辑
        if (mState == STATE_SCROLL) {
            return false
        }

        // 检查游戏结束
        if (checkSuccess()) {
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
                if (sprite.posY + mScorllValue <= TOP_PADDING) {
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
                sprite.moveCount = (sprite.moveCount + 1) % BOAT_MOVE_COUNT
                sprite.posX = HORIZONTAL_PADDING + (width - HORIZONTAL_PADDING * 2) / BOAT_MOVE_COUNT * sprite.moveCount
            }
            TYPE_CIRCLE -> {
                // 根据moveCount循环线性运动(分段函数)
                val totalWidth = width - HORIZONTAL_PADDING * 2
                val segment = totalWidth / BOAT_MOVE_COUNT
                // 两趟构成一个循环
                sprite.moveCount = (sprite.moveCount + 1) % (BOAT_MOVE_COUNT * 2)
                // 分两端函数
                if(sprite.moveCount < BOAT_MOVE_COUNT) {
                    sprite.posX = HORIZONTAL_PADDING + segment * sprite.moveCount
                }else {
                    // 坐标转换下就能回来
                    val returnX = 2 * BOAT_MOVE_COUNT - sprite.moveCount
                    sprite.posX = HORIZONTAL_PADDING + segment * returnX
                }
            }
            TYPE_BEVAL -> {
                // 移动范围
                val totalWidth = width - HORIZONTAL_PADDING * 2
                val totalHeight = (height - BUTTOM_PADDING - TOP_PADDING) / 4
                // 获取静止位置，即刚生成的时候的Y
                val index = mDisplaySprites.indexOf(sprite) - 1
                val staticY = (height - BUTTOM_PADDING) - index * (height - BUTTOM_PADDING - TOP_PADDING) / 2
                // 方便函数计算，从最低的Y坐标开始算(注意，向下是加)
                val startY = staticY + totalHeight
                // 在上面基础上增加Y轴变换
                val segmentX = totalWidth / BOAT_MOVE_COUNT
                val segmentY = totalHeight / BOAT_MOVE_COUNT

                sprite.moveCount = (sprite.moveCount + 1) % (BOAT_MOVE_COUNT * 2)
                if(sprite.moveCount < BOAT_MOVE_COUNT) {
                    sprite.posX = HORIZONTAL_PADDING + segmentX * sprite.moveCount
                    sprite.posY = startY - segmentY * sprite.moveCount
                }else {
                    // 坐标转换下就能回来
                    val returnX = 2 * BOAT_MOVE_COUNT - sprite.moveCount
                    sprite.posX = HORIZONTAL_PADDING + segmentX * returnX
                    sprite.posY = startY - segmentY * returnX
                }
            }
            TYPE_SINY -> {
                // 移动范围
                val totalWidth = width - HORIZONTAL_PADDING * 2
                val totalHeight = (height - BUTTOM_PADDING - TOP_PADDING) / 4
                val halfHeight = totalHeight / 2
                // 获取静止位置，即刚生成的时候的Y
                val index = mDisplaySprites.indexOf(sprite) - 1
                val staticY = (height - BUTTOM_PADDING) - index * (height - BUTTOM_PADDING - TOP_PADDING) / 2
                // 方便函数计算，从最低的Y坐标开始算(注意，向下是加)
                val startY = staticY + halfHeight
                // 在上面基础上增加Y轴变换
                val segmentX = totalWidth / BOAT_MOVE_COUNT

                sprite.moveCount = (sprite.moveCount + 1) % (BOAT_MOVE_COUNT * 2)
                // 前一段是正弦，后一段负的正弦回去，类似 ∞ 符号
                // 写个正弦函数: x = x, y = height * sin w * x
                val w = 2 * PI / BOAT_MOVE_COUNT.toFloat()
                if(sprite.moveCount < BOAT_MOVE_COUNT) {
                    sprite.posX = HORIZONTAL_PADDING + segmentX * sprite.moveCount
                    sprite.posY = startY - (halfHeight * sin(w * sprite.moveCount)).toInt()
                }else {
                    // 坐标转换，Y函数也切换下
                    val returnX = 2 * BOAT_MOVE_COUNT - sprite.moveCount
                    sprite.posX = HORIZONTAL_PADDING + segmentX * returnX
                    sprite.posY = startY + (halfHeight * sin(w * returnX)).toInt()
                }
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
        if (moveCount in 0..HERO_FLY_COUNT) {
            // 在y轴上执行抛物线
            val half = HERO_FLY_COUNT / 2
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

    private fun checkSuccess(): Boolean {
        val result = mDisplaySprites.indexOf(mHeroBoat) == (mDisplaySprites.size - 1)
        if (result) {
            pause()
            AlertDialog.Builder(context)
                .setTitle("恭喜通关！！！")
                .setMessage("请点击确认继续游戏")
                .setPositiveButton("确认") { _, _ ->
                    reload(width, height)
                    start()
                }
                .setNegativeButton("取消", null)
                .create()
                .show()
        }
        return result
    }

    public override fun recycle() {
        super.recycle()
        mEggMask.recycle()
        mBoatMask.recycle()
    }
}