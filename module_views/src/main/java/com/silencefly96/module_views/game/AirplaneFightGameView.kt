@file:Suppress("unused")

package com.silencefly96.module_views.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
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

        // 游戏更新间隔，一秒20次
        const val GAME_FLUSH_TIME = 50L
        // 敌人添加隔时间
        const val ADD_ENEMY_TIME = 1000L
        // 敌人更新隔时间
        const val UPDATE_ENEMY_TIME = 200L
        // 敌人移动距离
        const val ENEMY_MOVE_DISTANCE = 50
        // 子弹更新隔时间
        const val ADD_BULLET_TIME = 100L
        // 子弹移动距离
        const val BULLET_MOVE_DISTANCE = 50

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

    // 画笔
    private val mPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 3f
        style = Paint.Style.STROKE
        flags = Paint.ANTI_ALIAS_FLAG
        textAlign = Paint.Align.CENTER
        textSize = 30f
    }

    // 上一个触摸点X的坐标
    private var mLastX = 0f

    init {
        // 读取配置
        val typedArray =
            context.obtainStyledAttributes(attributeSet, R.styleable.AirplaneFightGameView)

        mDefaultLiveSize =
            typedArray.getInteger(R.styleable.AirplaneFightGameView_liveSize, 3)

        // 得到的Bitmap为空
//        var resourceId =
//        typedArray.getResourceId(R.styleable.AirplaneFightGameView_airplane, 0)
//        mAirPlaneMask = if (resourceId != 0) {
//            BitmapFactory.decodeResource(resources, resourceId)
//        }else null

        // 注意Drawable也有类型
//        var drawable = typedArray.getDrawable(R.styleable.AirplaneFightGameView_airplane)
//        mAirPlaneMask = (drawable as BitmapDrawable).bitmap     //vector的xml不是BitmapDrawable
//        mAirPlaneMask = (drawable as VectorDrawable).toBitmap() //需要版本支持

        // 飞机掩图
        var drawable = typedArray.getDrawable(R.styleable.AirplaneFightGameView_airplane)
        mAirPlaneMask = if (drawable != null) drawableToBitmap(drawable, 2) else null

        // 子弹掩图
        drawable = typedArray.getDrawable(R.styleable.AirplaneFightGameView_bullet)
        mBulletMask = if (drawable != null) drawableToBitmap(drawable) else null

        // 敌人掩图
        drawable = typedArray.getDrawable(R.styleable.AirplaneFightGameView_enemy)
        mEnemyMask = if (drawable != null) drawableToBitmap(drawable, 2) else null

        typedArray.recycle()
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int = 1): Bitmap? {
        val w = drawable.intrinsicWidth * size
        val h = drawable.intrinsicHeight * size
        val config = Bitmap.Config.ARGB_8888
        val bitmap = Bitmap.createBitmap(w, h, config)
        //注意，下面三行代码要用到，否则在View或者SurfaceView里的canvas.drawBitmap会看不到图
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bitmap
    }

    // 完成测量开始游戏
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mGameController.removeMessages(0)
        // 设置好飞机位置，坐标未中心坐标，方便计算碰撞
        mAirPlane.bitmap = mAirPlaneMask
        mAirPlane.live = mDefaultLiveSize
        mAirPlane.posX = w / 2 - mAirPlaneMask!!.width / 2
        mAirPlane.posY = h - mAirPlaneMask.height / 2 - 50
        mGameController.sendEmptyMessageDelayed(0, GAME_FLUSH_TIME)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(getDefaultSize(0, widthMeasureSpec),
            getDefaultSize(0, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 绘制顶部信息
        canvas.drawText("生命值：${mAirPlane.live}, 当前得分：$mScore",
            (width / 2).toFloat(), 50f, mPaint)

        // 绘制敌人
        for (enemy in mEnemyList) {
            canvas.drawBitmap(mEnemyMask!!,
                enemy.posX.toFloat() - mEnemyMask.width / 2,
                enemy.posY.toFloat() - mEnemyMask.height / 2, mPaint)
        }

        // 绘制子弹
        for (bullet in mBulletList) {
            canvas.drawBitmap(mBulletMask!!,
                bullet.posX.toFloat()- mBulletMask.width / 2,
                bullet.posY.toFloat()- mBulletMask.height / 2, mPaint)
        }

        // 绘制飞机
        canvas.drawBitmap(mAirPlaneMask!!,
            mAirPlane.posX.toFloat() - mAirPlaneMask.width / 2,
            mAirPlane.posY.toFloat() - mAirPlaneMask.height / 2, mPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                mLastX = event.x
            }
            MotionEvent.ACTION_MOVE -> {
                val len = event.x - mLastX
                val preX = mAirPlane.posX + len
                if (preX > mAirPlaneMask!!.width / 2 ||
                        preX < (width - mAirPlaneMask.width / 2)) {
                    mAirPlane.posX += len.toInt()
                    Log.e("TAG", "onTouchEvent: mAirPlane.posX = ${mAirPlane.posX}")
                    invalidate()
                }
                mLastX = event.x
            }
            MotionEvent.ACTION_UP -> {}
        }
        return true
    }

    // kotlin自动编译为Java静态类，控件引用使用弱引用
    class GameController(view: AirplaneFightGameView): Handler(Looper.getMainLooper()){
        // 控件引用
        private val mRef: WeakReference<AirplaneFightGameView> = WeakReference(view)
        // 子弹更新频率限制
        private var bulletCounter = 0
        // 敌人更新频率控制
        private var enemyCounter = 0
        // 敌人出现频率控制
        private var enemyUpdateCounter = 0
        // 游戏结束标志
        private var isGameOver = false

        override fun handleMessage(msg: Message) {
            mRef.get()?.let { gameView ->
                // 移动已有子弹
                bulletCounter++
                if (bulletCounter == (ADD_BULLET_TIME / GAME_FLUSH_TIME).toInt()) {
                    var mark = -1
                    for (i in 0 until gameView.mBulletList.size) {
                        val bullet = gameView.mBulletList[i]
                        bullet.posY -= BULLET_MOVE_DISTANCE
                        // 子弹出界
                        if (bullet.posY < 0) mark = i
                    }

                    // 移除出界子弹
                    if (mark >= 0) gameView.mBulletList.remove(gameView.mBulletList[mark])

                    // 添加新子弹，横向在飞机上居中
                    gameView.mBulletList.add(Sprite(gameView.mAirPlane.posX,
                        gameView.mAirPlane.posY, 1, gameView.mBulletMask))

                    bulletCounter = 0
                }

                // 移动敌人,并验证碰撞
                enemyUpdateCounter++
                if (enemyUpdateCounter == (UPDATE_ENEMY_TIME / GAME_FLUSH_TIME).toInt()) {
                    val removeEnemyList = ArrayList<Sprite>()
                    val removeBulletList = ArrayList<Sprite>()
                    for (i in 0 until gameView.mEnemyList.size) {
                        val enemy = gameView.mEnemyList[i]
                        enemy.posY += ENEMY_MOVE_DISTANCE

                        // 验证和子弹碰撞
                        for (j in 0 until gameView.mBulletList.size) {
                            val bullet = gameView.mBulletList[j]
                            if (getDistance(bullet.posX, bullet.posY, enemy.posX, enemy.posY)
                                <= COLLISION_DISTANCE) {
                                // 发生碰撞
                                removeEnemyList.add(enemy)
                                removeBulletList.add(bullet)
                                gameView.mScore++
                            }
                        }

                        //验证和飞机碰撞
                        if (getDistance(gameView.mAirPlane.posX, gameView.mAirPlane.posY,
                                enemy.posX, enemy.posY)
                            <= COLLISION_DISTANCE) {
                            // 发生碰撞
                            gameView.mEnemyList.remove(enemy)
                            if (gameView.mAirPlane.live-- <= 0) {
                                isGameOver = true
                            }
                        }
                    }

                    // 统一移除
                    for (enemy in removeEnemyList) gameView.mEnemyList.remove(enemy)
                    for (bullet in removeBulletList) gameView.mBulletList.remove(bullet)

                    enemyUpdateCounter = 0
                }

                enemyCounter++
                if (enemyCounter == (ADD_ENEMY_TIME / GAME_FLUSH_TIME).toInt()) {
                    // 随机生成一个敌人
                    val x = (gameView.mEnemyMask!!.width / 2 +
                            Math.random() * (gameView.width - gameView.mEnemyMask.width)).toInt()
                    gameView.mEnemyList.add(
                        Sprite(x, gameView.mEnemyMask.height / 2, 1, gameView.mEnemyMask))

                    enemyCounter = 0
                }

                // 循环发送消息，刷新页面
                gameView.invalidate()
                if (!isGameOver) {
                    mRef.get()?.mGameController?.sendEmptyMessageDelayed(0, GAME_FLUSH_TIME)
                }
            }
        }
    }

    // 坐标使用中心坐标
    data class Sprite(var posX: Int, var posY: Int, var live: Int = 1, var bitmap: Bitmap? = null)

    /**
     * 供外部回收资源
     */
    fun recycle()  {
        mAirPlaneMask?.recycle()
        mBulletMask?.recycle()
        mEnemyMask?.recycle()
        mGameController.removeMessages(0)
    }
}