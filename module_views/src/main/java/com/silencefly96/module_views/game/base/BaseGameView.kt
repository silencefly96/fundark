@file:Suppress("unused", "LeakingThis", "MemberVisibilityCanBePrivate")

package com.silencefly96.module_views.game.base

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

abstract class BaseGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
): View(context, attrs, defStyleAttr) {

    companion object {
        // 游戏更新间隔，一秒20次
        const val GAME_FLUSH_TIME = 50L

        // 四个方向
        const val DIR_UP = 0
        const val DIR_RIGHT = 1
        const val DIR_DOWN = 2
        const val DIR_LEFT = 3

        // 距离计算公式
        fun getDistance(x1: Int, y1: Int, x2: Int, y2: Int): Float {
            return sqrt(((x1 - x2).toDouble().pow(2.0)
                    + (y1 - y2).toDouble().pow(2.0)).toFloat())
        }

        // 两点连线角度计算, (x1, y1) 为起点
        fun getDegree(x1: Float, y1: Float, x2: Float, y2: Float): Double {
            // 弧度
            val radians = atan2(y1 - y2, x1 - x2).toDouble()
            // 从弧度转换成角度
            return Math.toDegrees(radians)
        }
    }

    // 游戏控制器
    private val mGameController = GameController(this)

    // 上一个触摸点X、Y的坐标
    private var mLastX = 0f
    private var mLastY = 0f
    private var mStartX = 0f
    private var mStartY = 0f

    // 行的数量、间距
    private val rowNumb: Int = getRowNumb()
    private var rowDelta = 0
    protected open fun getRowNumb(): Int{ return 30 }

    // 列的数量、间距
    private val colNumb: Int = getColNumb()
    private var colDelta = 0
    protected open fun getColNumb(): Int{ return 20 }

    // 是否绘制网格
    protected open fun isDrawGrid(): Boolean{ return false }

    // 画笔
    private val mPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 10f
        style = Paint.Style.STROKE
        flags = Paint.ANTI_ALIAS_FLAG
        textAlign = Paint.Align.CENTER
        textSize = 30f
    }

    protected fun drawable2Bitmap(id: Int): Bitmap {
        val drawable = ResourcesCompat.getDrawable(resources, id, null)
        return drawable2Bitmap(drawable!!)
    }

    protected fun drawable2Bitmap(drawable: Drawable): Bitmap {
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        val config = Bitmap.Config.ARGB_8888
        val bitmap = Bitmap.createBitmap(w, h, config)
        //注意，下面三行代码要用到，否则在View或者SurfaceView里的canvas.drawBitmap会看不到图
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bitmap
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                mLastX = event.x
                mLastY = event.y
                mStartX = event.x
                mStartY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                // 适用于手指移动时，同步处理逻辑
                val lenX = event.x - mLastX
                val lenY = event.y - mLastY

                // 根据返回来判断是否刷新界面，可用来限制移动
                if (onMove(lenX, lenY)) {
                    invalidate()
                }

                mLastX = event.x
                mLastY = event.y
            }
            MotionEvent.ACTION_UP -> {
                // 适用于一个动作抬起时，做出逻辑修改
                val lenX = event.x - mStartX
                val lenY = event.y - mStartY

                // 转方向
                val dir = if (abs(lenX) > abs(lenY)) {
                    if (lenX >= 0) DIR_RIGHT else DIR_LEFT
                }else {
                    if (lenY >= 0) DIR_DOWN else DIR_UP
                }

                // 根据返回来判断是否刷新界面，可用来限制移动
                if (onMoveUp(dir)) {
                    invalidate()
                }
            }
        }
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 设置网格
        rowDelta = h / rowNumb
        colDelta = w / colNumb
        // 开始游戏
        load(w, h)
        start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 绘制网格
        if (isDrawGrid()) {
            mPaint.strokeWidth = 1f
            for (i in 0..rowNumb) {
                canvas.drawLine(0f, rowDelta * i.toFloat(),
                    width.toFloat(), rowDelta * i.toFloat(), mPaint)
            }
            for (i in 0..colNumb) {
                canvas.drawLine(colDelta * i.toFloat(), 0f,
                    colDelta * i.toFloat(), height.toFloat(), mPaint)
            }
            mPaint.strokeWidth = 10f
        }

        // 游戏绘制逻辑
        drawGame(canvas, mPaint)
    }

    // 精灵位置
    data class Sprite(
        var posX: Int = 0,           // 坐标
        var posY: Int = 0,
        var live: Int = 1,           // 生命值
        var degree: Float = 0f,      // 方向角度，[0, 360]
        var speed: Float = 1f,       // 速度，[0, 1]刷新速度百分比
        var speedCount: Int = 0,     // 用于计数，调整速度
        var mask: Bitmap? = null,    // 掩图
        var type: Int = 0,           // 类型
        var moveCount: Int = 0       // 单次运动的计时
    )

    // 用于在网格方块中心绘制精灵掩图
    protected open fun drawSprite(sprite: Sprite, canvas: Canvas, paint: Paint) {
        sprite.mask?.let { mask ->
            canvas.drawBitmap(mask, sprite.posX - mask.width / 2f,
                sprite.posY - mask.height / 2f, paint)
        }
    }

    class GameController(view: BaseGameView): Handler(Looper.getMainLooper()){
        // 控件引用
        private val mRef: WeakReference<BaseGameView> = WeakReference(view)
        // 游戏结束标志
        private var isGameOver = false
        // 暂停标志
        private var isPause = false

        override fun handleMessage(msg: Message) {
            mRef.get()?.let { gameView ->
                // 处理游戏逻辑
                isGameOver = gameView.handleGame(gameView)

                // 循环发送消息，刷新页面
                gameView.invalidate()
                if (isGameOver) {
                    gameView.gameOver()
                }else if (!isPause) {
                    gameView.mGameController.sendEmptyMessageDelayed(0, GAME_FLUSH_TIME)
                }
            }
        }

        fun pause(flag: Boolean) {
            isPause = flag
        }
    }

    /**
     * 加载
     *
     * @param w 宽度
     * @param h 高度
     */
    abstract fun load(w: Int, h: Int)

    /**
     * 重新加载
     *
     * @param w 宽度
     * @param h 高度
     */
    abstract fun reload(w: Int, h: Int)

    /**
     * 绘制游戏界面
     *
     * @param canvas 画板
     * @param paint 画笔
     */
    abstract fun drawGame(canvas: Canvas, paint: Paint)

    /**
     * 移动一小段，返回true刷新界面
     *
     * @param dx x轴移动值
     * @param dy y轴移动值
     * @return 是否刷新界面
     */
    protected open fun onMove(dx: Float, dy: Float): Boolean { return false }

    /**
     * 滑动抬起，返回true刷新界面
     *
     * @param dir 一次滑动后的方向
     * @return 是否刷新界面
     */
    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun onMoveUp(dir: Int): Boolean{ return false }

    /**
     * 处理游戏逻辑，返回是否结束游戏
     *
     * @param gameView 游戏界面
     * @return 是否结束游戏
     */
    abstract fun handleGame(gameView: BaseGameView): Boolean

    /**
     * 失败
     */
    protected open fun gameOver() {
        AlertDialog.Builder(context)
            .setTitle("继续游戏")
            .setMessage("请点击确认继续游戏")
            .setPositiveButton("确认") { _, _ ->
                run {
                    reload(width, height)
                    start()
                }
            }
            .setNegativeButton("取消", null)
            .create()
            .show()
    }

    /**
     * 暂停游戏刷新
     */
    protected fun pause() {
        mGameController.pause(true)
        mGameController.removeMessages(0)
    }

    /**
     * 继续游戏
     */
    protected fun start() {
        mGameController.pause(false)
        mGameController.sendEmptyMessageDelayed(0, GAME_FLUSH_TIME)
    }

    /**
     * 回收资源
     */
    protected open fun recycle() {
        mGameController.removeMessages(0)
    }
}