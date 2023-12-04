# Android View动画实践
## 前言
前面写了下Drawable的实践，这篇文章来写下View动画的实践，不过现在谁还用View动画啊，这里就随便写下吧，最近也忙了些，没太多时间更新博客了。

## 一般使用
View动画只有四种基本变化，比较简单，注意下一些属性的使用就行:
> res/anim/anim_animation_set.xml
```
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="1500"
    android:fillBefore="true"
    android:fillAfter="true"
    android:repeatMode="reverse"
    android:startOffset="100"
    android:shareInterpolator="true">
    <!--  fillBefore，fillAfter，动画结束后保持开始或结束状态  -->

    <scale
        android:fromXScale="0.25"
        android:fromYScale="0.25"
        android:toXScale="1"
        android:toYScale="1"
        android:pivotX="50%"
        android:pivotY="50%"
        />

    <rotate
        android:fromDegrees="0"
        android:toDegrees="360"
        android:pivotX="50%"
        android:pivotY="50%"
        />

    <translate
        android:fromXDelta="0"
        android:fromYDelta="0"
        android:toXDelta="50%p"
        android:toYDelta="0"
        />

    <alpha
        android:fromAlpha="0.25"
        android:toAlpha="1"
        />

</set>
```
layout使用:
```
<TextView
    android:id="@+id/viewAnimation"
    android:text="ViewAnimation(click)"
    android:background="@drawable/ic_launcher"
    android:layout_width="wrap_content"
    android:layout_height="80dp"
    android:layout_margin="5dp"
    android:clickable="true"
    android:focusable="true"
    tools:ignore="HardcodedText"
    />
```
代码使用:
```
// View动画
val animation = AnimationUtils.loadAnimation(requireContext(), R.anim.anim_animation_set)
binding.viewAnimation.startAnimation(animation)
binding.viewAnimation.setOnClickListener {
    showToast("perform click!")
}
```
实际效果:
![ezgif-3-2f15a04b59.gif](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/67e2592083d249ad99c06010de07783e~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=320&h=676&s=96881&e=gif&f=70&b=fffdff)

这里要注意下动画改变view位置后，点击的地方是不会变的，和animator不一样。

## 自定义View动画
本来不想给view动画写一篇文章的，只是看到书上，可以通过Camera搞出3D效果，直接写了一个，所以还是来写了一篇文章。

我这自定义了一个Custom3dIkunAnimation，它有四组变化，唱跳rap篮球，呃，说错了，是X轴平移、Y轴平移、Z轴平移及旋转，比较简单，对于熟悉自定义view动画、Camera、Matrix也算够了，下面看下代码:
```
import android.content.Context
import android.graphics.Camera
import android.util.AttributeSet
import android.view.animation.Animation
import android.view.animation.Transformation

class Custom3dIkunAnimation @JvmOverloads constructor(
    val context: Context,
    attrs: AttributeSet? = null
) : Animation(context, attrs) {

    // 背景属性
    private var mWidth: Int = 0
    private var mHeight: Int = 0
    private var mDensity: Float = 1f
    private lateinit var mCamera: Camera

    override fun initialize(width: Int, height: Int, parentWidth: Int, parentHeight: Int) {
        super.initialize(width, height, parentWidth, parentHeight)
        mWidth = width
        mHeight = height
        mCamera = Camera()
        mDensity = context.resources.displayMetrics.density
    }

    override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
        super.applyTransformation(interpolatedTime, t)

        // 图形矩阵
        val matrix = t.matrix

        // 唱
        val toZ = -100 + 200 * interpolatedTime
        // 跳
        val toY = -50 + 100 * interpolatedTime
        // rip
        val toX = -100 + 200 * interpolatedTime
        // 篮球
        val toDegree = 0 + 360f * interpolatedTime

        // 保存状态
        mCamera.save()

        // 执行操作，只支持平移和旋转
        mCamera.translate(toX, toY, toZ)
        mCamera.rotateY(toDegree)
        // 取得变换后的矩阵
        mCamera.getMatrix(matrix)

        // 恢复camera状态
        mCamera.restore()

        // 修复旋转时弹出屏幕问题
        val mValues = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        matrix.getValues(mValues)
        mValues[6] = mValues[6] / mDensity
        matrix.setValues(mValues)

        // 这两行代码的目的是在旋转动画中将矩阵的原点（坐标系的原点）移动到旋转轴心点的位置，
        // 然后在旋转完成后将原点移回到原来的位置，以确保旋转动画的正确性。(缺一不可，前面改变轴点，后者稳定位置)
        //
        // pre是把数据矩阵放前面和变化矩阵相乘，preTranslate方法的效果是在应用其他变换之前改变坐标系的原点位置。
        matrix.preTranslate(-mWidth/2f, -mHeight/2f)
        // post是把数据矩阵放后面和变化矩阵相乘，postTranslate方法的效果是在应用其他变换之后改变坐标系的原点位置。
        matrix.postTranslate(mWidth/2f, mHeight/2f)
    }
}
```
layout代码:
```
<TextView
    android:id="@+id/custom3dIkunAnimation"
    android:text="Custom3dIkunAnimation(click)"
    android:background="@drawable/ic_launcher"
    android:layout_width="wrap_content"
    android:layout_height="80dp"
    android:layout_gravity="center"
    android:layout_margin="5dp"
    android:clickable="true"
    android:focusable="true"
    tools:ignore="HardcodedText"
    />
```
代码使用:
```
// View动画
val ikunAnimation = Custom3dIkunAnimation(requireContext())
ikunAnimation.duration = 10000
ikunAnimation.repeatCount = 1
ikunAnimation.repeatMode = Animation.REVERSE
var isPlaying = false
ikunAnimation.setAnimationListener(object : Animation.AnimationListener {
    override fun onAnimationStart(animation: Animation?) {
        isPlaying = true
    }

    override fun onAnimationEnd(animation: Animation?) {
        isPlaying = false
    }

    override fun onAnimationRepeat(animation: Animation?) { }
})
binding.custom3dIkunAnimation.startAnimation(ikunAnimation)
binding.custom3dIkunAnimation.setOnClickListener {
    if (!isPlaying) {
        binding.custom3dIkunAnimation.startAnimation(ikunAnimation)
    }
}
```
实际效果:
![ezgif-3-54650679da.gif](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/14b4ce46d70d4f2b81e7cf93de1fc5ae~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=320&h=676&s=320677&e=gif&f=45&b=fffdff)

看起来还是挺炫酷的，就是Z轴平移看不出来好像。

## 帧动画
上一篇文章讲了drawable，我还说animation-list慢慢讲呢，后面发现这家伙就是帧动画啊，醉了醉了，哪上一篇的颜色渐变改了下:
> res/drawable/ic_animation_drawable.xml
```
<?xml version="1.0" encoding="utf-8"?>
<animation-list xmlns:android="http://schemas.android.com/apk/res/android"
    android:visible="true"
    android:oneshot="true"
    >
    <item android:duration="200">
        <shape><solid android:color="#000000FF"/><stroke android:color="@color/green"/></shape>
    </item>
    <item android:duration="200">
        <shape><solid android:color="#220000FF"/> <stroke android:color="@color/green"/></shape>
    </item>
    <item android:duration="200">
        <shape><solid android:color="#440000FF"/> <stroke android:color="@color/green"/></shape>
    </item>
    <item android:duration="200">
        <shape><solid android:color="#660000FF"/> <stroke android:color="@color/green"/></shape>
    </item>
    <item android:duration="200">
        <shape><solid android:color="#880000FF"/> <stroke android:color="@color/green"/></shape>
    </item>
    <item android:duration="200">
        <shape><solid android:color="#AA0000FF"/> <stroke android:color="@color/green"/></shape>
    </item>
    <item android:duration="200">
        <shape><solid android:color="#CC0000FF"/> <stroke android:color="@color/green"/></shape>
    </item>
    <item android:duration="200">
        <shape><solid android:color="#EE0000FF"/> <stroke android:color="@color/green"/></shape>
    </item>
    <item android:duration="200">
        <shape><solid android:color="#FF0000FF"/> <stroke android:color="@color/green"/></shape>
    </item>
</animation-list>
```
layout代码:
```
<TextView
    android:id="@+id/frameAnimation"
    android:text="FrameAnimation(click)"
    android:background="@drawable/ic_animation_drawable"
    android:layout_width="wrap_content"
    android:layout_height="80dp"
    android:layout_margin="5dp"
    android:clickable="true"
    android:focusable="true"
    tools:ignore="HardcodedText"
    />
```
代码实现:
```
// 帧动画
val frameAnimation = binding.frameAnimation.background as AnimationDrawable
frameAnimation.isOneShot = true
binding.frameAnimation.setOnClickListener {
    frameAnimation.start()
}
```
实际效果:
![SVID_20231204_152825_1.gif](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/fea922ae5ef541c794e6ae71c494a146~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=163946&e=gif&f=74&b=fffdff)

比较简单，不多说了。

## LayoutAnimation
比较有意思的是ViewGroup都能实现一个layoutAnimation，可以在XML中实现，也可以在代码里面设置，效果不错:
> res/anim/anim_layout.xml
```
<?xml version="1.0" encoding="utf-8"?>
<layoutAnimation xmlns:android="http://schemas.android.com/apk/res/android"
    android:delay="15%"
    android:animationOrder="normal"
    android:animation="@anim/anim_layout_item"
    />
<!--  单个元素的延迟时间，delay时间全部是300ms，o.5就是150ms，第一个150ms，第二个300ms  -->
```
layout实现，这里用CardView配合View实现了几个圆来作为child:
```
<LinearLayout
    android:id="@+id/layoutAnimation"
    android:background="@color/gray"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:orientation="horizontal"
    android:gravity="center"
    android:layoutAnimation="@anim/anim_layout"
    >

    <androidx.cardview.widget.CardView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        app:cardCornerRadius="25dp"
        android:layout_margin="5dp">
        <View
            android:layout_width="50dp"
            android:layout_height="50dp"
            android:background="@color/red"
            />
    </androidx.cardview.widget.CardView>

    <androidx.cardview.widget.CardView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        app:cardCornerRadius="25dp"
        android:layout_margin="5dp">
        <View
            android:layout_width="50dp"
            android:layout_height="50dp"
            android:background="@color/green"
            />
    </androidx.cardview.widget.CardView>

    <androidx.cardview.widget.CardView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        app:cardCornerRadius="25dp"
        android:layout_margin="5dp">
        <View
            android:layout_width="50dp"
            android:layout_height="50dp"
            android:background="@color/yellow"
            />
    </androidx.cardview.widget.CardView>

    <androidx.cardview.widget.CardView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        app:cardCornerRadius="25dp"
        android:layout_margin="5dp">
        <View
            android:layout_width="50dp"
            android:layout_height="50dp"
            android:background="@color/purple"
            />
    </androidx.cardview.widget.CardView>

</LinearLayout>
```
代码实现:
```
// LayoutAnimation
val layoutAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.anim_layout_item)
val control = LayoutAnimationController(layoutAnimation)
control.delay = 0.15f
control.order = LayoutAnimationController.ORDER_NORMAL
binding.layoutAnimation.layoutAnimation = control
// 点击刷新动画
binding.layoutAnimation.setOnClickListener {
    // 注意每次播放要加上标记，配合invalidate才有动画
    binding.layoutAnimation.scheduleLayoutAnimation()
    binding.layoutAnimation.postInvalidate()
}
// 长按删除第一个
binding.layoutAnimation.setOnLongClickListener {
    if (binding.layoutAnimation.childCount > 0) {
        binding.layoutAnimation.scheduleLayoutAnimation()
        binding.layoutAnimation.removeView(binding.layoutAnimation.getChildAt(0))
        return@setOnLongClickListener true
    }
    return@setOnLongClickListener false
}
```
实际效果:
![SVID_20231204_153021_1.gif](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/0ceff56bf92844019accb7020e6563ed~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=299032&e=gif&f=91&b=fffdff)

看起来操作比较麻烦，实际上，要触发一次只需要把FLAG_RUN_ANIMATION加上。

## overridePendingTransition
overridePendingTransition可以给activity加上进入和退出动画，估计基本都用过，虽然现在Android14源码已经把它废弃了，换成overrideActivityTransition了，不过就差不多，加了个参数。

下面简单实践下:
> res/anim/anim_enter_activity.xml、anim_exit_activity.xml
```
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="1000"
    >
    <translate
        android:fromXDelta="100%p"
        android:toXDelta="0%p"
        android:interpolator="@android:anim/decelerate_interpolator"
        />
</set>
```
这里加了个默认的interpolator，这个后面animator再讲，不过可以点进这个目录去看看，里面还有很多自带的interpolator。
```
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="1000"
    >
    <translate
        android:fromYDelta="0%p"
        android:toYDelta="100%p"
        android:interpolator="@android:anim/accelerate_interpolator"
        />
    <!--  @android:anim中包含很多animation动画，及几个插值器  -->
</set>
```
这里使用百分号p还是挺舒服的，注意下正负号和坐标的关系。

代码实现:
```
// Activity动画
binding.activityAnimation.setOnClickListener {
    startActivity(AnimationActivity::class.java)
    requireActivity().overridePendingTransition(R.anim.anim_enter_activity, R.anim.anim_exit_activity)
}

binding.button.setOnClickListener {
    finish()
    overridePendingTransition(R.anim.anim_enter_activity, R.anim.anim_exit_activity)
}
```
实际效果:
![SVID_20231204_153100_1.gif](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/16ed9e54652b40f08ce424fb68bdf2fc~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=393753&e=gif&f=60&b=fffdff)

## 小结
View动画还是很经典的，配合平移、旋转、缩放、渐变的组合可以实现很炫酷的效果，而且实现起来非常简单。