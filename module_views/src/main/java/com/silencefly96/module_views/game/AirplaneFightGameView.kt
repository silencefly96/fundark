@file:Suppress("unused")

package com.silencefly96.module_views.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.View
import com.silencefly96.module_views.R
import java.lang.ref.WeakReference
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 飞机大战 GameView
 *
 * 1，载入界面配置，设置界面信息view
 * 2，载入精灵配置，摆放飞机、子弹、敌人
 * 3，启动手势控制逻辑
 * 4，启动游戏controller
 */
class AirplaneFightGameView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
): View(context, attributeSet, defStyleAttr) {

    companion object{
        // 精灵类型
        const val SPRITE_TYPE_AIRPLANE = 0
        const val SPRITE_TYPE_BULLET = 1
        const val SPRITE_TYPE_ENEMY = 2

        // 游戏更新间隔，一秒四次
        const val GAME_FLUSH_TIME = 250L
        // 敌人更新隔时间
        const val ADD_ENEMY_TIME = 1000L
        // 敌人移动距离
        const val ENEMY_MOVE_DISTANCE = 100
        // 子弹更新隔时间
        const val ADD_BULLET_TIME = 500L
        // 子弹移动距离
        const val BULLET_MOVE_DISTANCE = 100

        // 碰撞间隔
        const val COLLISION_DISTANCE = 100

        // 距离计算公式
        fun getDistance(x1: Int, y1: Int, x2: Int, y2: Int): Float {
            return sqrt(((x1 - x2).toDouble().pow(2.0)
                    + (y1 - y2).toDouble().pow(2.0)).toFloat())
        }
    }

    // 默认生命值大小
    private val mDefaultLiveSize: Int

    // 得分
    private var mScore: Int = 0

    // 飞机
    private val mAirPlane: Sprite = Sprite(0, 0)
    private val mAirPlaneMask: Bitmap?

    // 子弹序列
    private val mBulletList = ArrayList<Sprite>()
    private val mBulletMask: Bitmap?

    // 敌人序列
    private val mEnemyList = ArrayList<Sprite>()
    private val mEnemyMask: Bitmap?

    // 游戏控制器
    private val mGameController = GameController(this)

    init {
        // 读取配置
        val typedArray =
            context.obtainStyledAttributes(attributeSet, R.styleable.AirplaneFightGameView)

        mDefaultLiveSize =
            typedArray.getInteger(R.styleable.AirplaneFightGameView_liveSize, 3)

        // 飞机掩图
        var resourceId =
            typedArray.getResourceId(R.styleable.AirplaneFightGameView_airplane, 0)
        mAirPlaneMask = if (resourceId != 0) {
            BitmapFactory.decodeResource(context.resources, resourceId)
        }else null

        // 子弹掩图
        resourceId =
            typedArray.getResourceId(R.styleable.AirplaneFightGameView_bullet, 0)
        mBulletMask = if (resourceId != 0) {
            BitmapFactory.decodeResource(context.resources, resourceId)
        }else null

        // 敌人掩图
        resourceId =
            typedArray.getResourceId(R.styleable.AirplaneFightGameView_bullet, 0)
        mEnemyMask = if (resourceId != 0) {
            BitmapFactory.decodeResource(context.resources, resourceId)
        }else null

        typedArray.recycle()
    }

    // 完成测量开始游戏
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mGameController.removeMessages(0)
        mGameController.sendEmptyMessageDelayed(0, ADD_ENEMY_TIME)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(getDefaultSize(0, widthMeasureSpec),
            getDefaultSize(0, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
    }

    // kotlin自动编译为Java静态类，控件引用使用弱引用
    class GameController(view: AirplaneFightGameView): Handler(Looper.getMainLooper()){
        // 控件引用
        private val mRef: WeakReference<AirplaneFightGameView> = WeakReference(view)
        // 子弹更新频率限制
        private var bulletCounter = 0
        // 敌人更新频率控制
        private var enemyCounter = 0

        override fun handleMessage(msg: Message) {
            mRef.get()?.let { gameView ->
                // 移动已有子弹
                bulletCounter++
                if (bulletCounter == (GAME_FLUSH_TIME / ADD_BULLET_TIME).toInt()) {
                    for (i in 0 until gameView.mBulletList.size) {
                        val bullet = gameView.mBulletList[i]
                        bullet.posY -= BULLET_MOVE_DISTANCE
                        // 子弹出界
                        if (bullet.posY < 0) gameView.mBulletList.remove(bullet)
                    }
                    bulletCounter = 0
                }

                // 移动敌人,并验证碰撞
                enemyCounter++
                if (enemyCounter == (GAME_FLUSH_TIME / ADD_ENEMY_TIME).toInt()) {
                    for (i in 0 until gameView.mEnemyList.size) {
                        val enemy = gameView.mEnemyList[i]
                        enemy.posY += ENEMY_MOVE_DISTANCE

                        // 验证碰撞
                        for (j in 0 until gameView.mBulletList.size) {
                            val bullet = gameView.mBulletList[j]
                            if (getDistance(bullet.posX, bullet.posY, enemy.posX, enemy.posY)
                                <= COLLISION_DISTANCE) {
                                // 发生碰撞
                                gameView.mBulletList.remove(bullet)
                                gameView.mEnemyList.remove(enemy)
                                gameView.mScore++
                            }
                        }
                    }
                    enemyCounter = 0
                }

                // 添加新子弹
                gameView.mBulletList.add(Sprite(gameView.mAirPlane.posX, gameView.mAirPlane.posY,
                    1, gameView.mBulletMask))

                // 随机生成一个敌人
                val y = (Math.random() * gameView.width).toInt()
                gameView.mEnemyList.add(Sprite(0, y, 1, gameView.mEnemyMask))

                // 循环发送消息，刷新页面
                gameView.invalidate()
                mRef.get()?.mGameController?.sendEmptyMessageDelayed(0, ADD_ENEMY_TIME)
            }
        }
    }

    data class Sprite(var posX: Int, var posY: Int, var live: Int = 1, var bitmap: Bitmap? = null)
}