# 自定义view实战(12)：安卓粒子线条效果

### 前言
很久没写代码了，忙工作、忙朋友、人也懒了，最近重新调整自己，对技术还是要有热情，要热情的话还是用自定义view做游戏有趣，写完这个粒子线条后面我会更新几个小游戏博文及代码，希望读者喜欢。

这个粒子效果的控件是去年写的，写的很差劲，这几天又重构了一下，还是难看的要命，勉强记录下吧。

## 需求
主要就是看到博客园的粒子线条背景很有意思，就想模仿一下。核心思想如下：
- 1、随机出现点
- 2、范围内的点连线
- 3、手指按下，加入点，范围内点向手指移动

## 效果图
效果图就是难看，没得说。


## 代码
```java
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.lang.ref.WeakReference
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 模仿博客粒子线条的view
 *
 * 核心思想简易版
 *
 * 1、随机出现点
 * 2、范围内的点连线
 * 3、手指按下，加入点，范围内点向手指移动
 *
 * @author silence
 * @date 2022-11-09
 *
 */
class ParticleLinesBgView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
): View(context, attributeSet, defStyleAttr){

    companion object{
        // 屏幕刷新时间，每秒20次
        const val SCREEN_FLUSH_TIME = 50L

        // 新增点的间隔时间
        const val POINT_ADD_TIME = 200L

        // 粒子存活时间
        const val POINT_ALIVE_TIME = 18000L

        // 吸引的合适距离
        const val ATTRACT_LENGTH = 250f

        // 维持的合适距离
        const val PROPER_LENGTH = 150f

        // 粒子被吸引每次接近的距离
        const val POINT_MOVE_LENGTH = 30f

        // 距离计算公式
        fun getDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            return sqrt(((x1 - x2).toDouble().pow(2.0)
                    + (y1 - y2).toDouble().pow(2.0)).toFloat())
        }
    }

    // 存放的粒子
    private val mParticles = ArrayList<Particle>(64)

    // 手指按下位置
    private var mTouchParticle: Particle? = null

    // 处理的handler
    private val mHandler = ParticleHandler(this)

    // 画笔
    private val mPaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 3f
        style = Paint.Style.STROKE
        flags = Paint.ANTI_ALIAS_FLAG
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 通过发送消息给handler实现间隔添加点
        mHandler.removeMessages(0)
        mHandler.sendEmptyMessageDelayed(0, POINT_ADD_TIME)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 绘制点和线
        for (i in 0 until mParticles.size) {
            val point = mParticles[i]
            canvas.drawPoint(point.x, point.y, mPaint)
            // 连线
            for (j in (i + 1) until mParticles.size) {
                val another = mParticles[j]
                val distance = getDistance(point.x, point.y, another.x, another.y)
                if (distance <= PROPER_LENGTH) {
                    canvas.drawLine(point.x, point.y, another.x, another.y, mPaint)
                }
            }
        }

        mTouchParticle?.let {
            // 手指按下点与附近连线
            for(point in mParticles) {
                val distance = getDistance(point.x, point.y, it.x, it.y)
                if (distance <= PROPER_LENGTH) {
                    canvas.drawLine(point.x, point.y, it.x, it.y, mPaint)
                }
            }

            // 吸引范围显示
            mPaint.color = Color.BLUE
            canvas.drawCircle(it.x, it.y, PROPER_LENGTH, mPaint)
            mPaint.color = Color.LTGRAY
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                mTouchParticle = Particle(event.x, event.y, 0)
            }
            MotionEvent.ACTION_MOVE -> {
                mTouchParticle!!.x = event.x
                mTouchParticle!!.y = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                mTouchParticle = null
            }
        }
        return true
    }

    // 粒子
    class Particle(var x: Float, var y: Float, var counter: Int)

    // kotlin自动编译为Java静态类，控件引用使用弱引用
    class ParticleHandler(view: ParticleLinesBgView): Handler(Looper.getMainLooper()){
        // 控件引用
        private val mRef: WeakReference<ParticleLinesBgView> = WeakReference(view)
        // 粒子出现控制
        private var mPointCounter = 0

        override fun handleMessage(msg: Message) {
            mRef.get()?.let {view->
                // 新增点
                mPointCounter++
                if (mPointCounter == (POINT_ADD_TIME / SCREEN_FLUSH_TIME).toInt()) {
                    // 随机位置
                    val x = (Math.random() * view.width).toFloat()
                    val y = (Math.random() * view.height).toFloat()
                    view.mParticles.add(Particle(x, y, 0))
                    mPointCounter = 0
                }

                val iterator = view.mParticles.iterator()
                while (iterator.hasNext()) {
                    val point = iterator.next()

                    // 移除失活粒子
                    if (point.counter == (POINT_ALIVE_TIME / SCREEN_FLUSH_TIME).toInt()) {
                        iterator.remove()
                    }

                    // 手指按下时，粒子朝合适的距离移动
                    view.mTouchParticle?.let {
                        val distance = getDistance(point.x, point.y, it.x, it.y)
                        if(distance in PROPER_LENGTH..ATTRACT_LENGTH) {
                            // 横向接近
                            if (point.x < it.x) point.x += POINT_MOVE_LENGTH
                            else point.x -= POINT_MOVE_LENGTH
                            // 纵向接近
                            if (point.y < it.y) point.y += POINT_MOVE_LENGTH
                            else point.y -= POINT_MOVE_LENGTH
                        }else if(distance <= PROPER_LENGTH) {
                            // 横向远离
                            if (point.x < it.x) point.x -= POINT_MOVE_LENGTH
                            else point.x += POINT_MOVE_LENGTH
                            // 纵向远离
                            if (point.y < it.y) point.y -= POINT_MOVE_LENGTH
                            else point.y += POINT_MOVE_LENGTH
                        }
                    }
                }

                // 循环发送
                view.invalidate()
                view.mHandler.sendEmptyMessageDelayed(0, POINT_ADD_TIME)
            }
        }
    }
}
```
这里没写onMeasure，注意下不能用wrap-content，布局的话改个黑色背景就行了。

## 主要问题
下面简单讲讲吧。

### 粒子
这里用了个数据类构造了粒子，用了一个ArrayList来存放，本来想用linkedHashMap来保存并实现下LRU的，结果连线的时候比较复杂，重构的时候直接删了，后面用了一个counter来控制粒子的存活时间。

### 逻辑控制
一开始的时候想的比较复杂，实现来弄得自己头疼，后面觉得何不将逻辑和绘制分离，在ondraw里面只进行绘制不就行了，逻辑通过handler来更新，实际这样在我看来是对的。

我这用了一个Handler配合嵌套循环发送空消息，实现定时更新效果，每隔一段时间更新一下逻辑，Handler内部通过弱引用获得view，并对其中的内容修改，修改完成后，通过invalidate出发线程更新。

### 新增点
Handler会定时更新，只需要在handleMessage里面添加点就行了，为了控制点出现的频率，我这又引入了控制变量。

### 粒子生命周期
handleMessage里面会检查粒子是否失活，失活了就通过iterator去移除，移除数组内内容还是尽量通过iterator去实现吧，特别是for-eacn循环以及for循环内删除多个时，会出错的！

### 粒子趋向于手指
手指按下时设置mTouchParticle，移动时更新这个mTouchParticle，手指抬起时对mTouchParticle赋空，这样在handleMessage里面只要在mTouchParticle不为空时稍稍改变下其他粒子的位置，就可以达到趋向效果。

### 粒子连线
这里我没有想到什么好办法，就是暴力破解，直接两两计算，并对合适距离的粒子进行连线。