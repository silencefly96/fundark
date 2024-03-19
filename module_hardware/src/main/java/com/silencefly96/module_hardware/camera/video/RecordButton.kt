package com.silencefly96.module_hardware.camera.video

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import java.lang.ref.WeakReference
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class RecordButton @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attributeSet, defStyleAttr) {
    companion object {
        // handler处理消息类型
        const val MESSAGE_ANIMATING      = 0
        const val MESSAGE_RECORDING      = 1

        // 页面更新间隔
        const val ANIMATE_FLUSH_TIME     = 10L
        const val RECORD_FLUSH_TIME      = 100L

        // 默认录制的最大时长
        const val DEFAULT_RECORD_TIME    = 6_000L
    }

    /**
     * 可录制的最大时长
     */
    var maxRecordTime = DEFAULT_RECORD_TIME

    /**
     * 录像回调
     */
    var recordCallback: RecordCallback? = null

    /**
     * 中间显示的文字
     */
    var textStr: String = "textStr"

    /**
     * 放大倍数
     */
    var zoomSize: String = "zoomSize"

    // 手指刚按下的位置
    private var mStartX = 0f
    private var mStartY = 0f

    // 当前手指所在的位置
    private var mCurPosX = 0f
    private var mCurPosY = 0f

    // 圆的最大半径
    private var mMaxRadius = 0

    // 小圆半径比例
    private var mRadiusPercent = 0.5f

    // 小圆坐标偏移值
    private val mPositionDiff = MutablePair(0f, 0f)

    // 录视频
    private var mRecordPercent = 0f

    // 控制计时的handler
    private val mHandler = RecordHandler(this)

    // 画笔
    private val mPaint = Paint().apply {
        strokeWidth = 10f
        color = Color.RED
        style = Paint.Style.FILL
        flags = Paint.ANTI_ALIAS_FLAG
    }
    private lateinit var mRectF: RectF

    init {
        // 偷个懒，把text放tag里面去了
        textStr = tag.toString()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mMaxRadius = if (w > h) h / 2 else w / 2
        mRectF = RectF(
            w / 2f - mMaxRadius,
            h / 2f - mMaxRadius,
            w / 2f + mMaxRadius,
            h / 2f + mMaxRadius
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 初始位置
                mStartX = event.x
                mStartY = event.y

                // 在小圈内时，请求父布局不拦截事件
                return if ((mStartX - width / 2f).pow(2) + (mStartY - height / 2f).pow(2)
                    <= (mMaxRadius * 0.5f).pow(2)
                ) {
                    parent.requestDisallowInterceptTouchEvent(true)
                    // 开始录制
                    startRecord()
                    true
                }else {
                    // 交给父布局处理
                    parent.requestDisallowInterceptTouchEvent(false)
                    false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // 当前位置
                mCurPosX = event.x
                mCurPosY = event.y
                // 根据位置缩放画面
                handleZoom()
                // 更新页面
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                // 结束录制
                stopRecord()
            }
        }
        return true
    }

    private fun startRecord() {
        Log.d("TAG", "startRecord: ")
        mHandler.removeMessages(0)
        // 录制的同时，给按钮播放一段动画
        mRadiusPercent = 0.5f
        mHandler.sendEmptyMessage(MESSAGE_ANIMATING)
        // 开始更新按钮外圈计时
        mHandler.sendEmptyMessage(MESSAGE_RECORDING)
        // 通知外部开始录像
        recordCallback?.onRecordStart(this)
    }

    private fun handleZoom() {
        // 根据起始位置、当前位置获得小圆位置
        val dx = mCurPosX - mStartX
        val dy = mCurPosY - mStartY

        // 极径和角度
        val length = sqrt(dx.pow(2) + dy.pow(2)).let {
            if (it > mMaxRadius) mMaxRadius.toFloat() else it
        }
        val radians = atan2(dy, dx).toDouble()

        // X轴、Y轴上相对于中心点的偏移值
        mPositionDiff.first = length * cos(radians).toFloat()
        mPositionDiff.second = length * sin(radians).toFloat()

        // 缩放倍数，[0, 1]
        val zoom = length / mMaxRadius
        // zoomSize = String.format("%.2f X", (1 + zoom))

        // 通知外部处理相机缩放
        recordCallback?.onRecordZoom(this, zoom)
    }

    private fun stopRecord() {
        Log.d("TAG", "stopRecord: ")

        // 移除所有消息
        mHandler.removeMessages(MESSAGE_ANIMATING)
        mHandler.removeMessages(MESSAGE_RECORDING)

        // 重置要更新的UI
        mRadiusPercent = 0.5f
        mRecordPercent = 0f
        mPositionDiff.first = 0f
        mPositionDiff.second = 0f
        // 多请求一次绘制，可能会卡住
        postInvalidate()

        // 通知外部停止录制
        recordCallback?.onRecordEnd()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 先画底部背景圆
        mPaint.style = Paint.Style.FILL
        mPaint.color = Color.parseColor("#B2B2B2")
        canvas.drawCircle(width / 2f, height / 2f, mMaxRadius * mRadiusPercent, mPaint)

        // 再画中间圆
        mPaint.color = Color.parseColor("#888888")
        canvas.drawCircle(width / 2f + mPositionDiff.first, height / 2f + mPositionDiff.second,
            mMaxRadius * 0.5f, mPaint)

        // 画边缘进度
        mPaint.color = Color.GREEN
        mPaint.strokeWidth = 10f
        mPaint.style = Paint.Style.STROKE
        val sweepAngle = mRecordPercent * 360
        canvas.drawArc(mRectF, -90f, sweepAngle, false, mPaint)

        // 绘制中间文字
        mPaint.color = Color.BLACK
        mPaint.strokeWidth = 1f
        mPaint.textSize = height / 10f
        mPaint.textAlign = Paint.Align.CENTER
        mPaint.style = Paint.Style.FILL
        canvas.drawText(textStr, width / 2f, getBaseline(mPaint, height / 2f), mPaint)

        // 绘制上面放大倍数(需要父布局配合clipChildren=false来显示)
        mPaint.color = Color.WHITE
        canvas.drawText(zoomSize, width / 2f, - height * 0.1f, mPaint)
    }

    private fun getBaseline(paint: Paint, tempY: Float): Float {
        //绘制字体的参数，受字体大小样式影响
        val fmi = paint.fontMetricsInt
        //top为基线到字体上边框的距离（负数），bottom为基线到字体下边框的距离（正数）
        //基线中间点的y轴计算公式，即中心点加上字体高度的一半，基线中间点x就是中心点x
        return tempY - (fmi.top + fmi.bottom) / 2f
    }

    // 用来处理计时和动画的handler
    private class RecordHandler(view: RecordButton): Handler(Looper.getMainLooper()){
        // 控件引用
        private val mRef: WeakReference<RecordButton> = WeakReference(view)

        override fun handleMessage(msg: Message) {
            mRef.get()?.let { view ->
                when(msg.what) {
                    MESSAGE_ANIMATING -> {
                        // 中间按钮向外扩散,扩散完开始录制
                        if (view.mRadiusPercent < 1f) {
                            view.mRadiusPercent += 0.01f
                            view.postInvalidate()
                            view.mHandler.sendEmptyMessageDelayed(MESSAGE_ANIMATING, ANIMATE_FLUSH_TIME)
                        }else {
                            view.mHandler.removeMessages(MESSAGE_ANIMATING)
                        }
                    }
                    MESSAGE_RECORDING -> {
                        // 未到最大时长，或者未停止，就继续更新
                        if (view.mRecordPercent < 1f) {
                            // 更新外圈进度
                            view.mRecordPercent += (RECORD_FLUSH_TIME / view.maxRecordTime.toFloat())
                            Log.d("TAG", "handleMessage peer: ${(RECORD_FLUSH_TIME / view.maxRecordTime)}")
                            Log.d("TAG", "handleMessage: ${view.mRecordPercent}")
                            view.postInvalidate()
                            view.mHandler.sendEmptyMessageDelayed(MESSAGE_RECORDING, RECORD_FLUSH_TIME)
                        } else {
                            // 到达最大时长
                            view.mHandler.removeMessages(MESSAGE_RECORDING)
                            view.stopRecord()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * 录像回调
     */
    interface RecordCallback {
        fun onRecordStart(button: RecordButton)
        fun onRecordZoom(button: RecordButton, zoom: Float)   // zoom范围[0, 1]
        fun onRecordEnd()
    }

    data class MutablePair(var first: Float, var second: Float)
}