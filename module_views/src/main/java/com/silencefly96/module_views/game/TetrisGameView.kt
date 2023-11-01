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
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.util.Consumer
import com.silencefly96.module_views.R
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.collections.indices as indices1

/**
 * 俄罗斯方块view
 */
class TetrisGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
): View(context, attrs, defStyleAttr) {
    companion object {
        // 游戏更新间隔，一秒5次
        const val GAME_FLUSH_TIME = 200L
        // 砖块移动间隔，一秒2.5次
        const val TETRIS_MOVE_TIME = 400L
        // 快速模式把更新时间等分
        const val FAST_MOD_TIMES = 10

        // 四个方向
        const val DIR_NULL = -1
        const val DIR_UP = 0
        const val DIR_RIGHT = 1
        const val DIR_DOWN = 2
        const val DIR_LEFT = 3

        // 四种砖块对应的配置，是一个2 * 8的数组, 这里用二进制来保存
        // 顶点左上角，二行四列，默认朝右，方向变换咦左上角旋转
        private const val CONFIG_TYPE_L = 0b1110_1000
        private const val CONFIG_TYPE_T = 0b1110_0100
        private const val CONFIG_TYPE_I = 0b1111_0000
        private const val CONFIG_TYPE_O = 0b0110_0110

        // 砖块类型数组，用于随机生成
        val sTypeArray = intArrayOf(CONFIG_TYPE_L, CONFIG_TYPE_T, CONFIG_TYPE_I, CONFIG_TYPE_O)
    }

    // 屏幕划分数量及等分长度
    private val mRowNumb: Int
    private var mRowDelta: Int = 0
    private val mColNumb: Int
    private var mColDelta: Int = 0

    // 节点掩图
    private val mTetrisMask: Bitmap?

    // 游戏地图是个二维数组
    private val mGameMap: Array<IntArray>

    // 当前操作的方块
    private val mTetris = Tetris(0, 0, 0, 0)

    // 不要在onDraw中创建对象, 在onDraw中临时计算绘制位置
    private val mPositions = ArrayList<MutableTriple<Int, Int, Boolean>>(8).apply {
        for (i in 0..7) add(MutableTriple(0, 0, false))
    }

    // 游戏控制器
    private val mGameController = GameController(this)

    // 画笔
    private val mPaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 1f
        style = Paint.Style.STROKE
        flags = Paint.ANTI_ALIAS_FLAG
    }

    // 上一个触摸点X、Y的坐标
    private var mLastX = 0f
    private var mLastY = 0f

    init {
        // 读取配置
        val typedArray =
            context.obtainStyledAttributes(attrs, R.styleable.TetrisGameView)

        // 横竖划分
        mRowNumb = typedArray.getInteger(R.styleable.TetrisGameView_rowNumber, 30)
        mColNumb = typedArray.getInteger(R.styleable.TetrisGameView_colNumber, 20)

        // 根据行数和列数生成地图
        mGameMap = Array(mRowNumb){ IntArray(mColNumb)}

        // 节点掩图
        val drawable = typedArray.getDrawable(R.styleable.TetrisGameView_tetrisMask)
        mTetrisMask = if (drawable != null) drawableToBitmap(drawable) else null

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
        mRowDelta = h / mRowNumb
        mColDelta = w / mColNumb
        // 开始游戏
        load()
    }

    // 加载
    private fun load() {
        mGameController.removeMessages(0)
        mGameController.sendEmptyMessageDelayed(0, GAME_FLUSH_TIME)
    }

    // 重新加载
    private fun reload() {
        mGameController.removeMessages(0)
        // 清空界面
        for (array in mGameMap) {
            array.fill(0)
        }
        mGameController.isNewTurn = true
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
        // 绘制网格，mColDelta * mColNumb.toFloat() != width
        for (i in 0..mRowNumb) {
            canvas.drawLine(0f, mRowDelta * i.toFloat(),
                mColDelta * mColNumb.toFloat(), mRowDelta * i.toFloat(), mPaint)
        }
        for (i in 0..mColNumb) {
            canvas.drawLine(mColDelta * i.toFloat(), 0f,
                mColDelta * i.toFloat(), mRowDelta * mRowNumb.toFloat(), mPaint)
        }

        // 绘制地图元素, (i, j)表示第i行，第j列
        for (i in mGameMap.indices1) {
            val array = mGameMap[i]
            for (j in array.indices1) {
                if (mGameMap[i][j] > 0) {
                    canvas.drawBitmap(mTetrisMask!!,
                        j * mColDelta.toFloat() + mColDelta / 2 - mTetrisMask.width / 2,
                        i * mRowDelta.toFloat() + mRowDelta / 2 - mTetrisMask.height / 2,
                        mPaint)
                }
            }
        }

        // 绘制当前砖块，仅绘制，碰撞、旋转由GameController控制
        for (pos in mPositions) {
            if (pos.third) {
                canvas.drawBitmap(mTetrisMask!!,
                    pos.second * mColDelta.toFloat() + mColDelta / 2 - mTetrisMask.width / 2,
                    pos.first * mRowDelta.toFloat() + mRowDelta / 2 - mTetrisMask.height / 2,
                    mPaint)
            }
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

                // 只更改方向，逻辑由GameController处理，方向更改成功与否需要确认
                if (abs(lenX) > abs(lenY)) {
                    // 左右移动
                    // val delta = (lenX / mColDelta).toInt()
                    // mGameController.colDelta = delta
                    mGameController.colDelta = if (lenX > 0) 1 else -1
                }else {
                    if (lenY >= 0) {
                        // 往下滑动加快
                        mTetris.fastMode = true
                    }else {
                        // 往上移动切换形态
                        mGameController.newDirection = mTetris.dir + 1
                        if (mGameController.newDirection > 3) {
                            mGameController.newDirection = 0
                        }
                    }
                }
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
    class GameController(view: TetrisGameView): Handler(Looper.getMainLooper()){
        // 控件引用
        private val mRef: WeakReference<TetrisGameView> = WeakReference(view)
        // 防止大量生成对象
        private val mTempPositions =
            ArrayList<MutableTriple<Int, Int, Boolean>>(8).apply {
            for (i in 0..7) add(MutableTriple(0, 0, false))
        }
        // 新砖块
        internal var isNewTurn = true
        // 左右移动
        internal var colDelta = 0
        // 更改的新方向
        internal var newDirection = DIR_NULL
        // 游戏结束标志
        internal var isGameOver = false
        // 砖块移动控制变量，让左右移动能比向下移动快一步
        private var mMoveCounter = 0

        override fun handleMessage(msg: Message) {
            mRef.get()?.let { gameView ->
                // 新一轮砖块
                startNewTurn(gameView)

                // 判断并旋转砖块
                if (checkDirection(gameView)) {
                    updateTetris(gameView)
                }

                // 判断并左右移动砖块
                if (checkHorizontalMove(gameView)) {
                    updateTetris(gameView)
                }

                // 向下移动比旋转和左右移动慢一步
                delayMove {
                    // 判断并向下移动砖块
                    if (checkDownMove(gameView)) {
                        gameView.mTetris.posRow += 1
                        updateTetris(gameView)
                    } else {
                        // 固定砖块，只有向下移动时才判断
                        settleTetris(gameView)
                        // 检查消除底层
                        checkRemove(gameView)
                    }
                }

                // 循环发送消息，刷新页面
                gameView.invalidate()
                if (!isGameOver) {
                    if (gameView.mTetris.fastMode) {
                        gameView.mGameController.sendEmptyMessageDelayed(0,
                            GAME_FLUSH_TIME / FAST_MOD_TIMES)
                    }else {
                        gameView.mGameController.sendEmptyMessageDelayed(0, GAME_FLUSH_TIME)

                    }
                }else {
                    gameView.gameOver()
                }
            }
        }

        private fun startNewTurn(gameView: TetrisGameView) {
            if (isNewTurn) {
                // 保留旋转空余
                val col = (3 + Math.random() * (gameView.mColNumb - 6)).toInt()
                val type = sTypeArray[(Math.random() * 4).toInt()]
                gameView.mTetris.dir = (Math.random() * 4).toInt()
                // 因为旋转所以要保证在界面内
                gameView.mTetris.posRow = -2 + when(gameView.mTetris.dir) {
                    DIR_LEFT -> 1
                    DIR_UP -> 2
                    else -> 0
                }
                gameView.mTetris.posCol = col
                gameView.mTetris.config = type
                gameView.mTetris.fastMode = false

                isNewTurn = false
            }
        }

        private fun checkDirection(gameView: TetrisGameView): Boolean {
            var shouldUpdate = false

            // 方向预测
            if (newDirection != DIR_NULL) {
                val tetris = gameView.mTetris
                getPositions(mTempPositions, tetris, tetris.posRow, tetris.posCol, newDirection)
                shouldUpdate = checkOutAndOverlap(mTempPositions, gameView.mRowNumb, gameView.mColNumb,
                    gameView.mGameMap)

                // 修改值，等下一步更新
                if (shouldUpdate) {
                    tetris.dir = newDirection
                }
                newDirection = DIR_NULL
            }

            return shouldUpdate
        }

        private fun checkHorizontalMove(gameView: TetrisGameView): Boolean {
            var shouldUpdate = false

            // 左右预测
            if (colDelta != 0) {
                val tetris = gameView.mTetris
                getPositions(mTempPositions, tetris, tetris.posRow, tetris.posCol + colDelta, tetris.dir)
                shouldUpdate = checkOutAndOverlap(mTempPositions, gameView.mRowNumb, gameView.mColNumb,
                    gameView.mGameMap)

                // 修改值，等下一步更新
                if (shouldUpdate) {
                    tetris.posCol += colDelta
                }
                colDelta = 0
            }

            return shouldUpdate
        }
        private fun delayMove(func: Consumer<Boolean>) {
            // 对向下移动控制，左右移动、旋转不限制
            mMoveCounter++
            if (mMoveCounter == (TETRIS_MOVE_TIME / GAME_FLUSH_TIME).toInt()) {
                func.accept(true)
                mMoveCounter = 0
            }
        }

        private fun checkDownMove(gameView: TetrisGameView): Boolean {
            val tetris = gameView.mTetris
            // 向下移动预测
            getPositions(mTempPositions, tetris, tetris.posRow + 1, tetris.posCol, tetris.dir)
            return checkOutAndOverlap(mTempPositions, gameView.mRowNumb, gameView.mColNumb,
                gameView.mGameMap)
        }

        // 根据条件获得positions，直接定义不同方向的dir_type会更好吗？其实也要确定锚点，一样的
        private fun getPositions(positions: ArrayList<MutableTriple<Int, Int, Boolean>>,
            tetris: Tetris, posRow: Int, posCol: Int, dir: Int) {

            // 优化旋转方向
            var optimizedDir = dir
            // 对方块和条形类型特别优化，O型不需要变换，I型只要两种状态
            if (tetris.config == CONFIG_TYPE_O) optimizedDir = tetris.dir
            if (tetris.config == CONFIG_TYPE_I) optimizedDir = dir.mod(2)

            // 位置映射
            for (i in 0..1) for (j in 0..3) {
                val index = i * 4 + j
                // 按位取得配置
                val mask = 1 shl (7 - index)
                val flag = tetris.config and mask == mask
                val triple = positions[index]
                // 将不同方向对应的位置转换到config的顺序，并保存该位置是否绘制的flag
                triple.third = flag
                when(optimizedDir) {
                    // 以o为锚点旋转，再优化，左边为旋转后，右边为优化后，目的：减小影响范围，限制在矩形内
                    // 一开始以右向左上角旋转，范围是7*7，可通过取值的变换，变换为5*5或者4*4的矩阵
                    // - x x - -
                    // - x x x -      - x x -
                    // x x o x x      x o o x
                    // x x x x x      x o o x
                    // - - x x -  =>  - x x -

                    // 右向，基础型
                    // o x x x      x o x x
                    // x x x x  ->  x x x x
                    DIR_RIGHT -> {
                        triple.first = posRow + i
                        triple.second = posCol + j - 1
                    }
                    // 下向
                    // x o      x x
                    // x x      x o
                    // x x      x x
                    // x x  ->  x x
                    DIR_DOWN -> {
                        triple.first = posRow + j - 1
                        triple.second = posCol - i
                    }
                    // 左向
                    // x x x x      x x x x
                    // x x x o  ->  x x o x
                    DIR_LEFT -> {
                        triple.first = posRow - i
                        triple.second = posCol - j + 1
                    }
                    // 上向
                    // x x      x x
                    // x x      x x
                    // x x      o x
                    // o x  ->  x x
                    DIR_UP -> {
                        triple.first = posRow - j + 1
                        triple.second = posCol + i
                    }
                    else -> {}
                }
            }
        }

        // 检测出界和重叠，下边和左右
        private fun checkOutAndOverlap(positions: ArrayList<MutableTriple<Int, Int, Boolean>>,
            rowNumb: Int, colNumb: Int, gameMap: Array<IntArray>): Boolean {
            var flag = true
            for (pos in positions) {
                // 只对有值的位置进行验证
                if(!pos.third) continue

                // 出下界
                if (pos.first >= rowNumb) {
                    flag = false
                    break
                }

                // 左右出界
                if (pos.second >= colNumb || pos.second < 0) {
                    flag = false
                    break
                }

                // 旋转后有冲突，暂且忽略上边之外的情况
                if (pos.first < 0) continue
                if (gameMap[pos.first][pos.second] > 0) {
                    flag = false
                    break
                }
            }

            return flag
        }

        private fun updateTetris(gameView: TetrisGameView) {
            val tetris = gameView.mTetris
            getPositions(gameView.mPositions, tetris, tetris.posRow, tetris.posCol, tetris.dir)
        }

        private fun settleTetris(gameView: TetrisGameView) {
            // 固定砖块的位置，moveTetris已经将位置放到了gameView.mPositions中
            Log.d("TAG", "type: ${gameView.mTetris.config}，settleTetris: ${gameView.mPositions}")
            for (pos in gameView.mPositions) {
                if (pos.third) {
                    // 注意这里位置超出屏幕上方就是游戏结束
                    if (pos.first < 0) {
                        isGameOver = true
                    }else {
                        gameView.mGameMap[pos.first][pos.second] = 1
                    }
                }
            }

            isNewTurn = true
        }

        private fun checkRemove(gameView: TetrisGameView) {
            // 应该从顶层到底层检查，这样消除后的移动逻辑才没错，就是复杂了点
            val gameMap = gameView.mGameMap
            for (i in gameMap.indices1) {
                val array = gameMap[i]
                var isFull = true
                for (peer in array) {
                    if (peer <= 0) {
                        isFull = false
                    }
                }

                // 消除，数组移位就行了
                if (isFull) {
                    for (j in (i - 1) downTo 0) {
                        // 把上面一层的数据填到当前层即可，最后会填到空层
                        val cur = gameMap[j + 1]
                        val another = gameMap[j]
                        for (k in cur.indices1) {
                            cur[k] = another[k]
                        }
                    }
                    // 最顶上填空
                    gameMap[0].fill(0)
                }
            }
        }
    }

    /**
     * 供外部回收资源
     */
    fun recycle()  {
        mTetrisMask?.recycle()
        mGameController.removeMessages(0)
    }

    // 砖块，以左上角为旋转中心旋转
    data class Tetris(
        var posRow: Int,
        var posCol: Int,
        var dir: Int,
        var config: Int,
        var fastMode: Boolean = false)

    data class MutableTriple<T, V, R>(var first: T, var second: V, var third: R)
}