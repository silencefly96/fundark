# Android 属性动画实践
## 前言
最近用零碎的时间，把drawable、view动画、属性动画都试了试，这里是第三篇，算是一点点打基础吧。

## 通过XML实现
和view动画类似，属性动画也可以用XML或代码实现，不过属性动画要放到res的animator目录下，下面是一个例子:
> res/animator/animator_set_test.xml
```
<?xml version="1.0" encoding="utf-8"?>
<set xmlns:android="http://schemas.android.com/apk/res/android"
    android:ordering="sequentially"
    >

<!--    <animator-->
<!--        android:duration="1000"-->
<!--        android:repeatMode="reverse"-->
<!--        android:interpolator="@android:interpolator/decelerate_cubic"-->
<!--        android:startOffset="100"-->
<!--        android:repeatCount="5"-->
<!--        android:valueType="colorType"-->
<!--        android:valueFrom="@color/blue"-->
<!--        android:valueTo="@color/yellow"-->
<!--        />-->
    <!--  valueType有四种: colorType、floatType、intType、pathType  -->
    <!--  @android:animator中包含很多animator动画，但是插值器保存在@android:interpolator，和animation不能通用  -->

    <set android:ordering="together">
        <objectAnimator
            android:propertyName="x"
            android:valueType="floatType"
            android:valueTo="100"
            android:duration="1000"
            />
        <!--    这里x需要使用float，注意属性类型要写对，不然没反应    />-->

        <objectAnimator
            android:propertyName="translationY"
            android:valueType="floatType"
            android:valueTo="100"
            android:duration="1000"
            />
        <!--   无法知道起始值     />-->

    </set>

    <objectAnimator
        android:propertyName="translationY"
        android:valueType="floatType"
        android:valueFrom="100"
        android:valueTo="0"
        android:duration="1000"
        />
    <!--    注意translationY和y属性的区别    />-->

    <objectAnimator
        android:propertyName="translationX"
        android:valueType="floatType"
        android:valueTo="100dp"
        android:duration="1000"
        />
    <!--    android:valueTo="75%p"，这样的写法不对了    />-->
</set>
```
layout代码:
```
<TextView
    android:id="@+id/animatorSetTest"
    android:text="AnimatorSetFormXML(click)"
    android:background="@drawable/ic_launcher"
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
val animatorSet = AnimatorInflater.loadAnimator(requireContext(), R.animator.animator_set_test)
animatorSet.setTarget(binding.animatorSetTest)
animatorSet.start()
binding.animatorSetTest.setOnClickListener {
    // 和view动画不同，点击事件生效的位置和属性动画有关
    showToast("new position click work!")
}
// 这里退出时集中关闭。
animatorCollector.add(animatorSet)
```
注意属性动画退出时要关闭，不要持有其他对象，比如binding，不然会闪退(这里showToast并不会)，如果无限的动画，还可能造成内存泄露。

实际效果:

![petal_20231204_160653.gif](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/3321c13307614eb1b2fdccc02fe52d58~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=561161&e=gif&f=99&b=fffbff)

可以看到属性动画移动后，点击事件的位置也跟着变化了，因为它改的是view的属性，而不仅仅是显示的画面，当然也要记住animator是通过set方法去修改属性的，如果set方法改不了或者是其他功能，就要想想办法了。

## 通过代码实现
一般来说，使用属性动画，一般都是通过代码实现，因为属性的初始值比较难确定，动态设置更为准确，还有就是我发现view动画里面很好用的"%p"，好像用不了了(如果不对，评论区可以指出)，下面是一个代码实现的animator:
```
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:clipChildren="false"
    android:clipToPadding="false"
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <LinearLayout
        android:id="@+id/container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp"
        android:orientation="vertical"
        android:clipChildren="false"
        android:clipToPadding="false"
        >
        
        <TextView
            android:id="@+id/animatorSetTest2"
            android:text="AnimatorSetFormCode(click)"
            android:background="@drawable/ic_launcher"
            android:layout_width="wrap_content"
            android:layout_height="80dp"
            android:layout_margin="5dp"
            android:clickable="true"
            android:focusable="true"
            tools:ignore="HardcodedText"
            />
            
    </LinearLayout>

</ScrollView>
```
这里涉及移动到LinearLayout外面，会被clip，可以研究下clipChildren和clipToPadding，上面LinearLayout和ScrollView都加了，很奇怪，一般来说加到祖父级ViewGroup就行了，可能是LinearLayout的高度是wrap_content，而ScrollView的高度和它一样，TextView要跑出的是ScrollView的范围。

当然，如果LinearLayout的范围足够大，根本不会触发clip这样的问题，不过学东西嘛，了解下，前面我手撕Android侧滑栏的文章就有用到，还是有意义的。

代码实现:
```
// 通过代码实现
val animatorSet2 = AnimatorSet()
val target = binding.animatorSetTest2
animatorSet2.playSequentially(
    ObjectAnimator.ofFloat(target, "alpha", 0f, 1f),
    ObjectAnimator.ofFloat(target, "rotationX", 0f, 360f),
    ObjectAnimator.ofFloat(target, "rotationY", 0f, 360f),
    ObjectAnimator.ofFloat(target, "rotation", 0f, 360f),
    ObjectAnimator.ofFloat(target, "scaleX", 1f, 1.5f),
    ObjectAnimator.ofFloat(target, "scaleY", 1f, 1.5f),
    ObjectAnimator.ofFloat(target, "translationX", 0f, 90f),
    ObjectAnimator.ofFloat(target, "translationY", 0f, 90f),
    ObjectAnimator.ofFloat(target, "scaleX", 1.5f, 1f),
    ObjectAnimator.ofFloat(target, "scaleY", 1.5f, 1f),
    ObjectAnimator.ofFloat(target, "translationX", 90f, 0f),
    ObjectAnimator.ofFloat(target, "translationY", 90f, 0f),
    ObjectAnimator.ofFloat(target, "translationZ", 0f, 90f),
    ObjectAnimator.ofFloat(target, "translationZ", 90f, 0f).setDuration(3000),
)
// 注意translationY和y属性的区别
// 移动出边界记得使用clipChildren、clipToPadding(父容器、祖父级容器)
animatorSet2.setDuration(1000).start()
target.setOnClickListener {
    if (animatorSet2.isRunning) {
        animatorSet2.end()
        showToast("end animatorSetTest2, start new turn!")
    }
    animatorSet2.start()
}
animatorCollector.add(animatorSet2)
```
实际效果:

![SVID_20231204_160853_1.gif](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/a55a2b74ec054dc7b68fb2b65083c655~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=1561444&e=gif&f=142&b=fffdff)

ObjectAnimator的属性字符串会被Android Studio检查，写错了会有报错，如果没报错就放心用吧。

## ViewPropertyAnimator
ViewPropertyAnimator真就大大简化了属性动画的使用，直接由View类提供一个animator来操作，还是链式调用，正式小刀划屁股，开了眼了，下面是例子:
```
<TextView
    android:id="@+id/viewPropertyAnimator"
    android:text="ViewPropertyAnimator(click)"
    android:background="@drawable/ic_launcher"
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
// ViewPropertyAnimator
binding.viewPropertyAnimator.animate()
    .translationX(100f)
    .rotationX(360f)
    .setDuration(2000)
    .withEndAction(Runnable {
        // showToast("ViewPropertyAnimator EndAction act!")
    })
    .start()
binding.viewPropertyAnimator.setOnClickListener {
    // By可以多次触发，不带By只触发一次
    binding.viewPropertyAnimator.animate()
        .translationXBy(20f)
        .rotation(180f)
        .setDuration(1000)
        .start()
}
```
这不比View动画还简单么！

![petal_20231204_161054.gif](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/f416780e26764103b104b6ae8b648d64~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=850616&e=gif&f=94&b=fffbff)

## ValueAnimator使用
ValueAnimator是属性动画的核心，它负责计算动画每一帧对应的数值，而ObjectAnimator提供的才是对属性值的更改，有时候我们只使用ValueAnimator反而能够更灵活，比如配合addUpdateListener对view进行layout。

下面简单写了个例子:
```
<TextView
    android:id="@+id/valueAnimator"
    android:text="ValueAnimator(click)"
    android:background="@drawable/ic_launcher"
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
// ValueAnimator使用
val valueAnimator = ValueAnimator.ofFloat(0f, 500f)
valueAnimator.repeatCount = 1
valueAnimator.repeatMode = ValueAnimator.REVERSE
valueAnimator.duration = 10000
// 更新
valueAnimator.addUpdateListener {
    val value = it.animatedValue as Float
    binding.valueAnimator.translationX = value
}
// 添加监听器，提供了可选监听器
valueAnimator.addListener(object : Animator.AnimatorListener {
    override fun onAnimationStart(animation: Animator?) {}
    override fun onAnimationEnd(animation: Animator?) {}
    override fun onAnimationCancel(animation: Animator?) {}
    override fun onAnimationRepeat(animation: Animator?) {
        showToast("ValueAnimator repeat")
    }
})
valueAnimator.addListener(onEnd = {
    showToast("ValueAnimator end")
})
binding.valueAnimator.setOnClickListener {
    valueAnimator.start()
}
animatorCollector.add(valueAnimator)
```
主要就是在addListener和addUpdateListener里面操作。

实际效果:

![SVID_20231204_161347_1.gif](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/61105f9fb7e24e439e36a158447b0526~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=1840690&e=gif&f=217&b=fffdff)

## TimeInterpolator使用
TimeInterpolator按我理解啊，就是根据时间的百分比计算属性的百分比，可以更改对应时间段属性值的密集度，越密集反应在动画上就是慢，越稀疏那么动画就移动的更快，下面看个例子:
```
<TextView
    android:id="@+id/timeInterpolator"
    android:text="TimeInterpolator: 抛物线(click)"
    android:background="@drawable/ic_launcher"
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
// 时间插值器，写个抛物线吧: y = -4 * (x - 0.5)^2 + 1，过三个点(0,0)(0.5,1)(1,0)
// 根据时间的百分比计算属性的百分比
val timeInterpolator =
    TimeInterpolator { input -> -4f * (input - 0.5).pow(2.0).toFloat() + 1 }
val animator = ObjectAnimator.ofFloat(binding.timeInterpolator,
    "translationX", 0f, 300f)
animator.interpolator = timeInterpolator
animator.setDuration(3000).start()
binding.timeInterpolator.setOnClickListener {
    animator.start()
}
animatorCollector.add(animator)
```
实际就是一个映射关系，可以理解为输入x(时间百分比)，输出y(属性百分比)，实际上属性值 Y=start + (end - start)*y。

实际效果:

![SVID_20231204_161620_1.gif](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/2c707941dcbf49e8a4e091d7db08fde2~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=377023&e=gif&f=49&b=fffdff)

## TypeEvaluator使用
TypeEvaluator我觉得就是对属性动画的扩展，本来只能对int、float以及color修改，自定义TypeEvaluator后，可以识别自己需要的属性，然后根据属性的百分比确定属性的值，下面看例子:
```
<TextView
    android:id="@+id/typeEvaluator"
    android:text="TypeEvaluator: 正弦(click)"
    android:background="@drawable/ic_launcher"
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
// 类型估值器，写个正弦函数: x = x, y = height * sin 2 * PI * x
// 根据属性的百分比确定属性的值
val height = 100f
val typeEvaluator = TypeEvaluator<Pair<Float, Float>> { fraction, startValue, endValue ->
    // x轴上线性移动
    val fx = (endValue.first - startValue.first) * fraction

    // y轴在线性移动上上，叠加正弦波动
    val dy = height * sin(2 * Math.PI * fraction).toFloat()
    val fy = (endValue.second - startValue.second) * fraction + dy
    return@TypeEvaluator Pair<Float, Float>(fx, fy)
}
val animator2 = ValueAnimator.ofObject(typeEvaluator, Pair(0f, 0f), Pair(300f, 0f))
// 根据pair的值更新位置坐标
animator2.addUpdateListener {
    val value = it.animatedValue as Pair<*, *>
    binding.typeEvaluator.translationX = value.first as Float
    binding.typeEvaluator.translationY = value.second as Float
}
animator2.setDuration(5000).start()
binding.typeEvaluator.setOnClickListener {
    animator2.start()
}
animatorCollector.add(animator2)
```
实际就是根据属性的百分比，以及起始属性值，确定该位置的属性值。

实际效果:

![SVID_20231204_161735_1.gif](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/8eba113313ae42479b94a62aec6f6271~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=1062838&e=gif&f=111&b=fffdff)

## Keyframe关键帧
关键帧感觉就是把动画分成好几部分执行，感觉就是顺序执行的动画，但是两个帧之间的动画以这两帧为起始状态，说的不是很好，看代码吧:
```
<TextView
    android:id="@+id/keyframe"
    android:text="Keyframe(click)"
    android:background="@drawable/ic_launcher"
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
// 关键帧
val frame1 = Keyframe.ofFloat(0f, 0f)
// 结束时回弹一下
frame1.interpolator = OvershootInterpolator()
val frame2 = Keyframe.ofFloat(0.25f, 300f)
// 开始回拉一下
frame2.interpolator = AnticipateInterpolator()
val frame3 = Keyframe.ofFloat(0.5f, 0f)
// 结束时Q弹一下
frame3.interpolator = BounceInterpolator()
val frame4 = Keyframe.ofFloat(0.75f, 300f)
// 来回循环，实际就是正弦，传入参数是执行次数(影响的是前面的动画，在这是0.5f-0.75f的效果)
frame4.interpolator = CycleInterpolator(2f)
val frame5 = Keyframe.ofFloat(1f, 0f)
val holder = PropertyValuesHolder.ofKeyframe("translationX", frame1, frame2, frame3, frame4, frame5)
// 这里还能转换属性类型
// holder.setConverter()
val animator3 = ObjectAnimator.ofPropertyValuesHolder(binding.keyframe, holder)
animator3.setDuration(4 * 5000).start()
animator3.addUpdateListener {
    // 根据时段添加说明
    val addedStr = when(it.animatedFraction) {
        in 0f..0.25f -> "OvershootInterpolator"
        in 0.25f..0.5f -> "AnticipateInterpolator"
        in 0.5f..0.75f -> "BounceInterpolator"
        in 0.75f..1.0f -> "CycleInterpolator"
        else -> ""
    }
    binding.keyframe.text = "Keyframe(click): $addedStr"
}
binding.keyframe.setOnClickListener {
    animator3.start()
}
animatorCollector.add(animator3)
```
这里顺便把一些默认的插值器给用上，四个比较有意思的东西，下面是实际效果:

![SVID_20231204_161906_1.gif](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/f1167db721da467ebdaea1cf28b3e567~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=2197814&e=gif&f=209&b=fffdff)

## PropertyValuesHolder使用
其实上面已经用到了PropertyValuesHolder，我感觉他就是一个没有目标的动画，最后需要用ObjectAnimator的ofPropertyValuesHolder方法来生成完整的动画，下面是例子:
```
<TextView
    android:id="@+id/propertyValuesHolder"
    android:text="PropertyValuesHolder(click)"
    android:background="@drawable/ic_launcher"
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
// PropertyValuesHolder，就是一个没有目标的动画吧
val holder1 = PropertyValuesHolder.ofFloat("alpha", 0f, 1f)
val holder2 = PropertyValuesHolder.ofFloat("rotation", 0f, 360f)
val holder3 = PropertyValuesHolder.ofFloat("translationX", 0f, 100f)
val animator4 = ObjectAnimator.ofPropertyValuesHolder(binding.propertyValuesHolder, holder1,holder2, holder3)
animator4.repeatMode = ObjectAnimator.REVERSE
animator4.setDuration(3000).start()
binding.propertyValuesHolder.setOnClickListener {
    animator4.start()
}
animatorCollector.add(animator4)
```
比较简单，就是一起执行几个holder的动画，下面是效果图:

![SVID_20231204_162046_1.gif](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/10d268cd48d84b139e70178547a9725e~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=329665&e=gif&f=54&b=fffdff)

## 小结
花了点时间把animator实践了一下，也就看个效果，里面的原理没有去深究。