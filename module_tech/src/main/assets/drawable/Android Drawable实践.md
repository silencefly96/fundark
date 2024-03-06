# Android Drawable实践
## 前言
最近看源码、做事情有点累了，抽时间看了看书，发现很多东西只是看过，却没有去实践，看了基本也就忘了，有些内容真好没那么复杂，就花了点零散时间试试。

这篇文章是Android中Drawable的实践内容，从《Android开发艺术探索》的第六章入手的，这里简单记录下。

## BitmapDrawable
BitmapDrawable实际就是用来放图片的，下面是例子:
```xml
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/ic_launcher"
    android:antialias="true"
    android:dither="true"
    android:filter="true"
    android:gravity="fill_vertical|left"
    android:mipMap="false"
    android:tileMode="disabled"
     />
<!--
    antialias 抗锯齿
    dither 抖动效果，防止因像素适配问题导致的失真
    filter 过滤效果，防止拉伸或压缩时影响显示效果
    gravity 当图片尺寸小于容器尺寸时，对图片的定位效果
    mipMap 纹理映射，防止远处图片失真
    tileMode 平铺模式，和gravity冲突，有平铺repeat、镜像mirror、两边扩展
-->
```
用法也简单，给view的background设置上就行:
```
<TextView
    android:id="@+id/bitmapDrawable"
    android:text="BitmapDrawable"
    android:background="@drawable/ic_drawable_bitmap"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:layout_margin="5dp"
    tools:ignore="HardcodedText"
    />
```
显示效果:
![image.png](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/0e73ee4ff1c24029bd5f999fa9a1979e~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=533&h=148&s=34742&e=png&b=fbfbfb)

## NinePatchDrawable
NinePatchDrawable和BitmapDrawable类似，需要注意的是Android Studio中只有png才能转成“.9”格式图片，NinePatchDrawable里面也不能访问mipmap的图片，需要复制到drawable里面去:
```xml
<nine-patch xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/ic_nine_patch"
    android:dither="true"
     />
<!--
    antialias 抗锯齿
    dither 抖动效果，防止因像素适配问题导致的失真
    filter 过滤效果，防止拉伸或压缩时影响显示效果
    gravity 当图片尺寸小于容器尺寸时，对图片的定位效果
    mipMap 纹理映射，防止远处图片失真
    tileMode 平铺模式，和gravity冲突，有平铺repeat、镜像mirror、两边扩展
-->
```
用法类似:
```
<TextView
    android:id="@+id/ninePatchDrawable"
    android:text="NinePatchDrawable"
    android:background="@drawable/ic_drawable_nine_patch"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:layout_margin="5dp"
    tools:ignore="HardcodedText"
    />
```
配置:
![image.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/16f253b49fc0475086f9fa0f2fa2fc90~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=634&h=624&s=82039&e=png&b=2b2d2e)

显示效果:
![image.png](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/7bf66cff8bf74e8686ce8ed95bca9957~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=545&h=136&s=47655&e=png&b=fefefe)

拉伸的很奇怪，不过没问题hh

## ShapeDrawable
ShapeDrawable用的比较多吧，各种背景基本是这个，不多说:
```
<shape android:shape="rectangle"
    xmlns:android="http://schemas.android.com/apk/res/android" >

    <!--  线  -->
    <stroke
        android:width="3dp"
        android:color="@color/blue"
        android:dashGap="1dp"
        android:dashWidth="2dp"/>

    <!--  实心  -->
    <solid
        android:color="@color/gray"/>

    <!--  圆角  -->
    <corners
        android:topLeftRadius="5dp"
        android:topRightRadius="10dp"
        android:bottomRightRadius="15dp"
        android:bottomLeftRadius="20dp"/>

    <!--  渐变(和solid冲突)  -->
    <gradient
        android:type="sweep"
        android:centerY="50%"
        android:centerX="25%"
        android:startColor="@color/red"
        android:centerColor="@color/yellow"
        android:endColor="@color/purple"
        />

    <padding
        android:top="0dp"
        android:left="30dp"
        android:right="30dp"
        android:bottom="20dp"/>

    <size
        android:width="300dp"
        android:height="100dp"
        />
</shape>
```
使用:
```
<TextView
    android:id="@+id/shapeDrawable"
    android:text="ShapeDrawable"
    android:background="@drawable/ic_drawable_shape"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:layout_margin="5dp"
    tools:ignore="HardcodedText"
    />
```
实际效果:
![image.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/d1d6ee09bea242dcaf786dabafd0578c~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=537&h=128&s=34205&e=png&b=ff6105)

shape的几种类型(rectangle、oval、ring)和gradient的几种类型注意下，渐变中心用百分比，其他问题不大。

## LayerDrawable
LayerDrawable就是放多层item，模拟阴影还挺好用的:
```
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">

    <!--  模仿输入框  -->

    <!--  全灰  -->
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/gray"/>
        </shape>
    </item>

    <!--  下面留一条灰线，其他全白  -->
    <item android:bottom="30dp" >
        <shape android:shape="rectangle">
            <solid android:color="@color/white"/>
        </shape>
    </item>

    <!--  左右留一点，嵌入底部灰线，中间全白  -->
    <item android:bottom="15dp" android:left="15dp" android:right="15dp">
        <shape android:shape="rectangle">
            <solid android:color="@color/white"/>
        </shape>
    </item>
</layer-list>
```
使用:
```
<TextView
    android:id="@+id/layerDrawable"
    android:text="LayerDrawable"
    android:background="@drawable/ic_drawable_layer"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:layout_margin="5dp"
    tools:ignore="HardcodedText"
    />
```
效果:
![image.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/a3fbd30f19874526bd10aa02b0665792~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=536&h=130&s=5201&e=png&b=fefefe)

## StateListDrawable
StateListDrawable就是selector标签，用的也挺多吧:
```
<selector xmlns:android="http://schemas.android.com/apk/res/android"
    android:constantSize="false"
    android:dither="true"
    android:variablePadding="false">
    <!--  constantSize，状态变化，size保持不变(最大item尺寸)  -->
    <!--  constantSize，状态变化，padding跟随变化，否则用最大padding  -->

    <!--  各种状态，pressed、checked、focused、selected、enabled  -->
    <item android:state_pressed="true">
        <shape>
            <solid android:color="@color/gray"/>
        </shape>
    </item>

    <item android:state_focused="true">
        <shape>
            <stroke android:width="3dp" android:color="@color/blue"/>
        </shape>
    </item>

    <!--  一般状态放最后  -->
    <item>
        <shape>
            <stroke android:width="3dp" android:color="@color/gray"/>
        </shape>
    </item>

</selector>
```
使用，这里要注意下TextView不处理点击事件:
```
<TextView
    android:id="@+id/stateListDrawable"
    android:text="StateListDrawable(press)"
    android:background="@drawable/ic_drawable_state_list"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:layout_margin="5dp"
    android:clickable="true"
    android:focusable="true"
    tools:ignore="HardcodedText"
    />
```
实际效果:
![SVID_20231120_193257_1.gif](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/b5a74149f95a4a4e80c98e3c3cac3eb5~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=152878&e=gif&f=55&b=fefcfe)

## LevelListDrawable
LevelListDrawable可以设置level，设置level后里面会变化:
```
<level-list xmlns:android="http://schemas.android.com/apk/res/android" >
    <item android:minLevel="0" android:maxLevel="0">
        <shape>
            <solid android:color="@color/gray"/>
        </shape>
    </item>
    <item android:minLevel="1" android:maxLevel="2">
        <shape>
            <solid android:color="@color/yellow"/>
        </shape>
    </item>
    <item android:minLevel="2" android:maxLevel="2">
        <shape>
            <solid android:color="@color/purple"/>
        </shape>
    </item>
    <item android:minLevel="3" android:maxLevel="3">
        <shape>
            <solid android:color="@color/red"/>
        </shape>
    </item>
</level-list>
```
使用:
```
<TextView
    android:id="@+id/levelListDrawable"
    android:text="LevelListDrawable: current level = 0"
    android:background="@drawable/ic_drawable_level_list"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:layout_margin="5dp"
    tools:ignore="HardcodedText"
    />
```
代码:
```
// 切换level
binding.levelListDrawable.setOnClickListener {
    var level = binding.levelListDrawable.background.level
    level = ++level % 5
    binding.levelListDrawable.background.level = level
    binding.levelListDrawable.text = "LevelListDrawable: current level = $level"
}
```
效果:
![SVID_20231120_193316_1.gif](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/11528b18e2ce49c5a06801b735be15ea~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=247185&e=gif&f=93&b=fdfbfd)

## TransitionDrawable
TransitionDrawable有个渐变效果:
```
<transition xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:top="50dp"
        android:bottom="50dp"
        android:left="100dp"
        android:right="100dp"
        >
        <shape>
            <stroke android:width="3dp" android:color="@color/gray"/>
        </shape>
    </item>
    <item>
        <shape>
            <solid android:color="@color/gray"/>
        </shape>
    </item>
</transition>
```
使用:
```
<TextView
    android:id="@+id/transitionDrawable"
    android:text="TransitionDrawable(click)"
    android:background="@drawable/ic_drawable_transition"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:layout_margin="5dp"
    tools:ignore="HardcodedText"
    />
```
代码:
```
// 启动transition
binding.transitionDrawable.setOnClickListener {
    (binding.transitionDrawable.background as TransitionDrawable).startTransition(1500)
}
```
实际效果:
![SVID_20231120_193334_1.gif](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/81f928c6f2a04cf5aa234d033b6f88f3~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=225855&e=gif&f=49&b=fefcfe)

## InsertDrawable
InsertDrawable就是能包含其他drawable，提供一个padding效果:
```
<inset xmlns:android="http://schemas.android.com/apk/res/android"
    android:drawable="@drawable/ic_launcher"
    android:insetTop="20dp"
    android:insetBottom="40dp"
    android:insetLeft="10dp"
    android:insetRight="30dp">

    <shape>
        <stroke android:width="3dp" android:color="@color/gray"/>
    </shape>
</inset>
```
使用:
```
<TextView
    android:id="@+id/insertDrawable"
    android:text="InsertDrawable"
    android:background="@drawable/ic_drawable_insert"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:layout_margin="5dp"
    tools:ignore="HardcodedText"
    />
```
实际效果:
![image.png](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/143894a7fdeb4e9397423105deaf5a6f~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=536&h=138&s=4990&e=png&b=fefefe)

## ScaleDrawable
ScaleDrawable就是会根据level缩放的drawable:
```
<scale xmlns:android="http://schemas.android.com/apk/res/android"
    android:drawable="@drawable/ic_launcher"
    android:scaleGravity="center"
    android:scaleWidth="75%"
    android:scaleHeight="75%"
    >

    <shape>
        <solid android:color="@color/gray"/>
    </shape>

</scale>
```
使用:
```
<TextView
    android:id="@+id/scaleDrawable"
    android:text="ScaleDrawable: current level = 0"
    android:background="@drawable/ic_drawable_scale"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:layout_margin="5dp"
    tools:ignore="HardcodedText"
    />
```
代码:
```
// 设置缩放
binding.scaleDrawable.setOnClickListener {
    var level = binding.scaleDrawable.background.level
    level = (level + 1000) % 10000
    binding.scaleDrawable.background.level = level
    binding.scaleDrawable.text = "ScaleDrawable: current level = $level"
}
```
实际效果:
![SVID_20231120_193350_1.gif](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/0449f11a740c4f33a82684ba600c7dd9~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=226611&e=gif&f=74&b=fffdff)

## ClipDrawable
ClipDrawable会根据level裁切:
```
<clip xmlns:android="http://schemas.android.com/apk/res/android"
    android:clipOrientation="vertical"
    android:gravity="center"
    android:drawable="@drawable/ic_launcher"
    >
</clip>
```
使用:
```
<TextView
    android:id="@+id/clipDrawable"
    android:text="ClipDrawable: current level = 0"
    android:background="@drawable/ic_drawable_clip"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:layout_margin="5dp"
    tools:ignore="HardcodedText"
    />
```
代码:
```
// 设置裁切
binding.clipDrawable.setOnClickListener {
    var level = binding.clipDrawable.background.level
    level = (level + 1000) % 10000
    binding.clipDrawable.background.level = level
    binding.clipDrawable.text = "ClipDrawable: current level = $level"
}
```
实际效果:
![SVID_20231120_193405_1.gif](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/e1e925869ed94f6aa24c5af2d0f82c62~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=304945&e=gif&f=87&b=fffdff)

## CustomDrawable
这里我自己写了一个Drawable，凑合看下吧:
```

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class CustomStarDrawable: Drawable() {

    private val mPaint: Paint = Paint()

    init {
        mPaint.strokeWidth = 5f
        mPaint.flags = Paint.ANTI_ALIAS_FLAG
        mPaint.style = Paint.Style.FILL
        mPaint.color = Color.GRAY
    }

    override fun draw(canvas: Canvas) {
        // 绘制圆圈
        val num = level / 2000
        val radius = (bounds.bottom - bounds.top) / 4f
        val distance = (bounds.right - bounds.left) / 5f

        when(num) {
            1 -> mPaint.color = Color.RED
            2 -> mPaint.color = Color.YELLOW
            3 -> mPaint.color = Color.BLUE
            4 -> mPaint.color = Color.GREEN
            else -> mPaint.color = Color.GRAY
        }
        // canvas.drawRect(bounds, mPaint)
        for (i in 0 until  num) {
            canvas.drawCircle(distance * i + radius,  2 * radius, radius, mPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        mPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        mPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java",
        ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat")
    )
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }


    // 默认大小
    override fun getIntrinsicWidth(): Int {
        return 500
    }

    override fun getIntrinsicHeight(): Int {
        return 100
    }
}
```
使用:
```
<TextView
    android:id="@+id/customDrawable"
    android:text="CustomDrawable: current level = 0"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:layout_margin="5dp"
    tools:ignore="HardcodedText"
    />
```
代码:
```
// 设置自定义drawable
binding.customDrawable.background = CustomStarDrawable()
binding.customDrawable.setOnClickListener {
    var level = binding.customDrawable.background.level
    level = (level + 2000) % 10000
    binding.customDrawable.background.level = level
    binding.customDrawable.text = "CustomDrawable: current level = $level"
}
```
实际效果:
![SVID_20231120_193424_1.gif](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/7cda25e9f8dc42f38c1b22005771899d~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=206999&e=gif&f=65&b=fffdff)

## RippleDrawable
RippleDrawable水波纹效果，很多点击会用到(注意V21):
```
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="@color/blue"
    android:radius="100dp"
     />
<!-- color，必须，涟漪的颜色 -->
<!-- radius，涟漪最大的半径 -->
```
使用:
```
<TextView
    android:id="@+id/rippleDrawable"
    android:text="RippleDrawable(click)"
    android:background="@drawable/ic_drawable_ripple"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:layout_margin="5dp"
    android:clickable="true"
    android:focusable="true"
    tools:ignore="HardcodedText"
    />
```
效果:
![SVID_20231120_193625_1.gif](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/ac0de1f8af30453da2b5422209103f02~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=194541&e=gif&f=38&b=fffdff)

## RotateDrawable
RotateDrawable旋转的，没什么好说的，也和level有关:
```
<rotate xmlns:android="http://schemas.android.com/apk/res/android"
    android:drawable="@drawable/ic_launcher"
    android:pivotX="50%"
    android:pivotY="50%"
    android:fromDegrees="0"
    android:toDegrees="180"
    android:visible="true"
    />
```
使用:
```
<TextView
    android:id="@+id/rotateDrawable"
    android:text="RotateDrawable: current level = 0"
    android:background="@drawable/ic_drawable_rotate"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:layout_margin="5dp"
    tools:ignore="HardcodedText"
    />
```
代码:
```
// 设置旋转drawable
binding.rotateDrawable.setOnClickListener {
    var level = binding.rotateDrawable.background.level
    level = (level + 2000) % 10000
    binding.rotateDrawable.background.level = level
    binding.rotateDrawable.text = "RotateDrawable: current level = $level"
}
```
实际效果:
![SVID_20231120_193642_1.gif](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/5944f05e38c64a9387067a43da3bce91~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=302002&e=gif&f=76&b=fffdff)

## AnimateStateListDrawable
AnimateStateListDrawable就是selector加动画，类似的还有好多个，可以加帧动画或者vector动画，下面我就简单试了下(注意v21):
```
<?xml version="1.0" encoding="utf-8"?>
<animated-selector xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- 开启状态 -->
    <item
        android:id="@+id/state_on"
        android:state_pressed="true">
        <shape>
            <solid android:color="@color/blue"/>
        </shape>
    </item>

    <!-- 关闭状态 -->
    <item
        android:id="@+id/state_off"
        android:state_pressed="false">
        <shape>
            <stroke android:width="3dp" android:color="@color/gray"/>
        </shape>
    </item>

    <!-- 关闭切换到开启的帧动画 -->
    <transition
        android:fromId="@id/state_off"
        android:toId="@id/state_on">
        <animation-list>
            <item android:duration="200">
                <shape><solid android:color="#000000FF"/> </shape>
            </item>
            <item android:duration="200">
                <shape><solid android:color="#220000FF"/> </shape>
            </item>
            <item android:duration="200">
                <shape><solid android:color="#440000FF"/> </shape>
            </item>
            <item android:duration="200">
                <shape><solid android:color="#660000FF"/> </shape>
            </item>
            <item android:duration="200">
                <shape><solid android:color="#880000FF"/> </shape>
            </item>
            <item android:duration="200">
                <shape><solid android:color="#AA0000FF"/> </shape>
            </item>
            <item android:duration="200">
                <shape><solid android:color="#CC0000FF"/> </shape>
            </item>
            <item android:duration="200">
                <shape><solid android:color="#EE0000FF"/> </shape>
            </item>
            <item android:duration="200">
                <shape><solid android:color="#FF0000FF"/> </shape>
            </item>
        </animation-list>
    </transition>

    <!-- 开启切换到关闭的动画 -->
    <transition
        android:fromId="@id/state_on"
        android:toId="@id/state_off">
        <animation-list>
            <item android:duration="200">
                <shape><solid android:color="#FF0000FF"/> </shape>
            </item>
            <item android:duration="200">
                <shape><solid android:color="#EE0000FF"/> </shape>
            </item>
            <item android:duration="200">
                <shape><solid android:color="#CC0000FF"/> </shape>
            </item>
            <item android:duration="200">
                <shape><solid android:color="#AA0000FF"/> </shape>
            </item>
            <item android:duration="200">
                <shape><solid android:color="#880000FF"/> </shape>
            </item>
            <item android:duration="200">
                <shape><solid android:color="#660000FF"/> </shape>
            </item>
            <item android:duration="200">
                <shape><solid android:color="#440000FF"/> </shape>
            </item>
            <item android:duration="200">
                <shape><solid android:color="#220000FF"/> </shape>
            </item>
            <item android:duration="200">
                <shape><solid android:color="#000000FF"/> </shape>
            </item>
        </animation-list>
    </transition>
</animated-selector>
```
使用:
```
<TextView
    android:id="@+id/animateStateListDrawable"
    android:text="AnimateStateListDrawable(press)"
    android:background="@drawable/ic_drawable_animate_state_list"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:layout_margin="5dp"
    android:clickable="true"
    android:focusable="true"
    tools:ignore="HardcodedText"
    />
```
实际效果:
![SVID_20231120_193654_1.gif](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/06e6cfa6aae542de84b43379bd701555~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=269807&e=gif&f=73&b=fffdff)

## 其他
好像还有其他几个，adaptive-icon、animated-rotate、animated-vector、animation-list，animated的都类似，后面有时间搞下动画，试试vector动画，adaptive-icon好像是和app图标相关的，用到再看。

## 小结
这里花了点时间，试了下各种drawable的使用效果，有的有状态，有的和level相关，有的配合动画，还挺有意思，也自定义了一个根据level增加圆圈的drawable，又学习了！

8点11，下班！