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
import kotlin.math.*

/**
 * 弹球游戏view
 */
class BombBallGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
): View(context, attrs, defStyleAttr) {
    companion object {
        // 游戏更新间隔，一秒20次
        const val GAME_FLUSH_TIME = 50L
        // 目标移动距离
        const val TARGET_MOVE_DISTANCE = 20

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

    // 板的长度
    private val mLength: Int

    // 行的数量、间距
    private val rowNumb: Int
    private var rowDelta = 0

    // 列的数量、间距
    private val colNumb: Int
    private var colDelta = 0

    // 球的掩图
    private val mBallMask: Bitmap?

    // 目标的掩图
    private val mTargetMask: Bitmap?

    // 目标的原始配置
    private val mTargetConfigList = ArrayList<Sprite>()

    // 目标的集合
    private val mTargetList = ArrayList<Sprite>()

    // 球
    private val mBall = Sprite(0, 0, 0f)

    // 板
    private val mBoard = Sprite(0, 0, 0f)

    // 游戏控制器
    private val mGameController = GameController(this)

    // 上一个触摸点X的坐标
    private var mLastX = 0f

    // 画笔
    private val mPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 10f
        style = Paint.Style.STROKE
        flags = Paint.ANTI_ALIAS_FLAG
        textAlign = Paint.Align.CENTER
        textSize = 30f
    }

    init {
        // 读取配置
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.BombBallGameView)
        mLength = typedArray.getInteger(R.styleable.BombBallGameView_length, 300)
        rowNumb = typedArray.getInteger(R.styleable.BombBallGameView_row, 30)
        colNumb = typedArray.getInteger(R.styleable.BombBallGameView_col, 20)

        // 球的掩图
        var drawable = typedArray.getDrawable(R.styleable.BombBallGameView_ballMask)
        mBallMask = if (drawable != null) drawableToBitmap(drawable) else null
        // 目标的掩图
        drawable = typedArray.getDrawable(R.styleable.BombBallGameView_targetMask)
        mTargetMask = if (drawable != null) drawableToBitmap(drawable) else null

        // 读取目标的布局配置
        val configId = typedArray.getResourceId(R.styleable.BombBallGameView_targetConfig, -1)
        if (configId != -1) {
            getTargetConfig(configId)
        }
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

    private fun getTargetConfig(configId: Int) {
        val array = resources.getStringArray(configId)
        try {
            for (str in array) {
                // 取出坐标
                val pos = str.substring(1, str.length - 1).split(",")
                val x = pos[0].trim().toInt()
                val y = pos[1].trim().toInt()
                mTargetConfigList.add(Sprite(x, y, 0f))
            }
        }catch (e : Exception) {
            e.printStackTrace()
        }
        // 填入游戏的list
        mTargetList.clear()
        mTargetList.addAll(mTargetConfigList)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 开始游戏
        load()
    }

    // 加载
    private fun load() {
        mGameController.removeMessages(0)
        // 设置网格
        rowDelta = height / rowNumb
        colDelta = width / colNumb
        // 设置球，随机朝下的方向
        mBall.posX = width / 2
        mBall.posY = height / 2
        mBall.degree = (Math.random() * 180 + 180).toFloat()
        // 设置板
        mBoard.posX = width / 2
        mBoard.posY = height - 50
        // 将目标集合中的坐标改为实际坐标
        for (target in mTargetList) {
            val exactX = target.posY * colDelta + colDelta / 2
            val exactY = target.posX * rowDelta + rowDelta / 2
            target.posX = exactX
            target.posY = exactY
        }
        mGameController.sendEmptyMessageDelayed(0, GAME_FLUSH_TIME)
    }

    // 重新加载
    private fun reload() {
        mGameController.removeMessages(0)
        // 重置
        mTargetList.clear()
        mTargetList.addAll(mTargetConfigList)
        mGameController.isGameOver = false
        // 设置球，随机朝下的方向，注意：因为Y轴朝下应该是180度以内
        mBall.posX = width / 2
        mBall.posY = height / 2
        mBall.degree = (Math.random() * 180).toFloat()
        // 设置板
        mBoard.posX = width / 2
        mBoard.posY = height - 50
        // 由于mTargetConfigList内对象被load修改了，清空并不影响对象，不需要再转换了
        mGameController.sendEmptyMessageDelayed(0, GAME_FLUSH_TIME)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 绘制网格
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

        // 绘制板
        canvas.drawLine(mBoard.posX - mLength / 2f, mBoard.posY.toFloat(),
            mBoard.posX + mLength / 2f, mBoard.posY.toFloat(), mPaint)

        // 绘制球
        canvas.drawBitmap(mBallMask!!, mBall.posX - mBallMask.width / 2f,
            mBall.posY - mBallMask.height / 2f, mPaint)

        // 绘制目标物
        for (target in mTargetList) {
            canvas.drawBitmap(mTargetMask!!, target.posX - mTargetMask.width / 2f,
                target.posY - mTargetMask.height / 2f, mPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                mLastX = event.x
            }
            MotionEvent.ACTION_MOVE -> {
                val len = event.x - mLastX
                val preX = mBoard.posX + len
                if (preX > mLength / 2 && preX < (width - mLength / 2)) {
                    mBoard.posX += len.toInt()
                    invalidate()
                }
                mLastX = event.x
            }
            MotionEvent.ACTION_UP -> {}
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
    class GameController(view: BombBallGameView): Handler(Looper.getMainLooper()){
        // 控件引用
        private val mRef: WeakReference<BombBallGameView> = WeakReference(view)
        // 游戏结束标志
        internal var isGameOver = false

        override fun handleMessage(msg: Message) {
            mRef.get()?.let { gameView ->
                // 移动球
                val radian = Math.toRadians(gameView.mBall.degree.toDouble())
                val deltaX = (TARGET_MOVE_DISTANCE * cos(radian)).toInt()
                val deltaY = (TARGET_MOVE_DISTANCE * sin(radian)).toInt()
                gameView.mBall.posX += deltaX
                gameView.mBall.posY += deltaY
                // 检查反弹碰撞
                checkRebound(gameView)

                // 球和目标的碰撞
                val iterator = gameView.mTargetList.iterator()
                while (iterator.hasNext()) {
                    val target = iterator.next()
                    if (checkCollision(gameView.mBall, target,
                            gameView.mBallMask!!, gameView.mTargetMask!!)) {
                        // 与目标碰撞，移除该目标并修改球的方向
                        iterator.remove()
                        collide(gameView.mBall, target)
                        break
                    }
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

        // 检测碰撞
        private fun checkCollision(s1: Sprite, s2: Sprite, mask1: Bitmap, mask2: Bitmap): Boolean {
            // 选较长边的一半作为碰撞半径
            val len1 = if(mask1.width > mask1.height) mask1.width / 2f else mask1.height / 2f
            val len2 = if(mask2.width > mask2.height) mask2.width / 2f else mask2.height / 2f
            return getDistance(s1.posX, s1.posY, s2.posX, s2.posY) <= (len1 + len2)
        }

        // 击中目标时获取反弹角度,角度以两球圆心连线对称并加180度
        private fun collide(ball: Sprite, target: Sprite) {
            // 圆心连线角度，注意向量方向，球的方向向上，连线以球为起点
            val lineDegree = getDegree(ball.posX.toFloat(), ball.posY.toFloat(),
                target.posX.toFloat(), target.posY.toFloat())
            val deltaDegree = abs(lineDegree - ball.degree)
            ball.degree += if(lineDegree > ball.degree) {
                2 * deltaDegree.toFloat() + 180
            }else {
                -2 * deltaDegree.toFloat() + 180
            }
        }

        // 击中边缘或者板时反弹角度,反射角度和法线对称，方向相反
        private fun checkRebound(gameView: BombBallGameView) {
            val ball = gameView.mBall
            val board = gameView.mBoard
            // 左边边缘，法线取同向的180度
            if (ball.posX <= 0) {
                val deltaDegree = abs(180 - ball.degree)
                ball.degree += if (ball.degree < 180)  {
                    2 * deltaDegree - 180
                }else {
                    -2 * deltaDegree - 180
                }
            // 右边边缘
            }else if (ball.posX >= gameView.width) {
                val deltaDegree: Float
                ball.degree += if (ball.degree < 180)  {
                    deltaDegree = ball.degree - 0
                    -2 * deltaDegree + 180
                }else {
                    deltaDegree = 360 - ball.degree
                    2 * deltaDegree - 180
                }
            // 上边边缘
            }else if(ball.posY <= 0) {
                val deltaDegree = abs(90 - ball.degree)
                ball.degree += if (ball.degree < 90)  {
                    2 * deltaDegree + 180
                }else {
                    -2 * deltaDegree + 180
                }
            // 和板碰撞，因为移动距离的关系y不能完全相等
            }else if (ball.posY + gameView.mBallMask!!.height / 2 >= board.posY) {
                // 板内
                if (abs(ball.posX - board.posX) <= gameView.mLength / 2){
                    val deltaDegree = abs(270 - ball.degree)
                    ball.degree += if (ball.degree < 270)  {
                        2 * deltaDegree - 180
                    }else {
                        -2 * deltaDegree - 180
                    }
                }else {
                    isGameOver = true
                }
            }
        }
    }

    // 圆心坐标，角度方向（degree，对应弧度radian）
    data class Sprite(var posX: Int, var posY: Int, var degree: Float)

    /**
     * 供外部回收资源
     */
    fun recycle()  {
        mBallMask?.recycle()
        mTargetMask?.recycle()
        mGameController.removeMessages(0)
    }
}
