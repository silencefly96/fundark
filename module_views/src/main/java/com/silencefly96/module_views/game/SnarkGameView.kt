@file:Suppress("unused")

package com.silencefly96.module_views.game

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
import com.silencefly96.module_views.R
import java.lang.ref.WeakReference
import kotlin.math.abs

/**
 * 贪吃蛇游戏view
 */
class SnarkGameView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attributeSet, defStyleAttr) {
    companion object{
        // 四个方向
        const val DIR_UP = 0
        const val DIR_RIGHT = 1
        const val DIR_DOWN = 2
        const val DIR_LEFT = 3

        // 游戏更新间隔，一秒5次
        const val GAME_FLUSH_TIME = 200L
        // 蛇体移动频率
        const val SNARK_MOVE_TIME = 600L
        // 食物添加间隔时间
        const val FOOD_ADD_TIME = 5000L
        // 食物存活时间
        const val FOOD_ALIVE_TIME = 10000L
        // 食物闪烁时间，要比存货时间长
        const val FOOD_BLING_TIME = 3000L
        // 食物闪烁间隔
        const val FOOD_BLING_FREQ = 300L

        // 手指移动距离判定
        const val MOVE_DISTANCE = 150
    }

    // 屏幕划分数量及等分长度
    private val rowNumb: Int
    private var rowDelta: Int = 0
    private val colNumb: Int
    private var colDelta: Int = 0

    // 节点掩图
    private val mNodeMask: Bitmap?

    // 头节点
    private val mHead = Snark(0, 0, DIR_DOWN, null)

    // 尾节点
    private var mTail = mHead

    // 食物数组
    private val mFoodList = ArrayList<Food>()

    // 游戏控制器
    private val mGameController = GameController(this)

    // 画笔
    private val mPaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 1f
        style = Paint.Style.STROKE
        flags = Paint.ANTI_ALIAS_FLAG
        textAlign = Paint.Align.CENTER
        textSize = 30f
    }

    // 上一个触摸点X、Y的坐标
    private var mLastX = 0f
    private var mLastY = 0f

    init {
        // 读取配置
        val typedArray =
            context.obtainStyledAttributes(attributeSet, R.styleable.SnarkGameView)

        // 横竖划分
        rowNumb = typedArray.getInteger(R.styleable.SnarkGameView_rowNumb, 30)
        colNumb = typedArray.getInteger(R.styleable.SnarkGameView_colNumb, 20)

        // 节点掩图
        val drawable = typedArray.getDrawable(R.styleable.SnarkGameView_node)
        mNodeMask = if (drawable != null) drawableToBitmap(drawable) else null

        typedArray.recycle()
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
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

    // 完成测量开始游戏
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rowDelta = h / rowNumb
        colDelta = w / colNumb
        // 开始游戏
        load()
    }

    // 加载
    private fun load() {
        mGameController.removeMessages(0)
        // 设置贪吃蛇的位置
        mHead.posX = colNumb / 2
        mHead.posY = rowNumb / 2
        mGameController.sendEmptyMessageDelayed(0, GAME_FLUSH_TIME)
    }

    // 重新加载
    private fun reload() {
        mGameController.removeMessages(0)
        // 清空界面
        mFoodList.clear()
        mHead.posX = colNumb / 2
        mHead.posY = rowNumb / 2
        // 蛇体链表回收，让GC通过可达性分析去回收
        mHead.next = null
        mGameController.isGameOver = false
        mGameController.sendEmptyMessageDelayed(0, GAME_FLUSH_TIME)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(getDefaultSize(0, widthMeasureSpec),
            getDefaultSize(0, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制网格
        for (i in 0..rowNumb) {
            canvas.drawLine(0f, rowDelta * i.toFloat(),
                width.toFloat(), rowDelta * i.toFloat(), mPaint)
        }
        for (i in 0..colNumb) {
            canvas.drawLine(colDelta * i.toFloat(), 0f,
                colDelta * i.toFloat(), height.toFloat(), mPaint)
        }

        // 绘制食物
        for (food in mFoodList) {
            if (food.show) canvas.drawBitmap(mNodeMask!!,
                (food.posX + 0.5f) * colDelta - mNodeMask.width / 2,
                (food.posY + 0.5f) * rowDelta - mNodeMask.height / 2, mPaint)
        }

        // 绘制蛇体
        var p: Snark? = mHead
        while (p != null) {
            canvas.drawBitmap(mNodeMask!!,
                (p.posX + 0.5f) * colDelta - mNodeMask.width / 2,
                (p.posY + 0.5f) * rowDelta - mNodeMask.height / 2, mPaint)
            p = p.next
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                mLastX = event.x
                mLastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {}
            MotionEvent.ACTION_UP -> {
                val lenX = event.x - mLastX
                val lenY = event.y - mLastY

                mHead.dir = if (abs(lenX) > abs(lenY)) {
                    if (lenX >= 0) DIR_RIGHT else DIR_LEFT
                }else {
                    if (lenY >= 0) DIR_DOWN else DIR_UP
                }

                invalidate()
            }
        }
        return true
    }

    private fun gameOver() {
        AlertDialog.Builder(context)
            .setTitle("继续游戏")
            .setMessage("请点击确认继续游戏")
            .setPositiveButton("确认") { _, _ -> reload() }
            .setNegativeButton("取消", null)
            .create()
            .show()
    }

    // kotlin自动编译为Java静态类，控件引用使用弱引用
    class GameController(view: SnarkGameView): Handler(Looper.getMainLooper()){
        // 控件引用
        private val mRef: WeakReference<SnarkGameView> = WeakReference(view)
        // 蛇体移动控制
        private var mSnarkCounter = 0
        // 食物闪烁控制
        private var mFoodCounter = 0
        // 游戏结束标志
        internal var isGameOver = false

        override fun handleMessage(msg: Message) {
            mRef.get()?.let { gameView ->
                mSnarkCounter++
                if (mSnarkCounter == (SNARK_MOVE_TIME / GAME_FLUSH_TIME).toInt()) {
                    // 移动蛇体
                    var p: Snark? = gameView.mHead
                    var dir = gameView.mHead.dir
                    while (p != null) {
                        // 移动逻辑，会穿过屏幕边界
                        when(p.dir) {
                            DIR_UP -> {
                                p.posY--
                                if (p.posY < 0)  {
                                    p.posY = gameView.rowNumb - 1
                                }
                            }
                            DIR_RIGHT -> {
                                p.posX++
                                if (p.posX >= gameView.colNumb) {
                                    p.posX = 0
                                }
                            }
                            DIR_DOWN -> {
                                p.posY++
                                if (p.posY >= gameView.rowNumb)  {
                                    p.posY = 0
                                }
                            }
                            DIR_LEFT -> {
                                p.posX--
                                if (p.posX < 0) {
                                    p.posX = gameView.colNumb - 1
                                }
                            }
                        }

                        // 死亡逻辑,蛇头撞到身体了
                        if (p != gameView.mHead &&
                            p.posX == gameView.mHead.posX && p.posY == gameView.mHead.posY) {
                            isGameOver = true
                        }

                        // 移动修改方向为上一节的方
                        val temp = p.dir
                        p.dir = dir
                        dir = temp

                        p = p.next
                    }

                    mSnarkCounter = 0
                }

                // 食物控制
                val iterator = gameView.mFoodList.iterator()
                while (iterator.hasNext()) {
                    val food = iterator.next()
                    food.counter++

                    // 食物消失
                    if (food.counter >= (FOOD_ALIVE_TIME / GAME_FLUSH_TIME)) {
                        iterator.remove()
                        continue
                    }

                    // 食物闪烁
                    if (food.counter >= ((FOOD_ALIVE_TIME - FOOD_BLING_TIME) / GAME_FLUSH_TIME)) {
                        food.blingCounter++
                        if (food.blingCounter >= (FOOD_BLING_FREQ / GAME_FLUSH_TIME)) {
                            food.show = !food.show
                            food.blingCounter = 0
                        }
                    }

                    // 食物被吃，添加一节蛇体到尾部
                    if (food.posX == gameView.mHead.posX && food.posY == gameView.mHead.posY) {
                        var x = gameView.mTail.posX
                        var y = gameView.mTail.posY
                        // 在尾部添加
                        when(gameView.mTail.dir) {
                            DIR_UP -> y++
                            DIR_RIGHT -> x--
                            DIR_DOWN -> y--
                            DIR_LEFT -> x++
                        }
                        gameView.mTail.next = Snark(x, y, gameView.mTail.dir,null)
                        gameView.mTail = gameView.mTail.next!!

                        // 移除被吃食物
                        iterator.remove()
                    }
                }

                mFoodCounter++
                if (mFoodCounter == (FOOD_ADD_TIME / GAME_FLUSH_TIME).toInt()) {
                    // 生成食物
                    val x = (Math.random() * gameView.colNumb).toInt()
                    val y = (Math.random() * gameView.rowNumb).toInt()
                    gameView.mFoodList.add(Food(x, y, 0, 0,true))
                    mFoodCounter = 0
                }

                // 循环发送消息，刷新页面
                gameView.invalidate()
                if (!isGameOver) {
                    gameView.mGameController.sendEmptyMessageDelayed(0, GAME_FLUSH_TIME)
                }else {
                    gameView.gameOver()
                }
            }
        }
    }

    data class Food(var posX: Int, var posY: Int, var counter: Int, var blingCounter: Int, var show: Boolean)
    data class Snark(var posX: Int, var posY: Int, var dir: Int, var next: Snark? = null)
}