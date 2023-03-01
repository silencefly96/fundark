### 前言
上一篇文章用贝塞尔曲线画了一个看起来不错的小红点功能，技术上没什么难度，主要就是数学上的计算。这篇文章也差不多，模仿了一个常用的滑动解锁的九宫格控件。

## 需求
用过安卓的都知道，用过苹果的也知道，这里就是一个滑动解锁的控件。核心思想如下：
- 1、摆放九个圆，当手指经过圆附近的时候选取该点，手指移动的时候将选中点连线
- 2、预设一个正确的连线，当手指抬起时的连线与预设连线一致，验证通过
- 3、通过layout参数可以设置圆和线的颜色

## 效果图
这里功能勉强可以吧，感觉选中点的时候不是很流畅。

![pic](https://img-blog.csdnimg.cn/a1b7266b22e940d38cc7962c7e7dada0.gif#pic_center)

## 代码

```java
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet

import com.silencefly96.module_hardware.R

import kotlin.math.sqrt

/**
 * 九宫格控件
 *
 * @author silence
 * @date 2022-11-09
 */
class PatternLockView

        @JvmOverloads
        constructor(
                context:Context,
                attributeSet:AttributeSet?=null,
                defStyleAttr:Int=0
        ):View(context,attributeSet,defStyleAttr){

        /**
         * 预设值
         */
        var preData=LinkedList<Int>()

        /**
         * 回调接口
         */
        var listener:OnMoveUpListener?=null

// 当前值
private var curData=LinkedList<Int>()

// 圆的颜色
private val mCircleColor:Int

// 线的颜色
private val mLineColor:Int

// 圆半径占最短宽高的比例
private val mRadiusPercent:Float

// 两点之间的距离
private var mBetweenLength=0f

// 第一个圆所在位置
private var mStartX=0f
private var mStartY=0f

// 圆半径
private var mRadius=0f

// 当前手指所在的位置
private var mCurPosX=0f
private var mCurPosY=0f

// 是否在移动的状态
private var isMoving=false

// 路径
private var mPath=Path()

// 校验结果， -1失败，0未验证，1验证成功，根据验证结果修改线条颜色
private var mCheckResult=0

// 画笔
private val mPaint=Paint().apply{
        strokeWidth=5f
        style=Paint.Style.STROKE
        flags=Paint.ANTI_ALIAS_FLAG

        // 连接处样式: 平斜接
        strokeJoin=Paint.Join.MITER
        strokeMiter=4f

        // 落笔和结束时那点(point)的样式: 添加半圆
        strokeCap=Paint.Cap.ROUND
        }

        init{
        // 获取布局参数
        val typedArray=
        context.obtainStyledAttributes(attributeSet,R.styleable.PatternLockView)

        mCircleColor=typedArray.getColor(R.styleable.PatternLockView_circleColor,
        Color.LTGRAY)

        mLineColor=typedArray.getColor(R.styleable.PatternLockView_lineColor,Color.YELLOW)

        mRadiusPercent=typedArray.getFraction(R.styleable.PatternLockView_circleRadiusPercent,
        1,1,0.05f)

        typedArray.recycle()
        }

        override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
        val width=getDefaultSize(100,widthMeasureSpec)
        val height=getDefaultSize(100,heightMeasureSpec)

        // 设置参数
        mBetweenLength=(if(width<height)width else height)*0.25f
        mRadius=(if(width<height)width else height)*mRadiusPercent
        mStartX=width/2f-mBetweenLength
        mStartY=height/2f-mBetweenLength

        setMeasuredDimension(width,height)
        }

@SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event:MotionEvent):Boolean{
            when(event.action){
            MotionEvent.ACTION_DOWN->{
            // 清除旧数据
            isMoving=true
            curData.clear()
            mCheckResult=0
            invalidate()
            }
            MotionEvent.ACTION_MOVE->{
            // 判断是否进入哪个点的范围
            val index=getEventCircleIndex(event.x,event.y)
            if(index!=-1&&!curData.contains(index)){
            curData.add(index)
            }

            mCurPosX=event.x
            mCurPosY=event.y
            // 触发绘制
            invalidate()
            }
            MotionEvent.ACTION_UP->{
            isMoving=false
            // 判断是否符合设置的值
            if(curData==preData){
            mCheckResult=1
            listener?.onMoveUp(true)
            }else{
            // 没有连线不触发判断
            if(curData.size>1){
            mCheckResult=-1
            listener?.onMoveUp(false)
            }
            }

            // 最后更新下，把移动的那部分线条去掉
            invalidate()
            }
            }
            return true
            }

private fun getEventCircleIndex(x:Float,y:Float):Int{
        var curX:Float
        var curY:Float
        for(i in 0until 9){
        curX=mStartX+mBetweenLength*(i%3)
        curY=mStartY+mBetweenLength*(i/3)
        if(getDistance(curX,curY,x,y)<=mRadius){
        return i
        }
        }

        return-1
        }

private fun getDistance(x1:Float,y1:Float,x2:Float,y2:Float):Float{
// 平方和公式
@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
        return sqrt((Math.pow((x1-x2).toDouble(),2.0)
                +Math.pow((y1-y2).toDouble(),2.0)).toFloat())
                }

                override fun onDraw(canvas:Canvas){
                super.onDraw(canvas)

                // 先绘制九个点
                var curX:Float
                var curY:Float
                mPaint.color=mCircleColor
                mPaint.style=Paint.Style.FILL
                mPaint.strokeWidth=5f
                for(i in 0until 9){
                curX=mStartX+mBetweenLength*(i%3)
                curY=mStartY+mBetweenLength*(i/3)
                canvas.drawCircle(curX,curY,mRadius,mPaint)
                }

                // 再绘制线，先画固定的线，再画移动中的线
                mPaint.color=when(mCheckResult){
                -1->Color.RED
                1->Color.GREEN
                else->mLineColor
                }
                mPaint.style=Paint.Style.STROKE
                mPaint.strokeWidth=mRadius/3f
                mPath.reset()
                for(i in curData){
                // 当前点坐标
                curX=mStartX+mBetweenLength*(i%3)
                curY=mStartY+mBetweenLength*(i/3)

                if(curData.indexOf(i)==0){
                mPath.moveTo(curX,curY)
                }else{
                mPath.lineTo(curX,curY)
                }
                }
                // 再画最后一点
                if(curData.size>0&&isMoving){
                mPath.lineTo(mCurPosX,mCurPosY)
                }

                canvas.drawPath(mPath,mPaint)
                }

interface OnMoveUpListener {
    fun onMoveUp(success:Boolean)
}
}
```
对应的style文件: pattern_lock_view_style.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <declare-styleable name="PatternLockView">
        <attr name="circleColor" format="color"/>
        <attr name="lineColor" format="color"/>
        <attr name="circleRadiusPercent" format="fraction"/>
    </declare-styleable>
</resources>
```

## 主要问题
实际上，这里没什么有难度的地方，就是画了九个圆，加上几段根据触发的点构成的连线，就不多写了，可以看代码注释。