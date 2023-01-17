@file:Suppress("unused")

package com.silencefly96.module_views.game

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import androidx.core.view.forEach
import com.silencefly96.module_views.R
import java.lang.ref.WeakReference

/**
 * 飞机大战view
 */
class AirplaneFightGameView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
): ViewGroup(context, attributeSet, defStyleAttr) {

    companion object{
        /** 元素类型 **/
        const val SPRITE_TYPE_NULL = -1         // 非元素
        const val SPRITE_TYPE_AIRPLANE = 0      // 可操作的飞机
        const val SPRITE_TYPE_BULLET = 1        // 子弹
        const val SPRITE_TYPE_LIVE = 2          // 生命值加成
        const val SPRITE_TYPE_ENEMY1 = 3        // 敌人1
        const val SPRITE_TYPE_ENEMY2 = 4        // 敌人2
        const val SPRITE_TYPE_ENEMY3 = 5        // 敌人3
        const val SPRITE_TYPE_BOSS = 6          // 大BOSS

        // 精灵类默认移动距离
        const val DEFAULT_MOVE_LENGTH = 10

        // 控制逻辑刷新速度，fps = 1000 / DEFAULT_GAME_SPEED = 20
        const val DEFAULT_GAME_SPEED = 250L
    }

    // 预设生命值
    private var mLive: Int = 3

    // 飞机
    private var mAirplane: Sprite? = null

    // 子弹
    private var mBullet: Sprite? = null

    // 加生命值物品
    private var mLiveSprite: Sprite? = null

    // 敌人1~3
    private var mEnemyList = ArrayList<Sprite>(3)

    // 大BOSS
    private var mBossSprite: Sprite? = null

    // 所有精灵类
    private val mSpriteList = ArrayList<Sprite>(8)

    // 游戏控制逻辑handler
    private val mHandler = GameHandler(this)

    // 场景信息
    private val mSceneInfo = SceneInfo()

    init {
        val typedArray =
            context.obtainStyledAttributes(attributeSet, R.styleable.AirplaneFightGameView)
        // 获取设置的生命值
        mLive = typedArray.getInteger(R.styleable.AirplaneFightGameView_liveSize, 3)
        typedArray.recycle()

        // 通过post在view加载完成后调用
        post {
            mHandler.sendEmptyMessage(0)
        }
    }

    // 加载完XML布局view
    override fun onFinishInflate() {
        super.onFinishInflate()
        mSpriteList.clear()
        forEach {
            val type = (it.layoutParams as LayoutParams).spriteType
            val sprite = Sprite(type, it)
            when(type) {
                SPRITE_TYPE_AIRPLANE -> mAirplane = sprite
                SPRITE_TYPE_BULLET -> mBullet = sprite
                SPRITE_TYPE_LIVE -> mLiveSprite = sprite
                SPRITE_TYPE_ENEMY1, SPRITE_TYPE_ENEMY2, SPRITE_TYPE_ENEMY3
                    -> mEnemyList.add(sprite)
                SPRITE_TYPE_BOSS -> mBossSprite = sprite.apply {
                    // BOSS生命值更高
                    live = 3
                }
            }
            // 重置得到隐藏位置
            sprite.reset()
            mSpriteList.add(sprite)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // 将飞机放到屏幕底部中间
        val width = getDefaultSize(suggestedMinimumWidth, widthMeasureSpec)
        val height = getDefaultSize(suggestedMinimumHeight, heightMeasureSpec)
        mAirplane?.posX = width / 2
        mAirplane?.posY = height - 50
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for(sprite in mSpriteList) {
            sprite.spriteView.layout(
                sprite.posX - sprite.spriteView.width / 2,
                sprite.posY - sprite.spriteView.height / 2,
                sprite.posX + sprite.spriteView.width / 2,
                sprite.posY + sprite.spriteView.height / 2
            )
        }
    }

    //自定义的LayoutParams，子控件使用的是父控件的LayoutParams，所以父控件可以增加自己的属性，在子控件XML中使用
    @Suppress("MemberVisibilityCanBePrivate")
    class LayoutParams : MarginLayoutParams {
        //元素类型，不设置就是null
        var spriteType: Int = SPRITE_TYPE_NULL

        //三个构造
        constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
            //读取XML参数，设置相关属性，这里有个很烦的warning，样式必须是外部类加layout结尾
            val attrArr =
                context.obtainStyledAttributes(attrs, R.styleable.AirplaneFightGameView_Layout)
            spriteType = attrArr.getInteger(
                R.styleable.AirplaneFightGameView_Layout_sprite_type,
                SPRITE_TYPE_NULL
            )
            //回收
            attrArr.recycle()
        }
        constructor(width: Int, height: Int) : super(width, height)
        constructor(source: ViewGroup.LayoutParams) : super(source)
    }

    // 重写下面四个函数，在布局文件被填充为对象的时候调用的
    override fun generateLayoutParams(attrs: AttributeSet): ViewGroup.LayoutParams {
        return LayoutParams(context, attrs)
    }

    override fun generateLayoutParams(p: ViewGroup.LayoutParams?): ViewGroup.LayoutParams {
        return LayoutParams(p)
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams {
        return LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean {
        return p is LayoutParams
    }

    // 游戏中精灵类
    class Sprite(
        var type: Int,
        var spriteView: View,
        var live: Int = 1,
        var posX: Int = 0,
        var posY: Int = 0,
        var isShow: Boolean = false
    ) {
        // 重置，位置隐藏
        fun reset() {
            isShow = false
            live = 1
            posX = -spriteView.width
            posY = -spriteView.height
        }

        fun move(deltaX: Int, deltaY: Int) {
            posX += deltaX
            posY += deltaY
        }

        fun checkAttack(x: Float, y: Float) {
            // 简单得碰撞检测
            if (x < posX + spriteView.width / 2 && x > posX - spriteView.width / 2
                && y < posY + spriteView.height / 2 && y > posY - spriteView.height / 2) {
                live--
                if (live <= 0) death()
            }
        }

        private fun death() {
            // TODO 播放特效之类
            reset()
        }
    }

    // 场景信息
    data class SceneInfo(
        // 已击杀
        var enemyKilledCount: Int = 0,
        // 场上敌人数量
        var currentEnemyCount: Int = 0,
    )

    // kotlin自动编译为Java静态类，控件引用使用弱引用
    class GameHandler(view: AirplaneFightGameView): Handler(Looper.getMainLooper()){
        // 控件引用
        private val mRef: WeakReference<AirplaneFightGameView> = WeakReference(view)
        // 敌机间隔控制，3秒才一个
        private var timeCount = 0
        override fun handleMessage(msg: Message) {
            mRef.get()?.let {
                // 选择新出现得sprite
                val sprite = chooseSprite()
                sprite?.let { choose->
                    choose.isShow = true
                    choose.spriteView.let { view ->
                        choose.posX = view.width / 2 + (Math.random() * it.width - view.width).toInt()
                        choose.posY = view.height
                    }
                }


                // TODO 更新sprite运动
                for (peer in it.mSpriteList) {
                    if (peer.isShow) peer.move(0, 0)
                }


                // TODO 刷新页面

                // TODO 碰撞检测
                timeCount++
                it.mHandler.sendEmptyMessageDelayed(0, DEFAULT_GAME_SPEED)
            }
        }

        private fun chooseSprite(): Sprite? {
            mRef.get()?.let {
                if (it.mSceneInfo.currentEnemyCount < 4
                    && timeCount == 3000 / DEFAULT_GAME_SPEED.toInt()
                    && it.mSceneInfo.enemyKilledCount < 30) {
                    for (i in SPRITE_TYPE_LIVE..SPRITE_TYPE_ENEMY3) {
                        if (!it.mSpriteList[i].isShow) {
                            return it.mSpriteList[i]
                        }
                    }
                    timeCount = 0
                }

                // 击败三十次出现BOSS
                if (it.mSceneInfo.enemyKilledCount == 30) {
                    return  mRef.get()?.mBossSprite
                }
            }
            return null
        }
    }
}