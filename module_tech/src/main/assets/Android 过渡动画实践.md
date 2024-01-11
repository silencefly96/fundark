# Android过渡动画实践
## 前言
在学习矢量动画的时候，看到篇别人写的[《Android过渡动画，发现掘金小秘密》](https://juejin.cn/post/6850037271714856968)，觉得挺有意思的，算是拿人家内容做了个实践吧，后面又加了点在Transition和Visibility的实践，实践的过程中也踩了挺多坑，下面就写篇文章记录下，可能不会详细说明原理，但是内容都在代码里，

## 布局变换动画
还是先从简单的来吧，前面Animation和Animator里面讲到过LayoutAnimation和LayoutTransition，这里再来看一下吧，应该也算是中过渡动画。

### LayoutAnimation
LayoutAnimation的使用我这就不重新写了，就是View动画，贴一下之前文章该章节的链接，也可以看下效果:

[Android View动画实践 - LayoutAnimation](https://juejin.cn/post/7308414837288714240#heading-5)

![pic](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/0ceff56bf92844019accb7020e6563ed~tplv-k3u1fbpfcp-jj-mark:3024:0:0:0:q75.awebp#?w=356&h=754&s=299032&e=gif&f=91&b=fffdff)

### LayoutTransition
LayoutTransition也是一样的，只不过里面实现变成了属性动画:

[Android 属性动画实践 - LayoutTransition](https://juejin.cn/post/7308575144926887988#heading-10)

![pic](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/31a53453ac164bd785ae4282ed167a7e~tplv-k3u1fbpfcp-jj-mark:3024:0:0:0:q75.awebp#?w=356&h=754&s=368919&e=gif&f=125&b=fffdff)

## Activity过渡动画
上面两个动画在ViewGroup的子view发生变化的时候，会有过渡效果，而我们一般还会在Activity的过渡需要用到动画，下面讲讲。

### overridePendingTransition
使用Activity的过渡动画，最简单的就是使用overridePendingTransition了，不过overridePendingTransition在Android14被废弃，推荐使用overrideActivityTransition，需要改compileSdkVersion，这里就不提了。

overridePendingTransition在前面Animation的文章中也有写到，这里贴一下:

[Android View动画实践 - overridePendingTransition](https://juejin.cn/post/7308414837288714240#heading-6)

![pic](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/16ed9e54652b40f08ce424fb68bdf2fc~tplv-k3u1fbpfcp-jj-mark:3024:0:0:0:q75.awebp#?w=356&h=754&s=393753&e=gif&f=60&b=fffdff)

### ActivityOptions过渡动画
overridePendingTransition也是在Android5以前使用的方法了，在Android5后面，Google提供了新的Activity过渡动画，下面来看下。

因为过渡动画有几种，我这加了个单选框来选择过渡动画类型，先看下布局文件:
```xml
<TextView
    android:text="使用SceneTransitionAnimation (Android5+)"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_margin="5dp"
    tools:ignore="HardcodedText"
    />

<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    >

    <Button
        android:id="@+id/sceneTransition"
        android:text="点击跳转"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_margin="5dp"
        tools:ignore="HardcodedText"
        />

    <RadioGroup
        android:id="@+id/sceneType"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content">

        <RadioButton
            android:id="@+id/explode"
            android:text="Explode"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            tools:ignore="HardcodedText"
            />

        <RadioButton
            android:id="@+id/slide"
            android:checked="true"
            android:text="Slide"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            tools:ignore="HardcodedText"
            />

        <RadioButton
            android:id="@+id/fade"
            android:text="Fade"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            tools:ignore="HardcodedText"
            />

    </RadioGroup>

</LinearLayout>
```
布局里面就是一个点击跳转的按钮，和三个选择过渡动画类型的单选框，代码也比较简单:
```java
// Activity过渡动画(Android5之后使用)
binding.sceneTransition.setOnClickListener {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        val intent = Intent(requireContext(), SceneTransitionActivity::class.java)

        // 传入过渡动画类型
        intent.putExtra("type", when(binding.sceneType.checkedRadioButtonId) {
            R.id.explode -> "explode"
            R.id.slide -> "slide"
            R.id.fade -> "fade"
            else -> "slide"
        })

        // Create an ActivityOptions to transition between Activities using cross-Activity
        // scene animations.
        val optionsCompat =
            ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity())

        startActivity(intent, optionsCompat.toBundle())
    }else {
        showToast("当前系统版本不支持: ${Build.VERSION.SDK_INT}")
    }
}
```
要实现Activity的过渡效果，需要使用makeSceneTransitionAnimation生成一个optionsCompat，并转成bundle给另一个activity传过去。这里除了makeSceneTransitionAnimation，其他和我们没什么关系。

所以更重要的应该是在另一个activity里面，下面看一下SceneTransitionActivity的写法:
```java
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        // AppCompatActivity中window会被style影响，需要在style中设置下面两个属性
        // 启用窗口过渡属性;
        // window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
    
        // 设置是否需动画覆盖，转场动画中动画之间会有覆盖的情况
        // 可以设置false来让动画有序的进入退出
        // window.allowEnterTransitionOverlap = false
    
        // 根据传入信息配置过渡动画
        when(intent.getStringExtra("type")) {
            "explode" -> Explode()
            "slide" -> Slide()
            "fade" -> Fade()
            else -> CustomVisibility()
        }.apply {
        duration = 500
    
        // 排除状态栏和导航栏(这两个还真有用！！！navigationBarBackground效果不明显)
        // 关于排除的id，可以自定义一个Visibility，在onAppear方法中用Log查看
        excludeTarget(android.R.id.statusBarBackground, true)
        excludeTarget(android.R.id.navigationBarBackground, true)
        // 排查标题栏
        excludeTarget(R.id.action_bar_container, true)
        }.also { transition ->
            // 退出当前界面的过渡动画
            window.exitTransition = transition
            // 进入当前界面的过渡动画
            window.enterTransition = transition
            // 重新进入界面的过渡动画
            window.reenterTransition = transition
        }
    }
    //设置活动布局
    setContentView(mContextView)
}
```
这里就踩坑了，网上的文章基本都是说window的requestFeature要在setContentView前调用，我在这前面调用了还是会报错，因为AppCompatActivity会设置style，设置style的时候会调用setContentView，这样window的属性就不让修改了，正确的操作是继承原有的主题，再修改window的参数:

> manifest里面
```xml
<activity android:name=".tech.scene.SceneTransitionActivity" android:theme="@style/CustomActivityTheme"/>
```

> res/values/themes/themes.xml
```xml
<style name="CustomActivityTheme" parent="Theme.Fundark">
    <!-- 启用 FEATURE_CONTENT_TRANSITIONS 特性 -->
    <item name="android:windowContentTransitions">true</item>
    <item name="android:windowAllowEnterTransitionOverlap">true</item>
</style>
```

也就是把代码中动态设置的属性，放到style文件去。另外在activity返回的时候也要注意下，带动画返回:
```java
binding.button.setOnClickListener {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        // 带动画返回
        finishAfterTransition()
    }else {
        finish()
    }
}
```

说了这么多，还是看下效果吧：

![SVID_20240110_152020_1.gif](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/c49e597f0c9240a3b28b7b43f8a6b114~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=465318&e=gif&f=110&b=fffdff)

### 共享过渡动画
在看了上面三种自带的过渡效果，还有一种让我觉得很炫酷的过渡效果，这里必须得提一下，那就是共享过渡动画。

只不过不知道怎么说，它到底是Activity过渡动画，还是布局过渡动画，感觉像一个View从起始位置移动到目标位置，问GPT却说是TransitionManager控制的在两个页面变换的同一个View，原理有时间再深究，这里看下使用吧:

我这就用了一个TextView来验证，设置了TextView的background:
```xml
<TextView
    android:id="@+id/sceneTransitionAnimation"
    android:text="click"
    android:background="@drawable/ic_launcher"
    android:layout_width="wrap_content"
    android:layout_height="80dp"
    android:layout_margin="5dp"
    android:clickable="true"
    android:focusable="true"
    tools:ignore="HardcodedText"
    />
```
在启动activity的时候要给makeSceneTransitionAnimation传入要过渡的元素，以及另一个界面接收的标识符:
```java
// 共享元素过渡动画
binding.sceneTransitionAnimation.setOnClickListener {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        val intent = Intent(requireContext(), AnimatorActivity::class.java)

        // iv是当前点击的图片  share字符串是第二个activity布局中设置的**transitionName**属性
        val optionsCompat = ActivityOptionsCompat.makeSceneTransitionAnimation(
            requireActivity(),
            binding.sceneTransitionAnimation,
            "sceneTransition")

        val bundle = optionsCompat.toBundle()
        // 多个过渡元素情况，记得import androidx.core.util.Pair
//                val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
//                    requireActivity(),
//                    Pair(binding.sceneTransitionAnimation, "sceneTransition")
//                ).toBundle()

        startActivity(intent, bundle)
    }else {
        showToast("当前系统版本不支持: ${Build.VERSION.SDK_INT}")
    }
}
```
下面是另一个界面的布局，注意里面的transitionName(这里要求v21):
```xml
<TextView
    android:id="@+id/sceneTransitionAnimation"
    android:transitionName="sceneTransition"
    android:text="SceneTransitionAnimation(click)"
    android:background="@drawable/ic_launcher"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:layout_constraintTop_toTopOf="parent"
    app:layout_constraintLeft_toLeftOf="parent"
    app:layout_constraintRight_toRightOf="parent"
    android:layout_margin="5dp"
    android:clickable="true"
    android:focusable="true"
    tools:ignore="HardcodedText"
    />
```
共享过渡也支持多个元素的过渡，可以看下上面代码注释的地方，AnimatorActivity返回的时候和上面类似，要注意使用finishAfterTransition返回，下面看下效果:

![SVID_20240110_152200_1.gif](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/ed3eb5c8793c4987ba9f8e69174782cf~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=541230&e=gif&f=70&b=fffdff)

## Transition过渡动画
实际上一节后面两个Activity过渡动画，都是通过TransitionManager实现的，前后两个activity是一个Scene，TransitionManager通过Transition将两者之间的过渡整合起来。

下面我们先从简单的出发，慢慢去体验下这个Transition过渡动画。

### Layout变化动画
上面两个不同的activity是一种场景，两个不同的布局(include引入)也能是不同的Scene，听起来很懵，下面看代码:
```xml
<FrameLayout
    android:id="@+id/contentPanel"
    android:layout_weight="1"
    android:layout_width="wrap_content"
    android:layout_height="0dp">

    <include
        android:id="@+id/includeLayout"
        layout="@layout/layout_scene_first"/>

</FrameLayout>
```

上面是layout里面的代码，还有两个不同的布局，用于include:

> res/layout/layout_scene_first.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:background="@color/gray">

    <TextView
        android:id="@+id/sceneFirst"
        android:text="布局变化前"
        android:layout_width="wrap_content"
        android:layout_height="match_parent"
        app:layout_constraintStart_toStartOf="parent"
        android:layout_margin="5dp"
        android:gravity="center_vertical"
        tools:ignore="HardcodedText"
        />

</androidx.constraintlayout.widget.ConstraintLayout>
```

> res/layout/layout_scene_second.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:background="@color/gray"
    >

    <TextView
        android:id="@+id/sceneSecond"
        android:text="布局变化后"
        android:background="@drawable/ic_launcher"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_margin="5dp"
        tools:ignore="HardcodedText"
        />

</androidx.constraintlayout.widget.ConstraintLayout>
```

两个布局区别不大，就文字和宽度不一样，不过还是很容易肉眼区分的，切换两个layout的代码和触发过渡动画的代码也比较简单:
```java
// 布局变化动画
val firstScene = Scene.getSceneForLayout(binding.contentPanel,
    R.layout.layout_scene_first, requireContext())

val secondScene = Scene.getSceneForLayout(binding.contentPanel,
    R.layout.layout_scene_second, requireContext())

// ViewBinding无法拿到第二个layout中的id
// binding.includeLayout.sceneFirst.setOnClickListener {
var isFirst = true
binding.contentPanel.setOnClickListener {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        TransitionManager.go(if(isFirst) secondScene else firstScene, Slide(Gravity.START))
        isFirst = !isFirst
    }
}
```
定义两个Scene，然后通过TransitionManager的go方法执行就行了，比较坑的就是在viewBindding里面拿不到布局-_-||。

我这加了个Slide的过渡效果，这个上面有用到过，实际它就是一个Transition，这里先看下效果，Transition后面我们会自定义它，那时候就能理解了:

![SVID_20240110_152647_1.gif](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/da445317482844089b3a5b7d6c6146be~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=221013&e=gif&f=66&b=fffdff)

### Layout变化动画(子view属性)
上面我们是通过两个include的布局创建两个Scene来实现过渡效果的，实际上我们更多需要的是view属性变换后的过渡动画，听起来不就是属性动画么，可是还是有区别的，属性动画是我们自己去操作view，而这个过渡动画是我们修改view属性后，两个状态之间的过渡。

简单理解就是，系统会创建两个Scene，分别对应属性变换前和属性变换后，中间根据Transition的设定进行变化，产生过渡效果，说这么多，还是实操好理解:

```xml
<LinearLayout
    android:id="@+id/container"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="16dp"
    android:orientation="vertical"
    >

    <TextView
        android:id="@+id/sceneTransitionSystem"
        android:text="click"
        android:background="@drawable/ic_launcher"
        android:layout_width="wrap_content"
        android:layout_height="80dp"
        android:layout_margin="5dp"
        android:clickable="true"
        android:focusable="true"
        tools:ignore="HardcodedText"
    />
    
</LinearLayout>
```
上面就是一个TextView，放在一个id名为container的LinearLayout里面，下面是操作它变化的代码:
```java
// 布局变化过渡动画(子view属性)
var isFirst2 = true
binding.sceneTransitionSystem.setOnClickListener {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        // 延迟启动root的变化
        TransitionManager.beginDelayedTransition(binding.root, AutoTransition())
        // 修改root内子View，设置变化
        binding.sceneTransitionSystem.layoutParams.apply {
            width = if (isFirst2) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT
        }.also {
            binding.sceneTransitionSystem.layoutParams = it
        }
        isFirst2 = !isFirst2
    }
}
```
代码比较简单，通过TransitionManager的beginDelayedTransition方法，会对要产生变化的View进行监听，随后我们改变它的子view属性，就能触发Transition了。

这里的beginDelayedTransition方法可以不传第二个Transition参数，默认是AutoTransition，还是挺炫酷的:

![SVID_20240110_152807_1.gif](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/a07e653be94943bf886bb98e804ffc0a~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=274591&e=gif&f=56&b=fffdff)

### 过渡动画: TransitionSet
如果不看Transition相关的内容，我还不知道Android的res文件夹还能自动生成一个transition目录:

![image.png](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/9bd24c3d077c48d99282405b8a4e6912~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1257&h=733&s=60565&e=png&b=3d4042)

而且里面还支持这么多的标签:

![image.png](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/96712e3a8a7a4776b19e96b9acc77894~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1092&h=490&s=59665&e=png&b=454749)

不过这些都是Android已经定义好的transition，按需求使用就可以了，下面用前面的Layout变化动画来实践下:
```java
// 过渡动画: TransitionSet
// 继续用上面的布局变化动画，从XML中读取transition
val firstScene2 = Scene.getSceneForLayout(binding.contentPanelSet,
    R.layout.layout_scene_first, requireContext())
val secondScene2 = Scene.getSceneForLayout(binding.contentPanelSet,
    R.layout.layout_scene_second, requireContext())

val transitionSet =
    TransitionInflater.from(requireContext()).inflateTransition(R.transition.transition_set)
// 使用代码创建
//        TransitionSet().apply {
//            // 为目标视图滑动添加动画效果
//            addTransition(changeScroll())
//            // 为目标视图布局边界的变化添加动画效果
//            addTransition(ChangeBounds())
//            // 为目标视图裁剪边界的变化添加动画效果
//            addTransition(changeClipBounds())
//            // 为目标视图缩放和旋转方面的变化添加动画效果
//            addTransition(changeTransform())
//            // 为目标图片尺寸和缩放方面的变化添加动画效果
//            addTransition(ChangeImageTransform())
//
//            // 继承Visibility类，
//            addTransition(Slide())
//            addTransition(Explode())
//            addTransition(Fade(Fade.MODE_IN))
//        }

var isFirst3 = true
binding.contentPanelSet.setOnClickListener {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        TransitionManager.go(if(isFirst3) secondScene2 else firstScene2, transitionSet)
        isFirst3 = !isFirst3
    }
}
```
代码和布局都没什么好解释的了，和上面一样，重点就是通过TransitionInflater创建了一个transitionSet:

> res/transition/transition_set.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<transitionSet xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="300"
    android:transitionOrdering="together">

    <changeScroll />

    <changeImageTransform />

    <changeBounds />

    <fade />

</transitionSet>
```

我们加了几种效果，一些常用的系统transition解释，可以看下我在用代码创建TransitionSet的注释:
```java
// 使用代码创建
TransitionSet().apply {
    // 为目标视图滑动添加动画效果
    addTransition(changeScroll())
    // 为目标视图布局边界的变化添加动画效果
    addTransition(ChangeBounds())
    // 为目标视图裁剪边界的变化添加动画效果
    addTransition(changeClipBounds())
    // 为目标视图缩放和旋转方面的变化添加动画效果
    addTransition(changeTransform())
    // 为目标图片尺寸和缩放方面的变化添加动画效果
    addTransition(ChangeImageTransform())

    // 继承Visibility类，
    addTransition(Slide())
    addTransition(Explode())
    addTransition(Fade(Fade.MODE_IN))
}
```
实际都是transition，注重它的效果就行吧，深入源码我觉得看有没有多余时间吧，下面看下效果:

![SVID_20240110_152932_1.gif](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/4896f4e4cd8b4b6db92bcd001f7eec4b~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=208413&e=gif&f=59&b=fffdff)

### 自定义Transition
看了上面那么多Transition，不动手自己练几个，都不好意思说我这篇文章是实践的，下面就是手撕环节了。
```java
import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.transition.Transition
import android.transition.TransitionValues
import android.view.ViewGroup

class CustomTransition : Transition() {
    private val rightValue = "rightValue"

    override fun captureStartValues(transitionValues: TransitionValues) {
        // 获得view，可以拿到起始属性值，保存到transitionValues.values去
        val view = transitionValues.view
        transitionValues.values[rightValue] = view.right
    }

    override fun captureEndValues(transitionValues: TransitionValues) {
        // 和上面一样，保持结束值
        val view = transitionValues.view
        transitionValues.values[rightValue] = view.right
    }

    override fun createAnimator(
        sceneRoot: ViewGroup,
        startValues: TransitionValues,
        endValues: TransitionValues
    ): Animator {

        val view = endValues.view
        val start = startValues.values[rightValue] as Int
        val end = endValues.values[rightValue] as Int

        // 将自定义动画应用于目标视图
        val animatorSet = AnimatorSet()
        val animatorSetOther = AnimatorSet()
        animatorSetOther.playSequentially(
            ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.5f),
            ObjectAnimator.ofFloat(view, "scaleY", 1.5f, 1f)
        )
        animatorSet.playTogether(
            ObjectAnimator.ofInt(view, "right", start, end),
            animatorSetOther
        )
        return animatorSet
    }
}
```
自定义一个Transition需要实现三个方法，分别是captureStartValues和captureEndValues方法用于捕获初始及结束的变化值，在createAnimator方法里面实现变化效果。

看到createAnimator方法，以及它要求的返回值Animator，它的神秘面纱就被扯下来了，就是这么简单。。。

我们自定义的这个CustomTransition效果解释下，就是在view的right属性变化后，给它平滑的扩张过去，并且加了个Y轴上的先放大后缩小效果，我觉得还挺nice的，下面看下效果图:

![SVID_20240110_153051_1.gif](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/789e8345247c4809ac5cd7b9c9c8699e~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=441638&e=gif&f=63&b=fffdff)

### 自定义Visibility
虽然我们自定义了Transition，但是我们在Activity过渡中用到的Slide、Explode、Fade却是继承的Visibility，Visibility继承了Transition，会在View修改Visibility的时候触发。

自定义Visibility比自定义Transition复杂一些，下面看下代码:
```java
import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.transition.TransitionValues
import android.transition.Visibility
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.animation.addListener

class CustomVisibility: Visibility() {

    // 用于控制view出现时的外观变化，类似transition功能
    override fun onAppear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues,
        endValues: TransitionValues
    ): Animator {
        return super.onAppear(sceneRoot, view, startValues, endValues)
    }

    // 提供了更直接的方式来访问视图的可见性状态，未实现的话，会继续调用上面onAppear方法
    override fun onAppear(
        sceneRoot: ViewGroup,
        startValues: TransitionValues?,
        startVisibility: Int,
        endValues: TransitionValues,
        endVisibility: Int
    ): Animator {
        // 获取要操作的View，注意startValues可能为null
        val view = endValues.view

        // 加日志可以看到受影响的view，可根据ID排除
         Log.d("TAG", "onAppear: view = $view")
        // view = android.view.View{... #1020030 android:id/navigationBarBackground}
        // 比如: excludeTarget(android.R.id.navigationBarBackground, true)

        // 将自定义动画应用于目标视图
        return AnimatorSet().apply {
            duration = 2000
            when(startVisibility) {
                // 从GONE变为显示
                View.GONE -> playTogether(
                    ObjectAnimator.ofFloat(view, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(view, "scaleX", 0f, 1f),
                    ObjectAnimator.ofFloat(view, "scaleY", 0f, 1f)
                )
                // 从INVISIBLE变为显示
                View.INVISIBLE -> playTogether(
                    ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
                )
                // 当view被添时，startVisibility=-1，不加旋转
                else -> {
                    playTogether(
                        ObjectAnimator.ofFloat(view, "alpha", 0f, 1f),
                        ObjectAnimator.ofFloat(view, "scaleX", 0f, 1f),
                        ObjectAnimator.ofFloat(view, "scaleY", 0f, 1f),
                    )
                }
            }
        }
    }

    override fun onDisappear(
        sceneRoot: ViewGroup,
        startValues: TransitionValues,
        startVisibility: Int,
        endValues: TransitionValues?,
        endVisibility: Int
    ): Animator {
        // 获取要操作的View，注意endValues可能为null
        val view = endValues?.view ?: startValues.view

        // 这里要阻止View直接变成endVisibility，在动画结束后再设置
        // 通过日志可以看出这里设置visibility不会再触发onAppear、onDisappear
        Log.d("TAG", "onDisappear: view = $view")
        view.visibility = startVisibility

        return AnimatorSet().apply {
            duration = 2000
            addListener(onEnd = {
                view.visibility = endVisibility
            })
            when(endVisibility) {
                // 最终变为GONE
                View.GONE -> playTogether(
                    ObjectAnimator.ofFloat(view, "alpha", 1f, 0f),
                    ObjectAnimator.ofFloat(view, "scaleX", 1f, 0f),
                    ObjectAnimator.ofFloat(view, "scaleY", 1f, 0f),
                )
                // 最终变为INVISIBLE
                View.INVISIBLE -> playTogether(
                    ObjectAnimator.ofFloat(view, "alpha", 1f, 0f),
                )
                // 当view被remove的时候，endVisibility=-1，不加旋转
                else -> {
                    playTogether(
                        ObjectAnimator.ofFloat(view, "alpha", 1f, 0f),
                        ObjectAnimator.ofFloat(view, "scaleX", 1f, 0f),
                        ObjectAnimator.ofFloat(view, "scaleY", 1f, 0f)
                    )
                }
            }
        }
    }
}
```
实际我们只要实现其中的onAppear和onDisappear方法即可，参数和Transition类似，只不过多了起始的可见性startVisibility和endVisibility，我们可以根据变化前后的状态做一些操作。

我们在前面的ActivityOptions过渡动画那看下效果，不传值的话用我们自定义的过渡动画:
```java
// 根据传入信息配置过渡动画
when(intent.getStringExtra("type")) {
    "explode" -> Explode()
    "slide" -> Slide()
    "fade" -> Fade()
    else -> CustomVisibility()
}
```

在代码里点击过去，没什么区别:
```java
// 过渡动画Activity: CustomVisibility
binding.customVisibility.setOnClickListener {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        val intent = Intent(requireContext(), SceneTransitionActivity::class.java)

        // 传入过渡动画类型
        intent.putExtra("type", "CustomVisibility")
        val optionsCompat =
            ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity())

        startActivity(intent, optionsCompat.toBundle())
    }else {
        showToast("当前系统版本不支持: ${Build.VERSION.SDK_INT}")
    }
}
```
下面是效果图:

![SVID_20240110_153219_1.gif](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/34041ce1116648459b1e31847ca56f24~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=455545&e=gif&f=114&b=fffdff)

有个神奇的地方就是标题栏、状态栏、导航栏、布局内容都会调用onAppear方法，所以这里onAppear会调用多次，可以通过excludeTarget排除。

另外一个有意思的是Visibility的注释里面提到了:
> Visibility is determined not just by the View.setVisibility(int) state of views, but also whether views exist in the current view hierarchy.

也就是说一个view被添加到布局和从布局移除的时候也会触发，这里没体现，而且在Activity过渡动画中，Visibility的变化也没体现，我觉得还是得再搞个例子看看。

### 自定义Visibility(Layout)
前面说到了在Activity的过渡动画中，Visibility的一些性质并没有被体现出来，下面我们就用上面“Layout变化动画(子view属性)”的例子改一改，来体现这个效果。
```xml
<RadioGroup
    android:id="@+id/customVisibilityType"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content">

    <RadioButton
        android:id="@+id/visiable"
        android:checked="true"
        android:text="Visiable"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        tools:ignore="HardcodedText"
        />

    <RadioButton
        android:id="@+id/invisible"
        android:text="Invisible"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        tools:ignore="HardcodedText"
        />

    <RadioButton
        android:id="@+id/gone"
        android:text="Gone"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        tools:ignore="HardcodedText"
        />

</RadioGroup>

<RadioGroup
    android:id="@+id/customVisibilityAdd"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content">

    <RadioButton
        android:id="@+id/add"
        android:text="Add"
        android:checked="true"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        tools:ignore="HardcodedText"
        />

    <RadioButton
        android:id="@+id/remove"
        android:text="Remove"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        tools:ignore="HardcodedText"
        />

</RadioGroup>

<LinearLayout
    android:id="@+id/visibilityContainer"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:orientation="horizontal">

    <TextView
        android:id="@+id/customVisibilityText"
        android:text="click"
        android:background="@drawable/ic_launcher"
        android:layout_width="wrap_content"
        android:layout_height="80dp"
        android:layout_margin="5dp"
        android:clickable="true"
        android:focusable="true"
        tools:ignore="HardcodedText"
        />

</LinearLayout>
```
布局里添加了五个单选按钮(换了一行，不然太挤了)来操控下面TextView的状态，因为View.GONE会让TextView消失，页面会变化，这里加了个LinearLayout给它固定下位置。

代码里面根据radioGroup的变化操作textView的属性就行，记得beginDelayedTransition传入自定义的CustomVisibility就ok:
```java
// 过渡动画Layout: CustomVisibility
val textView = binding.customVisibilityText
binding.customVisibilityType.setOnCheckedChangeListener { _, checkedId ->
    // 设置过渡动画
    TransitionManager.beginDelayedTransition(binding.root, CustomVisibility())
    // 触发
    textView.visibility = when(checkedId) {
        R.id.visiable -> View.VISIBLE
        R.id.invisible -> View.INVISIBLE
        R.id.gone -> View.GONE
        else -> View.VISIBLE
    }
}
binding.customVisibilityAdd.setOnCheckedChangeListener { _, checkedId ->
    // 设置过渡动画
    TransitionManager.beginDelayedTransition(binding.root, CustomVisibility())
    // 触发
    when(checkedId) {
        R.id.add -> {
            if (!binding.visibilityContainer.contains(textView)) {
                // 添加并不会触发Visibility的onAppear
                textView.visibility = View.VISIBLE
                binding.visibilityContainer.addView(textView)
            }
        }
        R.id.remove -> {
            if (binding.visibilityContainer.contains(textView)) {
                // 可以触发onDisappear，但是无法出现过渡效果
                binding.visibilityContainer.removeView(textView)
            }
        }
        else -> {}
    }
}
```
下面是实际效果:

![SVID_20240110_191944_1.gif](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/be91d77c237144dfa60e9b62a0dde4aa~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=356&h=754&s=811859&e=gif&f=203&b=fffdff)

有点被Visibility的注释唬住了，view的add和remove只有remove会触发onDisappear，而add方法不会触发onAppear，而且这两个方法的visibility会从-1变化，不是visiable、invisible、gone中的任何一种，有点坑。

而且啊，这里动画不能太复杂，我一开始加了些旋转动画，结果有一定几率没反应，还有，这里的动画不要点太快，点快了也有一定几率没反应，特别是onAppear方法，好鸡肋啊。。。(当然也有可能我的用法不对)

## Demo
源码在我用来练手的仓库里，可以参考下:

[SceneTransitionTestDemo.kt](https://github.com/silencefly96/fundark/blob/master/module_tech/src/main/java/com/silencefly96/module_tech/tech/demo/SceneTransitionTestDemo.kt)

## 参考文章
[Android过渡动画，发现掘金小秘密](https://juejin.cn/post/6850037271714856968)

[Android技术分享| Activity 过渡动画 — 让切换更加炫酷](https://juejin.cn/post/7140108564550385678)

[android中的Transition动画的使用](https://juejin.cn/post/6844904065948712974)

## 小结
这篇文章主要对Android里面过渡动画做了些实践，给出Demo和Gif效果图，包含了Activity过渡效果和布局的过渡效果，还自定义了下Transition和Visibility，又是知识满满啊！