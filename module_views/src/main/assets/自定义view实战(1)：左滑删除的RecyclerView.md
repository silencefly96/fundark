### 概述
最近安卓自定义view的知识看的很熟，但是却很久没动手了，这几天用kotlin手撕了原先一个左滑删除的RecyclerView，居然弄得有点懵逼。后面又慢慢改进、加东西，发现这样一个例子下来，自定义View以及事件分发的知识居然覆盖的差不多了，所以有了写博客的想法。下面我会从我的思路一点点的写下去，碰到的各种问题就是知识的实际应用了，通过问题学知识，我觉得这样的方式非常好！


### 需求
这里我要做的是一个左滑删除列表项的功能，之前拿过一个别人的用，所以有了一点思路，但是不深刻。于是我开始从零出发，先写个大致思路再一步步去解决，首先肯定的是通过继承RecyclerView去实现，后面思路大致如下：
- 在 down 事件中，判断在列表内位置,得到对应 item
- 拦截 move 事件，item 跟随滑动，最大距离为删除按钮长度
- 在 up 事件中，确定最终状态，固定 item 位置


### 编写代码I
根据上面三点思路，我刷刷地就写下了下面的代码：
```kotlin
class SlideDeleteRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    //流畅滑动
    private var mScroller = Scroller(context)
    //当前选中item
    private var mItem: ViewGroup? = null
    //上次按下横坐标
    private var mLastX = 0f

    override fun onInterceptTouchEvent(e: MotionEvent?): Boolean {
        e?.let {
           when(e.action) {
               MotionEvent.ACTION_DOWN -> {
                   //获取点击位置
                   getSelectItem(e)
                   //设置点击的横坐标
                   mLastX = e.x
               }
               MotionEvent.ACTION_MOVE -> {
                   //不管左右都应该让item跟随滑动
                   moveItem(e)
                   //拦截事件
                   return true
               }
               MotionEvent.ACTION_UP -> {
                   //判断结果
                   stopMove(e)
               }
           }
        }
        return super.onInterceptTouchEvent(e)
    }

    //滑动结束
    //版本一：判断一下结束的位置，补充或恢复位置
    private fun stopMove(e: MotionEvent) {
        mItem?.let {
            val dx = e.x - mLastX
            //如果移动过半了，应该判定左滑成功
            val deleteWidth = it.getChildAt(it.childCount - 1).width
            if (abs(dx) >= deleteWidth / 2) {
                //触发移动
                val left = if (dx > 0) {
                    deleteWidth - dx
                }else {
                    - deleteWidth + dx
                }
                mScroller.startScroll(0, 0, left.toInt(),0)
                invalidate()
            }else {
                //如果移动没过半应该恢复状态
                mScroller.startScroll(0, 0, - dx.toInt(),0)
                invalidate()
            }

            //清除状态
            mLastX = 0f
            mItem = null
        }
    }

    //移动item
    //版本一：绝对值小于删除按钮长度随便移动，大于则不移动
    private fun moveItem(e: MotionEvent) {
        mItem?.let {
            val dx = e.x - mLastX
            //这里默认最后一个view是删除按钮
            if (abs(dx) < it.getChildAt(it.childCount - 1).width) {
                //触发移动
                mScroller.startScroll(0, 0, dx.toInt(), 0)
                invalidate()
            }
        }
    }

    //获取点击位置
    //版本一：通过点击的y坐标除于item高度得出
    private fun getSelectItem(e: MotionEvent) {
        val firstChild = getChildAt(0)
        firstChild?.let {
            val pos = (e.x / firstChild.height).toInt()
            mItem = getChildAt(pos) as ViewGroup
        }
    }

    //流畅地滑动
    override fun computeScroll() {
        if (mScroller.computeScrollOffset()) {
            mItem?.scrollBy(mScroller.currX, mScroller.currY)
            postInvalidate()
        }
    }
}
```
注意啊，这里的代码是没法用的，滑动后选不中正确的item，距离也有问题，所以里面有很多问题！

##### kotlin的构造
其实上来最懵逼的就是kotlin的构造函数，自己写了几次，感觉都不对，还是搜了下，有两种写法，我还是觉得使用JvmOverloads的比较方便，不过好像在API版本>21时还有个defStyleRes，我这就不相叙了，可以查资料。

##### 获取的item位置不对
这里获取的item明显不对，其实这个问题很好发现，因为事件的x是屏幕的x啊，这里使用列表去计算明显不行，而且考虑了可见性吗？考虑可滑动隐藏了吗？考虑了第一个item子显示部分吗？
结合上面这些问题，应该如何去正确获取item的位置呢？看下面代码：
```kotlin 
    private fun getSelectItem(e: MotionEvent) {
        val frame = Rect()
        forEach {
            if (it.visibility != GONE) {
                it.getHitRect(frame)
                if (frame.contains(e.x.toInt(), e.y.toInt())) {
                    mItem = it as ViewGroup
                }
            }
        }
    }
```
这里参考了别人的代码，通过遍历子item，检查事件坐标是否在其中，在的话得到选中的item，不再需要position了，还是挺好理解的。

##### 移动的计算不对
上面的代码将mLastX只记录down事件，而每次的是事件和dwon事件横坐标差值，明显错了。
首先mLastX这里应该记录的是每个事件的x，包含move的事件，移动的差值应该是一个小的差值。
```kotlin
MotionEvent.ACTION_MOVE -> {
    //移动控件
    moveItem(e)
    //更新点击的横坐标
    mLastX = e.x
    //拦截事件
    return true
}
```
```kotlin
    private fun moveItem(e: MotionEvent) {
        mItem?.let {
            val dx = mLastX - e.x
            //检查mItem移动后应该在[-deleteLength, 0]内
            val deleteWidth = it.getChildAt(it.childCount - 1).width
            if ((it.scrollX + dx) <= deleteWidth && (it.scrollX + dx) >= 0) {
                //触发移动
                it.scrollBy(dx.toInt(), 0)
            }
        }
    }
```
##### 滑动结束结束判断不对
上面的mLastX修改后，滑动结束结束的判断不对，而且原本就是不对的哈！mScroller的移动就错了，正确的看下面：
```kotlin
    private fun stopMove() {
        mItem?.let {
            //如果移动过半了，应该判定左滑成功
            val deleteWidth = it.getChildAt(it.childCount - 1).width
            if (abs(it.scrollX) >= deleteWidth / 2) {
                //触发移动至完全展开
                mScroller.startScroll(it.scrollX, 0, - deleteWidth,0)
                invalidate()
            }else {
                //如果移动没过半应该恢复状态
                mScroller.startScroll(it.scrollX, 0, 0,0)
                invalidate()
            }

            //清除状态
            mLastX = 0f
            mItem = null
        }
    }
```
### 编写代码II
改完上面代码大致就有了第二版，下面看全部代码，看看还有什么问题啊：
```kotlin
class SlideDeleteRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    //流畅滑动
    private var mScroller = Scroller(context)
    //当前选中item
    private var mItem: ViewGroup? = null
    //上次按下横坐标
    private var mLastX = 0f

    override fun onInterceptTouchEvent(e: MotionEvent?): Boolean {
        e?.let {
            when(e.action) {
               MotionEvent.ACTION_DOWN -> {
                   //获取点击位置
                   getSelectItem(e)
                   //设置点击的横坐标
                   mLastX = e.x
               }
               MotionEvent.ACTION_MOVE -> {
                   //移动控件
                   moveItem(e)
                   //更新点击的横坐标
                   mLastX = e.x
                   //拦截事件
                   return true
               }
               MotionEvent.ACTION_UP -> {
                   //判断结果
                   stopMove()
               }
           }
        }
        return super.onInterceptTouchEvent(e)
    }

    //滑动结束
    //版本一：判断一下结束的位置，补充或恢复位置
    //问题：mLast不应该是down的位置
    //版本二：
    private fun stopMove() {
        mItem?.let {
            //如果移动过半了，应该判定左滑成功
            val deleteWidth = it.getChildAt(it.childCount - 1).width
            if (abs(it.scrollX) >= deleteWidth / 2) {
                //触发移动至完全展开
                mScroller.startScroll(it.scrollX, 0, - deleteWidth,0)
                invalidate()
            }else {
                //如果移动没过半应该恢复状态
                mScroller.startScroll(it.scrollX, 0, 0,0)
                invalidate()
            }

            //清除状态
            mLastX = 0f
            mItem = null
        }
    }

    //移动item
    //版本一：绝对值小于删除按钮长度随便移动，大于则不移动
    //问题：移动方向反了，而且左右可以滑动，没有限定住范围，mLast只是记住down的位置
    //版本二：通过整体移动的数值，和每次更新的数值，判断是否在范围内，再移动
    private fun moveItem(e: MotionEvent) {
        mItem?.let {
            val dx = mLastX - e.x
            //检查mItem移动后应该在[-deleteLength, 0]内
            val deleteWidth = it.getChildAt(it.childCount - 1).width
            if ((it.scrollX + dx) <= deleteWidth && (it.scrollX + dx) >= 0) {
                //触发移动
                it.scrollBy(dx.toInt(), 0)
            }
        }
    }

    //获取点击位置
    //版本一：通过点击的y坐标除于item高度得出
    //问题：没考虑列表项的可见性、列表滑动的情况，并且x和屏幕有关不仅仅是列表
    //版本二：通过遍历子view检查事件在哪个view内，得到点击的item
    private fun getSelectItem(e: MotionEvent) {
        //获得第一个可见的item的position
        val frame = Rect()
        forEach {
            if (it.visibility != GONE) {
                it.getHitRect(frame)
                if (frame.contains(e.x.toInt(), e.y.toInt())) {
                    mItem = it as ViewGroup
                }
            }
        }
    }

    //流畅地滑动
    override fun computeScroll() {
        if (mScroller.computeScrollOffset()) {
            mItem?.scrollBy(mScroller.currX, mScroller.currY)
            postInvalidate()
        }
    }
}
```
代码改完，运行，诶，怎么只能滑动一小下？打断点试一下，选中的item正确了，但是怎么ACTION_MOVE只触发一次？怎么ACTION_UP不触发呢？这里就要注意下ACTION_MOVE里的代码：
```kotlin
MotionEvent.ACTION_MOVE -> {
    //移动控件
    moveItem(e)
    //更新点击的横坐标
    mLastX = e.x
    //拦截事件
    return true
}
```
这里返回了true？拦截事件？那后续的一系列事件不就是被当前view拦截了吗？果然仅仅一个onInterceptTouchEvent是搞不定的啊！
其实这里还有一个隐藏问题，computeScroll里面真的写对了吗？scrollBy和scrollTo有了解吗？
下面看再次改进的代码，主要就是改的上面两点，改动篇幅有点大，就全贴出来了。
### 编写代码III
```kotlin
class SlideDeleteRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    //流畅滑动
    private var mScroller = Scroller(context)
    //当前选中item
    private var mItem: ViewGroup? = null
    //上次按下横坐标
    private var mLastX = 0f

    override fun onInterceptTouchEvent(e: MotionEvent?): Boolean {
        e?.let {
            when(e.action) {
               MotionEvent.ACTION_DOWN -> {
                   //获取点击位置
                   getSelectItem(e)
                   //设置点击的横坐标
                   mLastX = e.x
               }
               MotionEvent.ACTION_MOVE -> {
                   //判断是否拦截
                   return moveItem(e)
               }
//               MotionEvent.ACTION_UP -> {
//                   //判断结果
//                   stopMove()
//               }
           }
        }
        return super.onInterceptTouchEvent(e)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent?): Boolean {
        e?.let {
            when(e.action) {
                //拦截了ACTION_MOVE后，后面一系列event都会交到本view处理
                MotionEvent.ACTION_MOVE -> {
                    //移动控件
                    moveItem(e)
                    //更新点击的横坐标
                    mLastX = e.x
                }
                MotionEvent.ACTION_UP -> {
                    //判断结果
                    stopMove()
                }
            }
        }
        return super.onTouchEvent(e)
    }

    //滑动结束
    //版本一：判断一下结束的位置，补充或恢复位置
    //问题：mLast不应该是down的位置
    //版本二：改进结果判断
    //问题：onInterceptTouchEvent的ACTION_UP不触发
    //版本三：改进补充或恢复位置的逻辑
    private fun stopMove() {
        mItem?.let {
            //如果移动过半了，应该判定左滑成功
            val deleteWidth = it.getChildAt(it.childCount - 1).width
            if (abs(it.scrollX) >= deleteWidth / 2f) {
                //触发移动至完全展开
                mScroller.startScroll(it.scrollX, 0, deleteWidth - it.scrollX,0)
                invalidate()
            }else {
                //如果移动没过半应该恢复状态
                mScroller.startScroll(it.scrollX, 0, -it.scrollX,0)
                invalidate()
            }

            //清除状态
            mLastX = 0f
            //不能为null，后续流畅滑动要用到
            //mItem = null
        }
    }

    //移动item
    //版本一：绝对值小于删除按钮长度随便移动，大于则不移动
    //问题：移动方向反了，而且左右可以滑动，没有限定住范围，mLast只是记住down的位置
    //版本二：通过整体移动的数值，和每次更新的数值，判断是否在范围内，再移动
    //问题：onInterceptTouchEvent的ACTION_MOVE只触发一次
    //版本三：放在onTouchEvent内执行，并且在onInterceptTouchEvent给出一个拦截判断
    private fun moveItem(e: MotionEvent): Boolean {
        mItem?.let {
            val dx = mLastX - e.x
            //检查mItem移动后应该在[-deleteLength, 0]内
            val deleteWidth = it.getChildAt(it.childCount - 1).width
            if ((it.scrollX + dx) <= deleteWidth && (it.scrollX + dx) >= 0) {
                //触发移动
                it.scrollBy(dx.toInt(), 0)
                return true
            }
        }
        return false
    }

    //获取点击位置
    //版本一：通过点击的y坐标除于item高度得出
    //问题：没考虑列表项的可见性、列表滑动的情况，并且x和屏幕有关不仅仅是列表
    //版本二：通过遍历子view检查事件在哪个view内，得到点击的item
    //问题：没有问题，成功拿到了mItem
    private fun getSelectItem(e: MotionEvent) {
        //获得第一个可见的item的position
        val frame = Rect()
        //防止点击其他地方，保持上一个item
        mItem = null
        forEach {
            if (it.visibility != GONE) {
                it.getHitRect(frame)
                if (frame.contains(e.x.toInt(), e.y.toInt())) {
                    mItem = it as ViewGroup
                }
            }
        }
    }

    //流畅地滑动
    override fun computeScroll() {
        if (mScroller.computeScrollOffset()) {
            mItem?.scrollTo(mScroller.currX, mScroller.currY)
            postInvalidate()
        }
    }
}
```
把上面代码运行下，果然就十分完美了，可是是不是觉得没彻底搞定啊？别急下面我们再加点东西.
### 优化
##### 优化一：TouchSlop
TouchSlop是一个移动的最小距离，由系统提供，可以用它来判断一个滑动距离是否有效。
##### 优化二：VelocityTracker
VelocityTracker是一个速度计算的工具，由native提供，可以计算移动像素点的速度，我们可以利用它判断当滑动速度很快时也展开删除按钮。
##### 优化三：GestureDetector
GestureDetector是手势控制类，可以很方便的判断各种手势，我们这可以设计它双击展开删除按钮。
##### 优化后代码
```kotlin
class SlideDeleteRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    //系统最小移动距离
    private val mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop

    //最小有效速度
    private val mMinVelocity = 600

    //增加手势控制，双击快速完成侧滑，还是为了练习
    private var isDoubleClick = false
    private var mGestureDetector: GestureDetector
        = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener(){
            override fun onDoubleTap(e: MotionEvent?): Boolean {
                e?.let { event->
                    getSelectItem(event)
                    mItem?.let {
                        val deleteWidth = it.getChildAt(it.childCount - 1).width
                        //触发移动至完全展开deleteWidth
                        if (it.scrollX == 0) {
                            mScroller.startScroll(0, 0, deleteWidth, 0)
                        }else {
                            mScroller.startScroll(it.scrollX, 0, -it.scrollX, 0)
                        }
                        isDoubleClick = true
                        invalidate()
                        return true
                    }
                }

                //不进行拦截，只是作为工具判断下双击
                return false
            }
        })

    //使用速度控制器，增加侧滑速度判定滑动成功，主要为了是练习
    //VelocityTracker 由 native 实现，需要及时释放内存
    private var mVelocityTracker: VelocityTracker? = null

    //流畅滑动
    private var mScroller = Scroller(context)

    //当前选中item
    private var mItem: ViewGroup? = null

    //上次事件的横坐标
    private var mLastX = 0f

    //当前RecyclerView被上层viewGroup分发到事件，所有事件都会通过dispatchTouchEvent给到
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        //
        mGestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    //viewGroup对子控件的事件拦截，一旦拦截，后续事件序列不会再调用onInterceptTouchEvent
    override fun onInterceptTouchEvent(e: MotionEvent?): Boolean {
        e?.let {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    //这里的优化会阻止双击滑动的使用，实际也没什么好优化的
//                    //防止快速按下情况出问题
//                    if (!mScroller.isFinished) {
//                        mScroller.abortAnimation()
//                    }

                    //获取点击位置
                    getSelectItem(e)
                    //设置点击的横坐标
                    mLastX = e.x
                }
                MotionEvent.ACTION_MOVE -> {
                    //判断是否拦截
                    //如果拦截了ACTION_MOVE，后续事件就不触发onInterceptTouchEvent了
                    return moveItem(e)
                }
                //拦截了ACTION_MOVE，ACTION_UP也不会触发
//                MotionEvent.ACTION_UP -> {
//                    //判断结果
//                    stopMove()
//                }
            }
        }
        return super.onInterceptTouchEvent(e)
    }

    //拦截后对事件的处理，或者子控件不处理，返回到父控件处理，在onTouch之后，在onClick之前
    //如果不消耗，则在同一事件序列中，当前View无法再次接受事件
    //performClick会被onTouchEvent拦截，我们这不需要点击，全都交给super实现去了
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent?): Boolean {
        e?.let {
            when (e.action) {
                //没有拦截，也不能拦截，所以不需要处理
//                MotionEvent.ACTION_DOWN -> {}
                //拦截了ACTION_MOVE后，后面一系列event都会交到本view处理
                MotionEvent.ACTION_MOVE -> {
                    //移动控件
                    moveItem(e)
                    //更新点击的横坐标
                    mLastX = e.x
                }
                MotionEvent.ACTION_UP -> {
                    //判断结果
                    stopMove()
                }
            }
        }
        return super.onTouchEvent(e)
    }

    //滑动结束
    //版本一：判断一下结束的位置，补充或恢复位置
    //问题：mLast不应该是down的位置
    //版本二：改进结果判断
    //问题：onInterceptTouchEvent的ACTION_UP不触发
    //版本三：改进补充或恢复位置的逻辑
    private fun stopMove() {
        mItem?.let {
            //如果移动过半了，应该判定左滑成功
            val deleteWidth = it.getChildAt(it.childCount - 1).width
            //如果整个移动过程速度大于600，也判定滑动成功
            //注意如果没有拦截ACTION_MOVE，mVelocityTracker是没有初始化的
            var velocity = 0f
            mVelocityTracker?.let { tracker ->
                tracker.computeCurrentVelocity(1000)
                velocity = tracker.xVelocity
            }
            //判断结束情况,移动过半或者向左速度很快都展开
            if ( (abs(it.scrollX) >= deleteWidth / 2f) || (velocity < - mMinVelocity) ) {
                //触发移动至完全展开
                mScroller.startScroll(it.scrollX, 0, deleteWidth - it.scrollX, 0)
                invalidate()
            }else {
                //如果移动没过半应该恢复状态，或者向右移动很快则恢复到原来状态
                mScroller.startScroll(it.scrollX, 0, -it.scrollX, 0)
                invalidate()
            }

            //清除状态
            mLastX = 0f
            //不能为null，后续mScroller要用到
            //mItem = null
            //mVelocityTracker由native实现，需要及时释放
            mVelocityTracker?.apply {
                clear()
                recycle()
            }
            mVelocityTracker = null
        }
    }

    //移动item
    //版本一：绝对值小于删除按钮长度随便移动，大于则不移动
    //问题：移动方向反了，而且左右可以滑动，没有限定住范围，mLast只是记住down的位置
    //版本二：通过整体移动的数值，和每次更新的数值，判断是否在范围内，再移动
    //问题：onInterceptTouchEvent的ACTION_MOVE只触发一次
    //版本三：放在onTouchEvent内执行，并且在onInterceptTouchEvent给出一个拦截判断
    @SuppressLint("Recycle")
    private fun moveItem(e: MotionEvent): Boolean {
        mItem?.let {
            val dx = mLastX - e.x
            //最小的移动距离应该舍弃，onInterceptTouchEvent不拦截，onTouchEvent内才更新mLastX
            if(abs(dx) > mTouchSlop) {
                //检查mItem移动后应该在[-deleteLength, 0]内
                val deleteWidth = it.getChildAt(it.childCount - 1).width
                if ((it.scrollX + dx) <= deleteWidth && (it.scrollX + dx) >= 0) {
                    //触发移动
                    it.scrollBy(dx.toInt(), 0)
                    //触发速度计算
                    //这里Recycle不存在问题，一旦返回true，就会拦截事件，就会到达ACTION_UP去回收
                    mVelocityTracker = mVelocityTracker ?: VelocityTracker.obtain()
                    mVelocityTracker!!.addMovement(e)
                    return true
                }
            }
        }
        return false
    }

    //获取点击位置
    //版本一：通过点击的y坐标除于item高度得出
    //问题：没考虑列表项的可见性、列表滑动的情况，并且x和屏幕有关不仅仅是列表
    //版本二：通过遍历子view检查事件在哪个view内，得到点击的item
    //问题：没有问题，成功拿到了mItem
    private fun getSelectItem(e: MotionEvent) {
        //获得第一个可见的item的position
        val frame = Rect()
        //防止点击其他地方，保持上一个item
        mItem = null
        forEach {
            if (it.visibility != GONE) {
                it.getHitRect(frame)
                if (frame.contains(e.x.toInt(), e.y.toInt())) {
                    mItem = it as ViewGroup
                }
            }
        }
    }

    //流畅地滑动
    override fun computeScroll() {
        if (mScroller.computeScrollOffset()) {
            mItem?.scrollTo(mScroller.currX, mScroller.currY)
            postInvalidate()
        }
    }
}
```
TouchSlop、VelocityTracker和GestureDetector的用法都很简单，但是有一点必须得说一下，那就是在dispatchTouchEvent中传递事件给GestureDetector，为什么呢？因为onInterceptTouchEvent拦截后就搜不到事件了，onTouchEvent的执行和自身及子控件有关，有不确定性，只有dispatchTouchEvent中的事件一定会收到！


### 总结
一篇文章下来，代码贴的有点多了，篇幅很长，但是如果仔细品的话，你会发现从事件分发到拦截都从问题里面学到了，几种滑动方式以及滑动的相对性也涉及了，坐标系也有了一定理解，其他几个工具TouchSlop、VelocityTracker和GestureDetector都用到了，还算可以吧！