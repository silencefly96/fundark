# Android Vector动画实践
## 前言
最近又忙了挺久，一忙又想偷懒，又不想学习。这个关于vector动画的实践，断断续续调试了挺久，今天写篇文章总结下，我自己觉得还是知识满满吧。

这里先给个看完这篇文章，最终效果的图，我觉得还行:

![petal_20231228_222123.gif](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/8036f8ba0d104feda7b4630afb5608b1~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=216&h=216&s=439099&e=gif&f=153&b=fffbff)

## Vector的使用
关于Vector的概念我这不想写太多，其实和SVG差不多，下面直接上干货，直接说怎么用。

### Vector Drawable案例
由于我编写的矢量图形有点难看，先来张Android图片镇楼:
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:viewportWidth="500"
    android:viewportHeight="500"
    android:width="500px"
    android:height="500px">
    <group android:name="android">
        <group android:name="head_eyes">
            <path
                android:name="head"
                android:fillColor="#9FBF3B"
                android:pathData="M301.314,83.298l20.159-29.272c1.197-1.74,0.899-4.024-0.666-5.104c-1.563-1.074-3.805-0.543-4.993,1.199L294.863,80.53c-13.807-5.439-29.139-8.47-45.299-8.47c-16.16,0-31.496,3.028-45.302,8.47l-20.948-30.41c-1.201-1.74-3.439-2.273-5.003-1.199c-1.564,1.077-1.861,3.362-0.664,5.104l20.166,29.272c-32.063,14.916-54.548,43.26-57.413,76.34h218.316C355.861,126.557,333.375,98.214,301.314,83.298" />
            <path
                android:name="left_eye"
                android:fillColor="#FFFFFF"
                android:pathData="M203.956,129.438c-6.673,0-12.08-5.407-12.08-12.079c0-6.671,5.404-12.08,12.08-12.08c6.668,0,12.073,5.407,12.073,12.08C216.03,124.03,210.624,129.438,203.956,129.438" />
            <path
                android:name="right_eye"
                android:fillColor="#FFFFFF"
                android:pathData="M295.161,129.438c-6.668,0-12.074-5.407-12.074-12.079c0-6.673,5.406-12.08,12.074-12.08c6.675,0,12.079,5.409,12.079,12.08C307.24,124.03,301.834,129.438,295.161,129.438" />
        </group>
        <group android:name="arms">
            <path
                android:name="left_arm"
                android:fillColor="#9FBF3B"
                android:pathData="M126.383,297.598c0,13.45-10.904,24.354-24.355,24.354l0,0c-13.45,0-24.354-10.904-24.354-24.354V199.09c0-13.45,10.904-24.354,24.354-24.354l0,0c13.451,0,24.355,10.904,24.355,24.354V297.598z" />
            <path
                android:name="right_arm"
                android:fillColor="#9FBF3B"
                android:pathData="M372.734,297.598c0,13.45,10.903,24.354,24.354,24.354l0,0c13.45,0,24.354-10.904,24.354-24.354V199.09c0-13.45-10.904-24.354-24.354-24.354l0,0c-13.451,0-24.354,10.904-24.354,24.354V297.598z" />
        </group>
        <path
            android:name="body"
            android:fillColor="#9FBF3B"
            android:pathData="M140.396,175.489v177.915c0,10.566,8.566,19.133,19.135,19.133h22.633v54.744c0,13.451,10.903,24.354,24.354,24.354c13.451,0,24.355-10.903,24.355-24.354v-54.744h37.371v54.744c0,13.451,10.902,24.354,24.354,24.354s24.354-10.903,24.354-24.354v-54.744h22.633c10.569,0,19.137-8.562,19.137-19.133V175.489H140.396z" />
    </group>
</vector>
```
下面是图形:

<img src="https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/9b4d9b1b5d614fc59a49d884959119e8~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=533&h=531&s=17736&e=png&b=ffffff" alt="image.png" width="30%" />

用一个案例放前面，我只是想让读者看下vector涉及的东西，尤其是这里又group和name的概念，图形是通过pathData来描述的，那话不多说，我们学下如何编写自己的矢量图。

### Vector Drawable编写
看完上面例子我们就来学下如何绘制自己的矢量图，这里又分了几个知识点来看。

#### vector标签
首先我们的矢量图就是drawable，它要再res的drawable目录创建，根标签是vector:
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:width="80dp"
    android:height="80dp"
    android:name="vector"
    android:alpha="1"
    android:autoMirrored="true"
    android:tint="@color/yellow"
    android:tintMode="multiply"
    android:opticalInsetTop="1dp"
    android:opticalInsetRight="1dp"
    android:opticalInsetBottom="1dp"
    android:opticalInsetLeft="1dp"
    tools:ignore="UnusedAttribute,VectorRaster"
    >
    <!--    vector属性说明: -->
    <!--    viewportWidth、viewportHeight: 长宽的点数，绘制的时候按照这个画path-->
    <!--    width、height: 固有宽高，当需要用到drawable宽高的时候会取这个值-->
    <!--    alpha: 透明度，0 - 1-->
    
    <!--    以下属性要求v21: -->
    <!--    autoMirrored: 是否自动镜像翻转，和语言布局有关，比如从右到左(RTL)-->
    <!--    tint: 矢量图形的颜色(作为src且全屏，path为dst)-->
    <!--    tintMode: 着色模式，只支持:-->
    <!--        add: 将颜色与矢量图形的像素进行加法混合。结果颜色将更亮。-->
    <!--        multiply: 将颜色与矢量图形的像素进行乘法混合。结果颜色的亮度将受到矢量图形像素的影响。-->
    <!--        screen: 将颜色与矢量图形的像素进行屏幕混合。结果颜色将反映出矢量图形像素和颜色的亮度。-->
    <!--        src_atop: 将颜色绘制在矢量图形上方，但仅绘制在与矢量图形相交的像素上，并与矢量图形的像素进行混合。-->
    <!--        src_in: 将颜色仅绘制在与矢量图形相交的像素上，其他像素将被透明化。-->
    <!--        src_over: 默认值。将颜色绘制在矢量图形上方，不考虑矢量图形的像素原始颜色。-->
    
    <!--    以下属性要求v29: -->
    <!--    opticalInsetTop/Right/Bottom/Left: 类似padding吧-->
```
大致就以上属性，注释里写的挺清楚了。

#### path属性说明
看望vector标签，我们再来看下path的属性:
```xml
    <!--    path属性说明: -->
    <!--    name: 名称-->
    <!--    pathData: 路径数据-->

    <!--    fillColor: 填充颜色-->
    <!--    fillAlpha: 填充透明度-->
    <!--    fillType: 填充类型-->
    <!--        nonZero: 非零环绕规则，默认，简单说就是内部都填充-->
    <!--            路径中的子路径会根据其方向进行计数。当一个子路径沿顺时针方向绘制时，计数值增加1；
                    当一个子路径沿逆时针方向绘制时，计数值减去1。填充区域位于计数值不为零的区域内。-->
    <!--        evenOdd: 奇偶环绕规则，简单说就是偶数重叠部分不绘制，比如环形部分-->
    <!--            路径中的子路径会根据其方向进行计数，并根据计数值的奇偶性来确定填充区域。当计数值为
                    奇数时，填充区域位于子路径内部；当计数值为偶数时，填充区域位于子路径外部。-->

    <!--    strokeColor: 线条颜色-->
    <!--    strokeWidth: 线条粗细-->
    <!--    strokeAlpha: 线条透明度-->

    <!--    trimPathStart: 定义路径的起始截取位置（0到1之间的值），用于截取路径的一部分。-->
    <!--    trimPathEnd: 定义路径的结束截取位置（0到1之间的值），用于截取路径的一部分。-->
    <!--    trimPathOffset: 定义路径的截取偏移量，用于调整截取位置。-->

    <!--    strokeLineCap: 定义路径的描边端点形状-->
    <!--        butt: 默认值。直线末端以路径结束的位置结束，没有任何扩展。-->
    <!--        round: 直线末端以一个圆形的点结束，直径等于描边宽度。-->
    <!--        square: 直线末端以一个方形的点结束，宽度和描边宽度相同，长度是描边宽度的一半。-->
    <!--    strokeLineJoin: 线条拐弯处的样式-->
    <!--        round: 圆角-->
    <!--        bevel: 斜角-->
    <!--        miter: 默认值。斜切尖角-->
    <!--    strokeMiterLimit: 控制strokeLineJoin为miter时，设置最大斜接长度，值越大越尖，超过会变平角bevel-->
    <!--        miter_length: 斜接长度指的是在两条线交汇处内角和外角之间的距离。-->
    <!--        line_width: 线条宽度，即strokeWidth-->
    <!--        strokeMiterLimit = miter_length / line_width: 通过两者之比限制大小，默认是4-->
    <!--        strokeMiterLimit = 1 / sin x: 取直角三角形，对边取1的时候，Miter就是斜边长度-->
```
前面几个属性都简单，strokeLineCap和strokeLineJoin实际和canvas的path使用类似，我在前面自定义View里面也有说明，后面我会对它进行实践。

这里最重要的就是trimPath了，这个属性是我们实现轨迹动画的核心，后面也有实践，而且很多。

#### path语法说明
path属性其实没什么好说的，作为矢量图，最重要的还是绘制矢量图的语法，下面看下:
```
<!--    Vector语法说明(大写表示绝对坐标，小写表示相对坐标):-->

<!--    M/m (x,y): 移动指定位置-->

<!--    L/l (x,y): 从当前位置，绘制线段到指定位置-->

<!--    H/h (x): 从当前位置，绘制水平线到达指定的 x 坐标-->
<!--    V/v (x): 从当前位置，绘制竖直线到达指定的 y 坐标-->

<!--    A/a (rx,ry,xr,laf,sf,x,y): 从当前位置，绘制弧线到指定位置-->
<!--        rx、ry为椭圆长短轴半径，xr为x轴旋转角度，laf(0:小弧度,1:大弧度)，sf(0:逆时针,1:顺时针)，x、y为终点-->

<!--    Q/q (x1,y1,x,y): 从当前位置，绘制二次贝塞尔曲线到指定位置-->
<!--        x、y为终点，前面的xn、yn为控制点，控制点和起始点连接的线是曲线在起始点处的切线-->
<!--    T/t (x,y): 从当前位置绘制，平滑二次贝塞尔曲线到指定位置-->
<!--        x、y表示终点，前一段路径要求是贝塞尔曲线，会保持前一个终点的切线方向，并由这个方向与本次终点形成对称关系，得到控制点-->

<!--    C/c (x1,y1,x2,y2,x,y): 从当前位置，绘制三次贝塞尔曲线到指定位置-->
<!--    S/s (x2,y2,x,y): 从当前位置，绘制平滑三次贝塞尔曲线到指定位置-->

<!--    Z/z 闭合当前路径-->
```
这里文字说明很多是我自己写的，网上好多说明不对，一切还是以实践为准，下面我会把这些命令都用一遍。

#### group属性说明
实际上path是不能直接平移、旋转、缩放的，因为vector动画还是属性动画实现的，经过上面介绍我们看到path并没有这些操作属性，实际这些操作属性在group标签上:
```xml
<!--   group说明:-->
<!--   name: 属性名，用于绑定animator-->
<!--   pivotX、pivotY: 锚点，平移、旋转、缩放的中心点，注意值要使用viewportWidth和viewportHeight的点数-->
<!--   scaleX、scaleY: 缩放倍数-->
<!--   rotation: 旋转，比如 (0 - 360)-->
<!--   translateX、translateY: 平移值，值也是viewportWidth和viewportHeight的点数-->
```
所以，想要对path平移、旋转、缩放，要加一层group标签。

### Vector Drawable实践
下面就用上面的知识来实践下:
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:width="80dp"
    android:height="80dp"
    android:name="vector"
    android:alpha="1"
    android:autoMirrored="true"
    android:tint="@color/yellow"
    android:tintMode="multiply"
    android:opticalInsetTop="1dp"
    android:opticalInsetRight="1dp"
    android:opticalInsetBottom="1dp"
    android:opticalInsetLeft="1dp"
    tools:ignore="UnusedAttribute,VectorRaster"
    >

    <group android:name="man">
        <!-- 绘制思路: 通过viewportWidth和viewportHeight来定位 -->

        <!-- 画一个半圆和两个圆，一个圆需要两个半圆拼起来，注意A和a的使用 -->
        <group android:name="head">

            <!-- 加了两个眼睛，好丑啊，试了下fillType的效果 -->
            <path
                android:fillColor="#9FBF3B"
                android:fillAlpha="0.6"
                android:fillType="evenOdd"
                android:pathData="M6,7 A6,6,0,0,1,18,7Z
                    M10,3 a1,1,0,0,1,0,2 a1,1,0,0,1,0,-2z
                    M14,3 a1,1,0,0,1,0,2 a1,1,0,0,1,0,-2z"/>
        </group>


        <!-- 画一个矩形加两个角，使用L/l、H、v，三个画线段的 -->
        <group android:name="body">

            <!-- strokeWidth是根据设置的点数来的，我这24个点 -->
            <!-- 两个角是用来看MiterLimit的，miter_length / line_width > 4时变平角bevel -->
            <!-- strokeMiterLimit = 1 / sin x，左边(45°)=根号2，右边(直角边:4,1)=根号17 -->
            <path
                android:strokeColor="#3F51B5"
                android:strokeWidth="0.5"
                android:strokeAlpha="0.9"

                android:strokeLineCap="round"
                android:strokeLineJoin="miter"
                android:strokeMiterLimit="4"

                android:fillColor="#9FBF3B"
                android:pathData="M4,8 L22,8 L18,9 l0,7 H6 v-6 L4,8z"/>
        </group>

        <!-- 画两个简单曲线图形，使用Q/q、T/t -->
        <group android:name="feet">
            <path
                android:name="left_feet"
                android:fillColor="#9FBF3B"
                android:pathData="M9,18 Q10,16,11,18 T11,22 q-1,2,-2,0 t0,-4z"/>

            <path
                android:name="right_feet"
                android:fillColor="#9FBF3B"
                android:pathData="M13,18 Q14,16,15,18 T15,22 q-1,2,-2,0 t0,-4z"/>
        </group>

        <!-- 画两个复杂曲线图形，使用C/c、S/s -->
        <group android:name="arm"
            android:pivotX="12"
            android:pivotY="12"
            android:translateY="2"
            android:scaleX="1.05"
            >

            <!-- 两段正弦波形，上下相间2个距离，手末端用平滑三阶贝塞尔曲线相连 -->
            <path
                android:name="left_arm"
                android:fillColor="#9FBF3B"
                android:pathData="M6,8 C5,7,3,9,2,8 S0,8,2,10 c1,1,3,-1,4,0z"/>

            <!-- 和上面类似，正弦变余弦 -->
            <path
                android:name="right_arm"
                android:fillColor="#9FBF3B"
                android:pathData="M18,8 c1,-1,3,1,4,0 s2,0,0,2 C21,11,19,9,18,10z"/>
        </group>
    </group>
</vector>
```
模仿了一下上面的demo，但是可以去使用所有知识还是听傻的，画出来的图很丑:

<img src="https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/8efda0644fbc4283bde0f1571dff6060~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=655&h=650&s=30035&e=png&b=ffffff" alt="image.png" width="50%" />

这个图形vector的知识大部分覆盖了吧，关于trimPath的会在下面的vector动画中实践。

## SVG轨迹动画
上面说到了trimPath，这里就正式来讲了，说白了就是通过objectAnimator改变trimPath的start或end位置，来达到显示部分轨迹的效果，下面看代码:

> res/drawable/ic_vector_path.xml
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="80dp"
    android:height="80dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    >

    <path
        android:name="vector_path"
        android:strokeWidth="1"
        android:strokeColor="#9FBF3B"
        android:pathData="M12,4 a8,8,0,1,1,0,16 a8,8,0,1,1,0,-16z" />

    <!--path不能直接旋转，50%这样的写法也不能用-->
    <group
        android:name="vector_arrow"
        android:pivotX="12"
        android:pivotY="12"
        >

        <path
            android:strokeWidth="1"
            android:strokeColor="#9FBF3B"
            android:fillColor="#9FBF3B"
            android:pathData="M12,12 l3,-3 l-1,-1 l2,0, l0,2 l-1,-1z" />
    </group>

</vector>
```
这个比较简单，主要看vector_path这个圆，这是我们通过trimPath要显示的全部内容，动画开始后会根据时间变化，显示它的部分内容。

这里顺带加了个旋转效果，看起来会更有意思些，

上面只是一张图，它是不会动的，要实现动画效果，还是得用到animator，即属性动画:

> res/animator/animator_vector_path.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android">
    <objectAnimator
        android:duration="2000"
        android:interpolator="@android:interpolator/linear"
        android:propertyName="trimPathStart"
        android:valueFrom="0.0"
        android:valueTo="1"
        android:valueType="floatType"/>

    <objectAnimator
        android:duration="2000"
        android:interpolator="@android:interpolator/accelerate_decelerate"
        android:propertyName="trimPathEnd"
        android:valueFrom="0.25"
        android:valueTo="1.25"
        android:valueType="floatType"/>

    <objectAnimator
        android:duration="2000"
        android:interpolator="@android:interpolator/linear"
        android:propertyName="strokeColor"
        android:valueFrom="#009688"
        android:valueTo="#FF5722"
        android:valueType="colorType"/>
</set>
```
这里让trimPath同时改变，并且设置了不同的interpolator，这样前后效果不一样，动起来的线段长度也不一样，这里还加了个颜色变化。

旋转的动画也在这写一下，因为要分别绑定不同name，所以要分两个文件:
> res/animator/animator_vector_arrow.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android">
    <objectAnimator
        android:duration="2000"
        android:interpolator="@android:interpolator/anticipate_overshoot"
        android:propertyName="rotation"
        android:valueFrom="0"
        android:valueTo="360"
        android:valueType="floatType"/>
</set>
```

写好矢量图形和属性动画后，我们就要让图形动起来，上面的vector标签是静态图，要动起来的话，就要使用animated-vector标签:
> res/drawable/ic_animated_vector_path.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<animated-vector
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:drawable="@drawable/ic_vector_path"
    tools:ignore="NewApi">

    <target
        android:animation="@animator/animator_vector_path"
        android:name="vector_path"/>

    <target
        android:animation="@animator/animator_vector_arrow"
        android:name="vector_arrow"/>

</animated-vector>
```
实际就是把animator和矢量图形的path或group绑定起来。

接下来把动图放到XML布局:
```xml
<TextView
    android:id="@+id/animatedVectorPath"
    android:text="AnimatedVectorPath(click)"
    app:drawableBottomCompat="@drawable/ic_animated_vector_path"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="5dp"
    android:clickable="true"
    android:focusable="true"
    tools:ignore="HardcodedText"
    />
```

这里比较随意了，用的TextView的drawableBottomCompat，最后在代码里面设置下触发:
```java
// SVG轨迹动画，取巧用一下TextView的drawableBottomCompat:
val drawable = binding.animatedVectorPath.compoundDrawables[3] as Animatable
drawable.start()
binding.animatedVectorPath.setOnClickListener {
    if (!drawable.isRunning) drawable.start()
}
```

下面来看下实际效果:

![SVID_20231228_220220_1.gif](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/9b27bc56ed3341e3a068deacbf1d285c~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=173937&e=gif&f=73&b=fffdff)

## SVG路径动画
什么轨迹动画，说白了就是对path的trimPath的属性动画，没什么特别的，而SVG路径动画就不一样了，它是由一个path变换到另一个path的动画，很炫酷。

按我理解啊，应该就是Android自动实现了pathData的TypeEvaluator，这样就能把一个属性流畅的变化到另一个属性，话不多说，下面看代码:
> res/drawable/ic_vector_morphing.xml
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="80dp"
    android:height="80dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    >

    <!--    路径变换要求: -->
    <!--    1、A和B具有相同数量的绘图命令。-->
    <!--    2、对于所有绘图命令 ， A和B相对应的绘图命令必须是相同的绘图类型。-->
    <!--    3、对于所有绘图命令， A和B相对应的绘图命令必须具有相同数量的参数。-->
    <!--    说人话就是，直线对直线，弧线对弧线，曲线对曲线，线的数量一致，每段线使用命令一致。-->

    <!--四个四分之一圆组成一个圆(取小弧)，放在中心点-->
    <path
        android:name="vector_circle"
        android:strokeWidth="1"
        android:strokeColor="#9FBF3B"
        android:pathData="M12,12
                            A0,0,0,0,1,12,12
                            A0,0,0,0,1,12,12
                            A0,0,0,0,1,12,12
                            A0,0,0,0,1,12,12Z"
        />

    <!--四个四分之一圆组成一个圆(取小弧)，变大后的样子(调试用，隐藏了)-->
    <path
        android:name="vector_circle_after"
        android:strokeAlpha="0"
        android:strokeWidth="1"
        android:strokeColor="#9FBF3B"
        android:pathData="M4,12
                            A8,8,0,0,1,12,4
                            A8,8,0,0,1,20,12
                            A8,8,0,0,1,12,20
                            A8,8,0,0,1,4,12Z"
        />

    <!--四条从顶点连接到中心点的线段-->
    <path
        android:name="vector_four_line"
        android:strokeWidth="1"
        android:strokeColor="#9FBF3B"
        android:pathData="M0,0 L12,12
                            M24,0 L12,12
                            M24,24 L12,12
                            M0,24 L12,12"
        />

    <!--四条从顶点连接到圆上下左右顶点的线段，变大后的样子(调试用，隐藏了)-->
    <path
        android:name="vector_four_line_after"
        android:strokeAlpha="0"
        android:strokeWidth="1"
        android:strokeColor="#9FBF3B"
        android:pathData="M0,0 L12,4
                            M24,0 L20,12
                            M24,24 L12,20
                            M0,24 L4,12"
        />

</vector>
```
这里四个path,共两组，包含变化前后的pathData，可以改下strokeAlpha来显示它们。两组path也很简单，就是圆和四根线段。

第一个路径变化，是把一个半径为0的圆变成一个半径为8的圆，看起来很像放大，为了和放大区别，我又让四个顶点链接圆的顶点，四条线段都有一端会随着圆的顶点变化。

这里的变化有一些限制，可以看下我注释的内容，这里可以用其他软件改，但是我觉得这是设计师的活，对这个有兴趣的可以自己找资料了解下吧。

下面继续看绑定的属性动画:
> res/animator/animator_vector_morphing_circle.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android">
    <objectAnimator
        android:duration="2000"
        android:propertyName="pathData"
        android:valueFrom="M12,12
                            A0,0,0,0,1,12,12
                            A0,0,0,0,1,12,12
                            A0,0,0,0,1,12,12
                            A0,0,0,0,1,12,12Z"
        android:valueTo="M4,12
                            A8,8,0,0,1,12,4
                            A8,8,0,0,1,20,12
                            A8,8,0,0,1,12,20
                            A8,8,0,0,1,4,12Z"
        android:valueType="pathType" />
</set>
```
这是圆的变化。

> res/animator/animator_vector_morphing_line.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android">
    <objectAnimator
        android:duration="2000"
        android:propertyName="pathData"
        android:valueFrom="M0,0 L12,12
                            M24,0 L12,12
                            M24,24 L12,12
                            M0,24 L12,12"
        android:valueTo="M0,0 L12,4
                            M24,0 L20,12
                            M24,24 L12,20
                            M0,24 L4,12"
        android:valueType="pathType" />
</set>
```
这是四条路径的变化。

再看下绑定好的动图:
> res/drawable/ic_animated_vector_morphing.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<animated-vector
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:drawable="@drawable/ic_vector_morphing"
    tools:ignore="NewApi">

    <target
        android:animation="@animator/animator_vector_morphing_circle"
        android:name="vector_circle"/>

    <target
        android:animation="@animator/animator_vector_morphing_line"
        android:name="vector_four_line"/>

</animated-vector>
```

在XML布局中使用:
```xml
<TextView
    android:id="@+id/animatedVectorMorphing"
    android:text="AnimatedVectorMorphing(click)"
    app:drawableBottomCompat="@drawable/ic_animated_vector_morphing"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="5dp"
    android:clickable="true"
    android:focusable="true"
    tools:ignore="HardcodedText"
    />
```

代码里面设置下点击:
```java
// SVG路径动画，从一种形状变成另一种形状:
val drawable1 = binding.animatedVectorMorphing.compoundDrawables[3] as Animatable
drawable1.start()
binding.animatedVectorMorphing.setOnClickListener {
    if (!drawable1.isRunning) drawable1.start()
}
```

实际效果还是挺不错的:

![SVID_20231228_220309_1.gif](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/98d40d56a058407fb3ee09f3631ac5c3~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=255134&e=gif&f=76&b=fffdff)

## SVG混合动画
说了这么多，最后搞一个有点技术含量的东西来收尾吧！下面是一个混了上面多种动画效果的组合动画，就不多解释了，直接看代码:

> res/drawable/ic_vector_combine.xml
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:width="80dp"
    android:height="80dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    >

    <group
        android:name="circle"
        android:pivotX="12"
        android:pivotY="12"
        >

        <!--四段三阶贝塞尔曲线拟合一个圆-->
        <path
            android:name="vector_bezier_circle"
            android:strokeWidth="1"
            android:strokeColor="#9FBF3B"
            android:pathData="M4,12
                            C4,8,8,4,12,4
                            C16,4,20,8,20,12
                            C20,16,16,20,12,20
                            C8,20,4,16,4,12Z"
            tools:ignore="VectorRaster"
            />

        <!--四段三阶贝塞尔曲线拟合一个正方形-->
        <path
            android:name="vector_bezier_circle_after"
            android:strokeAlpha="0"
            android:strokeWidth="1"
            android:strokeColor="#9FBF3B"
            android:pathData="M4,4
                            C8,4,16,4,20,4
                            C20,8,20,16,20,20
                            C16,20,8,20,4,20
                            C4,16,4,8,4,4Z"
            />

    </group>

    <group
        android:pivotX="12"
        android:pivotY="12"
        android:name="heart"
        >

        <!--配合strokeLineCap和strokeLineJoin画一个爱心-->
        <path
            android:strokeWidth="1"
            android:strokeColor="#FF0000"
            android:strokeLineCap="round"
            android:strokeLineJoin="miter"
            android:strokeMiterLimit="4"
            android:pathData="M11.5,2 l0.5,0.5 l0.5,-0.5"
            />
    </group>
    
</vector>
```
这里也是两个path，第一个是用三阶贝塞尔函数画的正方形变化到拟合圆，另一个是用strokeLineCap和strokeLineJoin画一个折角形成的心型。

下面会增加一些动画，让这两条path尽可能的七十二变。

首先是把正方形变化到拟合圆的animator:
> res/animator/animator_vector_combine_bezier.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android"
    android:ordering="sequentially">

    <objectAnimator
        android:duration="2000"
        android:propertyName="pathData"
        android:valueFrom="M4,4
                            C8,4,16,4,20,4
                            C20,8,20,16,20,20
                            C16,20,8,20,4,20
                            C4,16,4,8,4,4Z"
        android:valueTo="M4,12
                            C4,8,8,4,12,4
                            C16,4,20,8,20,12
                            C20,16,16,20,12,20
                            C8,20,4,16,4,12Z"
        android:valueType="pathType" />

    <set>

        <objectAnimator
            android:duration="2000"
            android:propertyName="trimPathEnd"
            android:valueFrom="1"
            android:valueTo="0"
            android:valueType="floatType"/>

        <objectAnimator
            android:duration="2000"
            android:interpolator="@android:interpolator/linear"
            android:propertyName="fillColor"
            android:valueFrom="#9FBF3B"
            android:valueTo="#FF0000"
            android:valueType="colorType"/>

    </set>

</set>
```
嘿嘿，这里的set还有另一个属性，就是ordering，我们让path变化后，再做一个trim轨迹动画，同时轨迹变化时还改变fillColor。

这里有两步动画，分别占了2000ms，后面我们注意下这个时机，就能做出一些很妙的配合。

因为path不能直接缩放之类的，我们要对这个三阶贝塞尔曲线的变化做缩放，就得操作group，就得再写一个animator文件，如下:
> res/animator/animator_vector_combine_scale.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android">
    <objectAnimator
        android:duration="2000"
        android:propertyName="scaleX"
        android:valueFrom="1.5"
        android:valueTo="1"
        android:valueType="floatType"/>

    <objectAnimator
        android:duration="2000"
        android:propertyName="scaleY"
        android:valueFrom="1.5"
        android:valueTo="1"
        android:valueType="floatType"/>
</set>
```
这里让正方形和图形同等大小开始缩小，时间在第一个2000ms，所以在path和group会一起变化。

操作完拟合圆，我们再来操作小爱心，这里全是平移、旋转、放大等，都是对group修改，可以写在一个文件里:
> res/animator/animator_vector_combine_rotate.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android"
    android:ordering="sequentially">

    <set>
        <objectAnimator
            android:duration="2000"
            android:propertyName="scaleX"
            android:valueFrom="0"
            android:valueTo="1"
            android:valueType="floatType"/>

        <objectAnimator
            android:duration="2000"
            android:propertyName="scaleY"
            android:valueFrom="0"
            android:valueTo="1"
            android:valueType="floatType"/>
    </set>

    <objectAnimator
        android:duration="2000"
        android:propertyName="rotation"
        android:valueFrom="360"
        android:valueTo="0"
        android:valueType="floatType"/>

    <set>
        <objectAnimator
            android:duration="2000"
            android:propertyName="scaleX"
            android:valueFrom="1"
            android:valueTo="4"
            android:valueType="floatType"/>

        <objectAnimator
            android:duration="2000"
            android:propertyName="scaleY"
            android:valueFrom="1"
            android:valueTo="4"
            android:valueType="floatType"/>

        <objectAnimator
            android:duration="2000"
            android:propertyName="translateY"
            android:valueFrom="0"
            android:valueTo="32"
            android:valueType="floatType"/>
    </set>

</set>
```
这里我们设计了三段动画，第一段从0放大到正常状态，对应正方形变化为拟合圆，第二段旋转，对应拟合圆的trim，第三段同时放大和平移，让爱心移动到图形中心。

把上面三个animator绑定到矢量图:
> res/drawable/ic_animated_vector_combine.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<animated-vector
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:drawable="@drawable/ic_vector_combine"
    tools:ignore="NewApi">

    <target
        android:animation="@animator/animator_vector_combine_bezier"
        android:name="vector_bezier_circle"/>

    <target
        android:animation="@animator/animator_vector_combine_scale"
        android:name="circle"/>

    <target
        android:animation="@animator/animator_vector_combine_rotate"
        android:name="heart"/>

</animated-vector>
```

在XML布局中设置:
```xml
<TextView
    android:id="@+id/animatedVectorCombine"
    android:text="AnimatedVectorCombine(click)"
    app:drawableBottomCompat="@drawable/ic_animated_vector_combine"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="5dp"
    android:clickable="true"
    android:focusable="true"
    tools:ignore="HardcodedText"
    />
```

代码里面启动及设置点击:
```java
// SVG多种动画，从一种形状变成另一种形状:
val drawable2 = binding.animatedVectorCombine.compoundDrawables[3] as Animatable
// 一开始有问题
binding.animatedVectorCombine.post {
    drawable2.start()
}
binding.animatedVectorCombine.setOnClickListener {
    if (!drawable2.isRunning) drawable2.start()
}
```
这里就神奇了，最开始那个三阶贝塞尔变换出不来，后面动画倒是没问题，只能加一层view的post延迟执行。

最后瞧一下实际效果，我是觉得有点炫酷:

![SVID_20231228_220354_1.gif](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/9b639c3a5e9246f8b19c9623cf0f25e5~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=409396&e=gif&f=153&b=fffdff)

## 源码及Demo
源码提交到了我练手的仓库，有兴趣的朋友可以看下: 

[VectorAnimTestDemo](https://github.com/silencefly96/fundark/blob/main/module_tech/src/main/java/com/silencefly96/module_tech/tech/demo/VectorAnimTestDemo.kt)

## 参考文章
编写代码实践的过程中，参考了一些文章，我觉得写得非常好，里面有很多图，值得看一下:

[Android矢量图动画：每人送一辆掘金牌小黄车](https://juejin.cn/post/6847902224396484621)

[Android矢量动画实践](https://www.jianshu.com/p/4707a4738a51)

[Android资源res之矢量图完全指南（加SVG-path命令分析）](https://juejin.cn/post/6844903733734670350)

[Android矢量图(二)--VectorDrawable所有属性全解析](https://www.jianshu.com/p/89efdbe01ac9)

[svg Vector 章节（4）：变形paths](https://www.jianshu.com/p/0792b4d954f7)

## 小结
把Android的矢量图以及矢量动画实践了下，包含pathData语法详解、轨迹动画、路径动画以及混合动画，有GIF可以直接看效果。