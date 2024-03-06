# Glide源码解析(3) Glide的扩展

## 前言
前面一篇文章中提到了GeneratedAppGlideModule这个类，在Glide的创建方法initializeGlide中，还有manifestModules。这些都是Glide中的扩展模块，这篇文章就来分析下。

如果对Glide的扩展没啥了解的话，可以先看下官方文档说明，下面是中文版的链接:
> 官方文档: https://muyangmin.github.io/glide-docs-cn/doc/generatedapi.html

这里大概总结下Glide扩展的使用情形:
- 使用一个或更多集成库
- 修改 Glide 的配置(configuration)（磁盘缓存大小/位置，内存缓存大小等）
- 扩展 Glide 的API。

下面我们就来体会下这些情形是如何实现的。

## GlideModule

### GlideModule创建
首先，要扩展Glide需要创建新的GlideModule。有两种GlideModule，对于应用程序来说是AppGlideModule，对于library则是LibraryGlideModule，而AppGlideModule继承了LibraryGlideModule。

下面我们先来定义一个GlideModule:
```
  // 可自定定义模块的名称
  @GlideModule(glideName = "GlideAppExt")
  public class MyGlideModule extends AppGlideModule {

    @Override
    public void applyOptions(@NonNull Context context,
                             @NonNull GlideBuilder builder) {
        super.applyOptions(context, builder);
        // 内存缓存相关,默认是20m
        int memoryCacheSizeBytes = 1024 * 1024 * 20; // 20mb
        builder.setMemoryCache(new LruResourceCache(memoryCacheSizeBytes));
    }

    @Override
    public boolean isManifestParsingEnabled() {
        // 兼容V3版本，V3版本在manifest内用meta-data指定，而不是注解生成
        return false;
    }

    @Override
    public void registerComponents(@NonNull @NotNull Context context,
                                   @NonNull @NotNull Glide glide,
                                   @NonNull @NotNull Registry registry) {
        super.registerComponents(context, glide, registry);
    }
  }
```
这里对内存缓存做了个自定义操作，限制内存缓存的大小是20MB。

当加上GlideModule注解后，编译时会自动生成GeneratedAppGlideModuleImpl类，下面是注解自动生成的代码:
```
@SuppressWarnings("deprecation")
final class GeneratedAppGlideModuleImpl extends GeneratedAppGlideModule {
  private final MyGlideModule appGlideModule;

  public GeneratedAppGlideModuleImpl(Context context) {
    appGlideModule = new MyGlideModule();
  }

  @Override
  public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
    appGlideModule.applyOptions(context, builder);
  }

  @Override
  public void registerComponents(@NonNull Context context, @NonNull Glide glide,
      @NonNull Registry registry) {
    appGlideModule.registerComponents(context, glide, registry);
  }

  @Override
  public boolean isManifestParsingEnabled() {
    return appGlideModule.isManifestParsingEnabled();
  }

  @Override
  @NonNull
  public Set<Class<?>> getExcludedModuleClasses() {
    return Collections.emptySet();
  }

  @Override
  @NonNull
  GeneratedRequestManagerFactory getRequestManagerFactory() {
    // 这个也是自动生成的
    return new GeneratedRequestManagerFactory();
  }
}
```
实际就是实际就是一个代理类，里面通过创建我们自定义的MyGlideModule对象，去实现一些功能。

那我们这个生成的的GeneratedAppGlideModuleImpl会在哪里用到呢？回顾下第一篇文章Glide的创建过程:
```
  @NonNull
  public static Glide get(@NonNull Context context) {
    if (glide == null) {
    
      // 这里通过调用getAnnotationGeneratedGlideModules方法去获取GeneratedAppGlideModule
      GeneratedAppGlideModule annotationGeneratedModule =
          getAnnotationGeneratedGlideModules(context.getApplicationContext());
      synchronized (Glide.class) {
        if (glide == null) {
          checkAndInitializeGlide(context, annotationGeneratedModule);
        }
      }
    }
    return glide;
  }
```
创建Glide的时候会调用getAnnotationGeneratedGlideModules方法去查找GeneratedAppGlideModule:
```
  @Nullable
  @SuppressWarnings({"unchecked", "TryWithIdenticalCatches", "PMD.UnusedFormalParameter"})
  private static GeneratedAppGlideModule getAnnotationGeneratedGlideModules(Context context) {
    GeneratedAppGlideModule result = null;
    
    try {
      // 通过反射创建的
      Class<GeneratedAppGlideModule> clazz =
          (Class<GeneratedAppGlideModule>)
              Class.forName("com.bumptech.glide.GeneratedAppGlideModuleImpl");
      result =
          clazz.getDeclaredConstructor(Context.class).newInstance(context.getApplicationContext());
    } catch (ClassNotFoundException e) {
      // ...
      // These exceptions can't be squashed across all versions of Android.
    } catch (InstantiationException e) {
      throwIncorrectGlideModule(e);
    } catch (IllegalAccessException e) {
      throwIncorrectGlideModule(e);
    } catch (NoSuchMethodException e) {
      throwIncorrectGlideModule(e);
    } catch (InvocationTargetException e) {
      throwIncorrectGlideModule(e);
    }
    return result;
  }
```
里面会通过反射创建GeneratedAppGlideModuleImpl对象，并强制转换成GeneratedAppGlideModule。当然，如果我们没有自定义GlideModule，那这个反射是生成不了的。

小结下，也就是说当我们新建了一个MyGlideModule类，并给它加上AppGlideModule注解，它就会生成一个GeneratedAppGlideModuleImpl类，当Glide创建的时候，就会通过反射去创建这个类的对象，而这个类中又会创建我们自定义的MyGlideModule对象，并且把所有功能交给它处理。听起来有点复杂，但是对于开发来说却是简单了，加注解就行了。

### 问题解决
说起来很简单，参考别人的文章，可是我的自动生成的GeneratedAppGlideModuleImpl一直出不来，出现了几个问题，这里提一下:

- 要添加注解处理器:
  ```
    annotationProcessor 'com.github.bumptech.glide:compiler:4.5.0'
    // kotlin版本使用kapt，记得添加'kotlin-kapt'插件
    kapt 'com.github.bumptech.glide:compiler:4.11.0'
  ```
- kapt报错。这里好像需要JDK11;
  > 这个也算我画蛇添足了，把项目换到JDK8了，后面kapt就不行了，然后还忘了，最后还是在StackOverflow上找到个回答，突然想起来，改回去就好了。
- “build\generated\ap_generated_sources\debug\out”目录没内容。
  其实在“build\generated\source\kapt\debug\com\bumptech\glide”目录。
  > 这里也卡了我一会，后面双击shift直接搜的，发现kapt生成的目录不一样了

![image.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/e11d71060b124f6d9a8b8c2fe25589fb~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=490&h=741&s=47370&e=png&b=3c3f41)

### Glide扩展的使用
到这，我们实际上就可以使用自定义的扩展了，使用方法如下:
```
  GlideAppExt.with(this).load(url).into(imgView);
```
好像没什么区别，但是别忘了我们在自定义的MyGlideModule中自定义了内存缓存，给它限制到了20MB。
```
  // 可自定定义模块的名称
  @GlideModule(glideName = "GlideAppExt")
  public class MyGlideModule extends AppGlideModule {

    @Override
    public void applyOptions(@NonNull Context context,
                             @NonNull GlideBuilder builder) {
        super.applyOptions(context, builder);
        // 内存缓存相关,默认是20m
        int memoryCacheSizeBytes = 1024 * 1024 * 20; // 20mb
        builder.setMemoryCache(new LruResourceCache(memoryCacheSizeBytes));
    }
    // ...
  }
```
这里GlideAppExt是我们在GlideModule注解传的值，同时它也是一个自动生成的类:
```
public final class GlideAppExt {
  private GlideAppExt() {}

  @Nullable
  public static File getPhotoCacheDir(@NonNull Context context) {
    return Glide.getPhotoCacheDir(context);
  }

  @Nullable
  public static File getPhotoCacheDir(@NonNull Context context, @NonNull String string) {
    return Glide.getPhotoCacheDir(context, string);
  }

  @NonNull
  public static Glide get(@NonNull Context context) {
    return Glide.get(context);
  }

  @Deprecated
  @VisibleForTesting
  @SuppressLint("VisibleForTests")
  public static void init(Glide glide) {
    Glide.init(glide);
  }

  @VisibleForTesting
  @SuppressLint("VisibleForTests")
  public static void init(@NonNull Context context, @NonNull GlideBuilder builder) {
    Glide.init(context, builder);
  }

  @VisibleForTesting
  @SuppressLint("VisibleForTests")
  public static void tearDown() {
    Glide.tearDown();
  }

  @NonNull
  public static GlideRequests with(@NonNull Context context) {
    return (GlideRequests) Glide.with(context);
  }

  @NonNull
  public static GlideRequests with(@NonNull Activity activity) {
    return (GlideRequests) Glide.with(activity);
  }

  @NonNull
  public static GlideRequests with(@NonNull FragmentActivity activity) {
    return (GlideRequests) Glide.with(activity);
  }

  @NonNull
  public static GlideRequests with(@NonNull Fragment fragment) {
    return (GlideRequests) Glide.with(fragment);
  }

  @Deprecated
  @NonNull
  public static GlideRequests with(@NonNull android.app.Fragment fragment) {
    return (GlideRequests) Glide.with(fragment);
  }

  @NonNull
  public static GlideRequests with(@NonNull View view) {
    return (GlideRequests) Glide.with(view);
  }
}
```
差不多就是Glide的一个入口吧，和Glide公开的方法差不多，里面还都是调用Glide去实现的，但是还是有不同的，需要注意下！

这里的with方法，调用Glide#with后，把RequestManager强制转换成了GlideRequests，GlideRequests继承了RequestManager，简单看下里面的代码:
```
  public class GlideRequests extends RequestManager {
    public GlideRequests(@NonNull Glide glide, @NonNull Lifecycle lifecycle,
        @NonNull RequestManagerTreeNode treeNode, @NonNull Context context) {
      super(glide, lifecycle, treeNode, context);
    }
  
    @Override
    @CheckResult
    @NonNull
    public <ResourceType> GlideRequest<ResourceType> as(@NonNull Class<ResourceType> resourceClass) {
      // 把RequestManager中的RequestBuilder换成了GlideRequest，GlideRequest同样继承了RequestBuilder
      return new GlideRequest<>(glide, this, resourceClass, context);
    }
    
    //...
  }
```
也就是说使用GlideAppExt的时候，GlideRequests会扩展RequestManager，而GlideRequest会扩展RequestBuilder。

要问这两个类是干嘛的，我只能暂时说这两个类也是随着MyGlideModule加注解后自动生成的，暂时看不出什么区别，后面再看咯。

还有一个问题，为什么我们在MyGlideModule中自定义了内存缓存能生效呢？实际第一篇的文章中已经讲到了:
```
  // Glide类中
  private static void initializeGlide(
    //...
    
    for (com.bumptech.glide.module.GlideModule module : manifestModules) {
      module.applyOptions(applicationContext, builder);
    }
    if (annotationGeneratedModule != null) {
      annotationGeneratedModule.applyOptions(applicationContext, builder);
    }
    //...
  }
```
在Glide对象创建的时候，我们的annotationGeneratedModule的applyOptions方法会被调用，然后中转到调用MyGlideModule的applyOptions方法:
```
    @Override
    public void applyOptions(@NonNull Context context,
                             @NonNull GlideBuilder builder) {
        super.applyOptions(context, builder);
        //内存缓存相关,默认是20m
        int memoryCacheSizeBytes = 1024 * 1024 * 20; // 20mb
        builder.setMemoryCache(new LruResourceCache(memoryCacheSizeBytes));
    }
```
在GlideBuilder的build方法中，有这么一段，因为memoryCache已经设置了，所以就生效了:
```
  @NonNull
  Glide build(@NonNull Context context) {
    // ...
    if (memoryCache == null) {
      memoryCache = new LruResourceCache(memorySizeCalculator.getMemoryCacheSize());
    }
    
    // ...
  }
```

## GlideExtension
上面说到了GlideModule以及它的使用，它可以让我们使用一个或更多集成库，前言也说到了Glide扩展还有修改Glide的配置、扩展Glide的API的功能，这就是GlideExtension的功能了。

GlideExtension支持两种扩展方式:
- GlideOption: 为 RequestOptions添加或修改选项。
- GlideType: 添加对新的资源类型的支持(GIF，SVG等等)。

### GlideExtension的创建
#### 创建
我们先来创建一个GlideExtension，再来分析它的源码:
```
  // 可在Application和Library中被扩展，被GlideExtension注解的类应以工具类的思维编写。
  @GlideExtension
  public class MyAppGlideExtension {
      // 被注解的类可以含有静态变量，可以引用其他的类或对象。
      private static final int MINI_THUMB_SIZE = 100;
  
      // 这种类应该有一个私有的、空的构造方法，应为 final 类型，并且仅包含静态方法。
      private MyAppGlideExtension() {}
  
      @NonNull
      @GlideOption
      public static BaseRequestOptions<?> miniThumb(BaseRequestOptions<?> options) {
          // 给option增加默认设置，居中且大小为100
          return options
                  .fitCenter()
                  .override(MINI_THUMB_SIZE);
      }
  }
```
经过编译后，自动生成的GlideOptions类中就会为我们添加方法:
```
  public final class GlideOptions extends RequestOptions implements Cloneable {
    // ...
    @CheckResult
    @NonNull
    public GlideOptions miniThumb() {
      return (GlideOptions) MyAppGlideExtension.miniThumb(this);
    }
  }
```
然后就可以像下面使用了:
```
  GlideAppExt.with(context)
      .load(url)
      .miniThumb()
      .into(imgView);
```
如果不使用GlideAppExt，而是通过Glide去调用是会有问题的:
![image.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/ae7a039228de48ae8cd4aa8e61c2a2d9~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=869&h=375&s=40043&e=png&b=2c2c2c)

如果只看表象而不探讨源码的话，咱们这文章就不改叫源码解析了。下面我们分析下为什么。

#### 原理
如果说这两份代码有何区别的话，其实前面Glide扩展的使用时已经有说到一点了，那就是两个load方法返回的对象不一样:
```
  // 使用Glide时
  public RequestBuilder<Drawable> load(@Nullable String string) {
    return asDrawable().load(string);
  }
  
  // 使用GlideAppExt时
  public GlideRequest<Drawable> load(@Nullable String string) {
    return (GlideRequest<Drawable>) super.load(string);
  }
```
也就是说实际是GlideRequest里面添加了我们的miniThumb，看下源码确实有:
```
  /**
   * @see MyAppGlideExtension#miniThumb(BaseRequestOptions)
   */
  @SuppressWarnings("unchecked")
  @CheckResult
  @NonNull
  public GlideRequest<TranscodeType> miniThumb() {
    return (GlideRequest<TranscodeType>) MyAppGlideExtension.miniThumb(this);
  }
```
看下它的Structure，除了继承的方法，就多了这一个方法，也就是说GlideRequest和RequestBuilder区别就是这么一个方法么？也就是说这就是扩展结果。
![image.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/5404d5c79b504b0b96430c471f9e1734~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=584&h=242&s=23137&e=png&b=3c3f41)

### GlideOption
#### 使用
其实上面已经把GlideOption讲解的挺清楚了，这里再多提一下GlideOption注释的方法也是可以添加参数的，看下面代码:
```
    @NonNull
    @GlideOption
    public static BaseRequestOptions<?> miniThumbSize(BaseRequestOptions<?> options, int size) {
        // 通过传递进来的size设置图片大小
        return options
                .fitCenter()
                .override(size);
    }
```
在之前的MyAppGlideExtension中加了个方法，编译一下，再来看下GlideRequest里面，果然多了个方法:
```
  @CheckResult
  @NonNull
  public GlideRequest<TranscodeType> miniThumbSize(int size) {
    return (GlideRequest<TranscodeType>) MyAppGlideExtension.miniThumbSize(this, size);
  }
```
其实到这我还有个关于GlideOption的疑问，既然我们加的GlideOption注解方法是在GlideRequest生效的，那为什么还要修改自动生成的GlideOptions类呢？

#### 探究
想到这里，我看了下GlideAppExt.with(context)返回对象的GlideRequests类中，添加了GlideOption注解它会有什么变化，果然在它的代码中我发现了问题:
```
  @Override
  protected void setRequestOptions(@NonNull RequestOptions toSet) {
    if (toSet instanceof com.silencefly96.fundark.GlideOptions) {
      super.setRequestOptions(toSet);
    } else {
      super.setRequestOptions(new com.silencefly96.fundark.GlideOptions().apply(toSet));
    }
  }
```
也就是说，这里强制把RequestOptions换成了我们自动生成的GlideOptions，那么只要调用设置(初始化也算)，GlideRequests(RequestManager)中保存的RequestOptions，已经是我们自己的GlideOptions:
```
  @GuardedBy("this")
  private RequestOptions requestOptions;
```
这有什么作用呢？我是觉得好像没啥用，除非能加字段，只加方法的话很鸡肋吧。

小结下，加上GlideOption注解，会有两个结果: 
- 一个是把RequestBuilder变成GlideRequest，并增加我们的扩展方法，使得在load(url)后面，我们就能调用自定义的扩展方法。
- 另一个是把RequestOptions变成GlideOptions，并增加我们的扩展方法，并且会使GlideRequests(RequestManager)中持有的RequestOptions变成GlideOptions类型。

第一个方便了我们使用，第二个改变了保存的变量的储存类型。

### GlideType
> ps: 下面GlideType是按官网例子写的，实际上并不太行，如果真的需要扩展的话可以看下一大节。

看完GlideOption，我们来看下GlideType，它能为Glide添加对新的资源类型的支持，包括指定默认选项。

#### 使用
下面我们就按官方的例子，添加对 GIF 的支持，还是在我们的MyAppGlideExtension添加代码:
```
  @GlideExtension
  public class MyAppGlideExtension {
    // ...
    private static final RequestOptions DECODE_TYPE_GIF
            = RequestOptions.decodeTypeOf(GifDrawable.class).lock();
    
    // 网上博文有问题。。。按下面代码吧！不要丢了NonNull，有return值，方法不能和已有方法重名
    @NonNull
    @GlideType(GifDrawable.class)
    public static RequestBuilder<GifDrawable> as2Gif(RequestBuilder<GifDrawable> requestBuilder) {
        return requestBuilder
                .transition(new DrawableTransitionOptions())
                .apply(DECODE_TYPE_GIF);
    }
  }
```
上面GlideOption注解会对GlideOptions和GlideRequest(RequestBuilder)做修改，而这里GlideType注解，则是修改GlideRequests(RequestManager)，编译一下看代码:
```
  /**
   * @see MyAppGlideExtension#as2Gif(RequestBuilder)
   */
  @NonNull
  @CheckResult
  public GlideRequest<GifDrawable> as2Gif() {
    // 注意asGif方法已经有了
    return (GlideRequest<GifDrawable>) MyAppGlideExtension.as2Gif(this.as(GifDrawable.class));
  }
```
看下GlideRequests的Structure，也是单独多了一个as2Gif方法:
![image.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/4d13a9810a5143c2a4df8fde45981dc0~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=840&h=247&s=35756&e=png&b=3d4245)

接下来就可以使用了:
```
  GlideApp.with(fragment)
    .as2Gif()
    .load(url)
    .into(imageView);
```
#### 部分原理
前面为Glide增加了对Gif格式的支持，我们接下来简单看下里面的原理。

在瞄一眼这个方法:
```
    private static final RequestOptions DECODE_TYPE_GIF
            = RequestOptions.decodeTypeOf(GifDrawable.class).lock();
            
    @NonNull
    @GlideType(GifDrawable.class)
    public static RequestBuilder<GifDrawable> as2Gif(RequestBuilder<GifDrawable> requestBuilder) {
        return requestBuilder
                .transition(new DrawableTransitionOptions())
                .apply(DECODE_TYPE_GIF);
    }
```
这里实际分了三步: decodeTypeOf、transition、apply。

1. 第一步，通过RequestOptions.decodeTypeOf方法创建了一个RequestOptions:
  ```
    @NonNull
    @CheckResult
    public static RequestOptions decodeTypeOf(@NonNull Class<?> resourceClass) {
      // 创建一个新的RequestOptions
      return new RequestOptions().decode(resourceClass);
    }
    
    @NonNull
    @CheckResult
    public T decode(@NonNull Class<?> resourceClass) {
      if (isAutoCloneEnabled) {
        // 复制一份再decode
        return clone().decode(resourceClass);
      }
  
      // 保存当前class
      this.resourceClass = Preconditions.checkNotNull(resourceClass);
      // 加上标记么?
      fields |= RESOURCE_CLASS;
      // 加上一层判断锁么，然后返回自身
      return selfOrThrowIfLocked();
    }
    
    @NonNull
    @SuppressWarnings("unchecked")
    private T selfOrThrowIfLocked() {
      if (isLocked) {
        throw new IllegalStateException("You cannot modify locked T, consider clone()");
      }
      return self();
    }
    
    private T self() {
      return (T) this;
    }
  ```
嗯，好像没什么有价值的东西，只是创建了一个RequestOptions，并把resourceClass、fields保存到变量域，再返回。

2. 第二步，在transition方法中创建并传入了一个DrawableTransitionOptions对象:
  ```
    @NonNull
    @CheckResult
    public RequestBuilder<TranscodeType> transition(
        @NonNull TransitionOptions<?, ? super TranscodeType> transitionOptions) {
      this.transitionOptions = Preconditions.checkNotNull(transitionOptions);
      isDefaultTransitionOptionsSet = false;
      return this;
    }
  ```
transition方法好像也只是记录下，DrawableTransitionOptions是动画效果，自带的，交叉淡化之类的。

3. 第三步，在apply方法中传入了第一步的DECODE_TYPE_GIF:
  ```
    @NonNull
    @CheckResult
    @Override
    public RequestBuilder<TranscodeType> apply(@NonNull BaseRequestOptions<?> requestOptions) {
      Preconditions.checkNotNull(requestOptions);
      // 就是更新了BaseRequestOptions中的数据
      return super.apply(requestOptions);
    }
    
    // BaseRequestOptions中
    @NonNull
    @CheckResult
    public T apply(@NonNull BaseRequestOptions<?> o) {
      // 设置了resourceClass
      if (isSet(other.fields, RESOURCE_CLASS)) {
        resourceClass = other.resourceClass;
      }
      
      // 把fields、options也保存进来
      fields |= other.fields;
      options.putAll(other.options);
      
      return selfOrThrowIfLocked();
    }
  ```
这里就是把新的requestOptions信息，保存到当前的requestOptions中吧，看名字apply也能理解。

三步看下来，还是一脸懵逼啊！大致就是把新的requestOptions(DECODE_TYPE_GIF)，更新到当前的requestOptions中，还在transition方法传了个动画效果。

#### 原理探究
本来花了很长时间去研究原理的，也写了几百行(下面删了)，最终发现GifDrawable可以用，是因为内置了Gif格式的解码器、转码器等:
```
  // 在Glide构造方法里面添加的:
  registry
    //...
    /* GIFs */
    .append(
        Registry.BUCKET_GIF,
        InputStream.class,
        GifDrawable.class,
        new StreamGifDecoder(imageHeaderParsers, byteBufferGifDecoder, arrayPool))
    .append(Registry.BUCKET_GIF, ByteBuffer.class, GifDrawable.class, byteBufferGifDecoder)
    .append(GifDrawable.class, new GifDrawableEncoder())
    /* GIF Frames */
    // Compilation with Gradle requires the type to be specified for UnitModelLoader here.
    .append(
        GifDecoder.class, GifDecoder.class, UnitModelLoader.Factory.<GifDecoder>getInstance())
    .append(
        Registry.BUCKET_BITMAP,
        GifDecoder.class,
        Bitmap.class,
        new GifFrameResourceDecoder(bitmapPool))
```
WTF？有点打击看源码的心情，后面觉得不能气馁，继续学了下Glide中到底如何扩展格式，这里写在了另一篇文章里面有兴趣的可以看一下:
[Glide加载自定义图片格式](https://juejin.cn/post/7281208218176995328)

研究完Glide中到底如何扩展格式，再回过来理解GlideType的原理，就明了多了，就是定制化RequestBuilder中的as方法:
```
// 一般使用
Glide.with(context)
    .`as`(CustomDrawable::class.java)
    // .load("file:///android_asset/pic.custom")
    .load(R.raw.pic)
    .into(binding.image)

// 扩展使用 
GlideAppExt.with(it)
  .asCustom()
  .load(R.raw.pic)
  .into(binding.image)
```
说明GlideType注解确实只是一个标记，真正起作用的是ResourceDecoder。

于是，我去翻了下我的第一篇文章，在engine.load(...)节，有把数据处理成resource的方法:
```
  private void decodeFromRetrievedData() {
    Resource<R> resource = null;
    try {
      // 处理数据
      resource = decodeFromData(currentFetcher, currentData, currentDataSource);
    } catch (GlideException e) {
      e.setLoggingDetails(currentAttemptingKey, currentDataSource);
      throwables.add(e);
    }
    
    if (resource != null) {
      // 通知获取数据完成
      notifyEncodeAndRelease(resource, currentDataSource);
    } else {
      runGenerators();
    }
  }
```
也就是说数据是在decodeFromData方法中解码的，接下来就一路追踪，过程太长就不写了，找到了下面方法:
```
  // DecodeJob中
  private <Data> Resource<R> decodeFromFetcher(Data data, DataSource dataSource)
      throws GlideException {
    LoadPath<Data, ?, R> path = decodeHelper.getLoadPath((Class<Data>) data.getClass());
    return runLoadPath(data, dataSource, path);
  }
```
在DecodeJob代码中第一次出现了Resource类中的R，也就是我们GlideType注解的类型，找下它怎么来的:
```
  // 原来是泛型类
  class DecodeJob<R> ...
  // 来自init方法里面的transcodeClass
  DecodeJob<R> init(
      // ...
      Class<?> resourceClass,
      Class<R> transcodeClass,
      // ...) {
```
其实这个transcodeClass很眼熟啊，第一篇里面作为参数传递了很多次，我们这追踪回去，也能发现它最终来自于RequestBuilder啊:
```
public class RequestBuilder<TranscodeType>
```
再看看RequestBuilder哪里来的，原来是as方法来的，其中的ResourceType来自resourceClass:
```
  @NonNull
  @CheckResult
  public <ResourceType> RequestBuilder<ResourceType> as(
      @NonNull Class<ResourceType> resourceClass) {
    return new RequestBuilder<>(glide, this, resourceClass, context);
  }
```
回去看《GlideType - 部分原理 - 3.第三步》，这不就闭环了吗？里面也设置了resourceClass:
```
  // BaseRequestOptions中
  @NonNull
  @CheckResult
  public T apply(@NonNull BaseRequestOptions<?> o) {
    // 设置了resourceClass
    if (isSet(other.fields, RESOURCE_CLASS)) {
      resourceClass = other.resourceClass;
    }
    
    // 把fields、options也保存进来
    fields |= other.fields;
    options.putAll(other.options);
    
    return selfOrThrowIfLocked();
  }
```
在BaseRequestOptions中的apply方法设置了这个resourceClass。

### 探究ResourceDecoder
都看到这了，也顺便追踪下ResourceDecoder怎么来的吧。继续从上面decodeFromData出发，一路查找:
```
  // DecodePath类中
  @NonNull
  private Resource<ResourceType> decodeResourceWithList(
      DataRewinder<DataType> rewinder, int width, int height, @NonNull Options options,
      List<Throwable> exceptions) throws GlideException {
      
    Resource<ResourceType> result = null;
    //noinspection ForLoopReplaceableByForEach to improve perf
    for (int i = 0, size = decoders.size(); i < size; i++) {
    
      // 在这里获取了ResourceDecoder去解码，得到Resource<ResourceType>
      ResourceDecoder<DataType, ResourceType> decoder = decoders.get(i);
      try {
        DataType data = rewinder.rewindAndGet();
        if (decoder.handles(data, options)) {
          data = rewinder.rewindAndGet();
          result = decoder.decode(data, width, height, options);
        }
      } catch (IOException | RuntimeException | OutOfMemoryError e) {
        exceptions.add(e);
      }
      if (result != null) { break; }
    }
    if (result == null) { throw new GlideException(failureMessage, new ArrayList<>(exceptions)); }
    return result;
  }
```
继续找一下这里的decoders是从哪里来的，找到最近创建ResourceDecoder数组的地方:
```
  // ResourceDecoderRegistry类中
  // 这里的decoders是一个map，以bucket为键，保存的是decoder数组
  private final Map<String, List<Entry<?, ?>>> decoders = new HashMap<>();
    
  @NonNull
  @SuppressWarnings("unchecked")
  public synchronized <T, R> List<ResourceDecoder<T, R>> getDecoders(
      @NonNull Class<T> dataClass, @NonNull Class<R> resourceClass) {
      
    List<ResourceDecoder<T, R>> result = new ArrayList<>();
    
    // 和bucket什么有关
    for (String bucket : bucketPriorityList) {
      List<Entry<?, ?>> entries = decoders.get(bucket);
      if (entries == null) { continue; }
      for (Entry<?, ?> entry : entries) {
        if (entry.handles(dataClass, resourceClass)) {
          result.add((ResourceDecoder<T, R>) entry.decoder);
        }
      }
    }
    return result;
  }
```
这里迷糊了，又有什么bucket，又有以bucket为键的好多decoder数组，继续看下decoder到底哪里添加的，又经过几个方法，发现是在Registry类中下面方法里:
```
  // Registry类中
  @NonNull
  public <Data, TResource> Registry append(
      @NonNull String bucket,
      @NonNull Class<Data> dataClass,
      @NonNull Class<TResource> resourceClass,
      @NonNull ResourceDecoder<Data, TResource> decoder) {
    decoderRegistry.append(bucket, decoder, dataClass, resourceClass);
    return this;
  }
```
如果看了我写的[Glide加载自定义图片格式](https://juejin.cn/post/7281208218176995328)，那就很熟悉了，自定义格式的时候也是通过Registry的append添加上去的。

继续看哪里调用了这个方法:
![image.png](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/fe02d6a9c82448699ab9bcd4c98c8de4~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1700&h=664&s=125347&e=png&b=4b473f)

居然是在Glide构造方法里面添加的:
```    
  registry
    //...
    /* GIFs */
    .append(
        Registry.BUCKET_GIF,
        InputStream.class,
        GifDrawable.class,
        new StreamGifDecoder(imageHeaderParsers, byteBufferGifDecoder, arrayPool))
    .append(Registry.BUCKET_GIF, ByteBuffer.class, GifDrawable.class, byteBufferGifDecoder)
    .append(GifDrawable.class, new GifDrawableEncoder())
    /* GIF Frames */
    // Compilation with Gradle requires the type to be specified for UnitModelLoader here.
    .append(
        GifDecoder.class, GifDecoder.class, UnitModelLoader.Factory.<GifDecoder>getInstance())
    .append(
        Registry.BUCKET_BITMAP,
        GifDecoder.class,
        Bitmap.class,
        new GifFrameResourceDecoder(bitmapPool))
```
到这，ResourceDecoder也有了个大致了解，是不是有种恍然大悟的感觉！

## 小结
本来以为拿着别人写好的关于扩展的文章，实践下，顺便研究下原理啥的，结果遇到了很多问题，拖了很久才把Glide扩展的内容大致了解了下，希望对读者有所帮助！

ps. 本来这篇文章要和[Glide加载自定义图片格式](https://juejin.cn/post/7281208218176995328)一起发的，就剩十分钟结个尾了，结果忙了一段时间，又逢中秋国庆假期，现在才发，继续努力吧！