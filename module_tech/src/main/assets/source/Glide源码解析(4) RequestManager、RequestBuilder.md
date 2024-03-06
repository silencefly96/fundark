# Glide源码解析(4) RequestManager、RequestBuilder
## 前言
Glide系列的文章又拖了很久，九月的时候又遇到了烦人的安全整改问题，弄了很久，后面又是中秋国庆放假，现在回来，还是要努力工作和学习吧！

前面第二篇已经把RequestManager讲了一部分，但是没有讲到重要的manager部分，这篇文章将详细看看，并分析下RequestBuilder。

## RequestManager
RequestManager和它的名字一样，是用来管理request的类:
> A class for managing and starting requests for Glide.

前面讲过了它的生命周期感知和load方法，下面就来看看它到底是如何管理request的，都有哪些管理功能，下面我把功能整理了下，希望能更明了些。

### RequestOptions相关
上一篇文章中，我们已经和RequestOptions打交道很久了，是它控制Glide的使用的众多配置，点开源码可以发现，RequestOptions只是一个RequestOptions集合的类，真正的内容保存在BaseRequestOptions<RequestOptions>里面。

#### RequestOptions类
RequestOptions提供了很多方法， 调用这些方法能够生成一个新的RequestOptions，并通过BaseRequestOptions的方法去改变属性:
```
public class RequestOptions extends BaseRequestOptions<RequestOptions> {
  // 部分保存的RequestOptions
  @Nullable private static RequestOptions skipMemoryCacheTrueOptions;
  // ...
  
  // 调用RequestOptions的方法生成新的RequestOptions
  @NonNull
  @CheckResult
  public static RequestOptions diskCacheStrategyOf(@NonNull DiskCacheStrategy diskCacheStrategy) {
    return new RequestOptions().diskCacheStrategy(diskCacheStrategy);
  }
  // ...
```
BaseRequestOption才是真正的数据管理类，保存数据，向外提供setter及getter方法:
```
  @NonNull
  @CheckResult
  public T diskCacheStrategy(@NonNull DiskCacheStrategy strategy) {
    // 克隆一份再设置，并返回
    if (isAutoCloneEnabled) {
      return clone().diskCacheStrategy(strategy);
    }
    
    // 更改自身
    this.diskCacheStrategy = Preconditions.checkNotNull(strategy);
    // 标记上设置了这个属性
    fields |= DISK_CACHE_STRATEGY;

    return selfOrThrowIfLocked();
  }
```

#### 修改RequestOptions
RequestManager提供了几个用于对RequestOptions设置、更新的方法:
```
  protected synchronized void setRequestOptions(@NonNull RequestOptions toSet) {
    requestOptions = toSet.clone().autoClone();
  }

  private synchronized void updateRequestOptions(@NonNull RequestOptions toUpdate) {
    requestOptions = requestOptions.apply(toUpdate);
  }
  
  @NonNull
  public synchronized RequestManager applyDefaultRequestOptions(
      @NonNull RequestOptions requestOptions) {
    updateRequestOptions(requestOptions);
    return this;
  }
  
  @NonNull
  public synchronized RequestManager setDefaultRequestOptions(
      @NonNull RequestOptions requestOptions) {
    setRequestOptions(requestOptions);
    return this;
  }
```
下面来着重看下这里的clone、apply、autoClone是什么操作，能学到什么东西。先来看下clone方法:
```
  @CheckResult
  @Override
  public T clone() {
    try {
      // 调用object的clone，浅拷贝
      BaseRequestOptions<?> result = (BaseRequestOptions<?>) super.clone();
      // options和transformations有数组拷贝
      result.options = new Options();
      result.options.putAll(options);
      result.transformations = new CachedHashCodeArrayMap<>();
      result.transformations.putAll(transformations);
      // 注意这两个标记位
      result.isLocked = false;
      result.isAutoCloneEnabled = false;
      return (T) result;
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }
 
```
clone大致就是浅拷贝后，对options和transformations的数组复制了下，clone的时候会把isLocked和isAutoCloneEnabled两个标志位设为false，下面正好会用到。
```
  @NonNull
  public T autoClone() {
    if (isLocked && !isAutoCloneEnabled) {
      throw new IllegalStateException(
          "You cannot auto lock an already locked options object" + ", try clone() first");
    }
    isAutoCloneEnabled = true;
    return lock();
  }
  
  public T lock() {
    isLocked = true;
    // This is the only place we should not check locked.
    return self();
  }
  
  private T self() {
    return (T) this;
  }
```
autoClone调用了几层，感觉就是加了个lock标记，把上面clone的两个标记位改为了true，autoClone前验证了这两个标志，如果为true会抛异常，所以想要autoClone，必须先clone。
```
  @NonNull
  @CheckResult
  public T apply(@NonNull BaseRequestOptions<?> o) {
    // 设置了isAutoCloneEnabled要，先clone再设置
    if (isAutoCloneEnabled) {
      return clone().apply(o);
    }
    
    BaseRequestOptions<?> other = o;

    // 根据fields判断，如果设置了该项，则复制到这个新的RequestOptions
    if (isSet(other.fields, SIZE_MULTIPLIER)) {
      sizeMultiplier = other.sizeMultiplier;
    }
    // ...
    
    // Applying options with dontTransform() is expected to clear our transformations.
    // 清空了transformations？
    if (!isTransformationAllowed) {
      transformations.clear();
      fields &= ~TRANSFORMATION;
      isTransformationRequired = false;
      fields &= ~TRANSFORMATION_REQUIRED;
      isScaleOnlyOrNoTransform = true;
    }

    // 添加上所有fields
    fields |= other.fields;
    
    // 复制options
    options.putAll(other.options);

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
```
根据apply名称可以知道，就是对RequestOptions进行更新，如果isAutoCloneEnabled为true需要clone后再更新，并返回clone后的RequestOptions，另外返回的时候不能是isLocked。也就是说上面用了autoClone就会lock住，更新要clone后再更新。

#### RequestOptions默认值
在RequestManager的构造方法设置了RequestOptions的默认值:
```
  setRequestOptions(glide.getGlideContext().getDefaultRequestOptions());
```
最后是GlideContext中的defaultRequestOptionsFactory创建的，并且lock了。
```
  public synchronized RequestOptions getDefaultRequestOptions() {
    if (defaultRequestOptions == null) {
      defaultRequestOptions = defaultRequestOptionsFactory.build().lock();
    }

    return defaultRequestOptions;
  }
```

### Request控制相关
主要就是监听、判断状态、暂停、恢复这几个，监听比较简单就是加个requestListener
```
  // 线程安全的数组
  private final CopyOnWriteArrayList<RequestListener<Object>> defaultRequestListeners;
  
  public RequestManager addDefaultRequestListener(RequestListener<Object> requestListener) {
    defaultRequestListeners.add(requestListener);
    return this;
  }
```
而判断状态、暂停、恢复这几个，都是通过requestTracker去操作的:
```
  // 判断状态
  public synchronized boolean isPaused() { return requestTracker.isPaused(); }

  // 暂停，两种形式，英文注释写的很清楚
  // Cancels any in progress loads, but does not clear resources of completed loads.
  public synchronized void pauseRequests() { requestTracker.pauseRequests(); }
  // Cancels any in progress loads and clears resources of completed loads.
  public synchronized void pauseAllRequests() { requestTracker.pauseAllRequests(); }
  
  // 清除所有treeNode上的request，对应上面两种暂停情况
  public synchronized void pauseAllRequestsRecursive() {
    pauseAllRequests();
    for (RequestManager requestManager : treeNode.getDescendants()) {
      requestManager.pauseAllRequests();
    }
  }
  public synchronized void pauseRequestsRecursive() {
    pauseRequests();
    for (RequestManager requestManager : treeNode.getDescendants()) {
      requestManager.pauseRequests();
    }
  }
  
  // 恢复
  public synchronized void resumeRequests() {
    requestTracker.resumeRequests();
  }
  
  // 全部恢复
  public synchronized void resumeRequestsRecursive() {
    // 限制在主线程
    Util.assertMainThread();
    resumeRequests();
    for (RequestManager requestManager : treeNode.getDescendants()) {
      requestManager.resumeRequests();
    }
  }
```
这里出现了一个treeNode，我们下面也看下，当然重点是控制request的requestTracker。

#### RequestManagerTreeNode
RequestManagerTreeNode是一个接口，就一个getDescendants方法:
```
public interface RequestManagerTreeNode {
  @NonNull
  Set<RequestManager> getDescendants();
}
```
这个是从RequestManager的构造方法传递过来的，默认是EmptyRequestManagerTreeNode:
```
final class EmptyRequestManagerTreeNode implements RequestManagerTreeNode {
  @NonNull
  @Override
  public Set<RequestManager> getDescendants() {
    return Collections.emptySet();
  }
}
```
还有另外两个实现，在SupportRequestManagerFragment和RequestManagerFragment中，我们看下其中一个，大致就能知道这个接口是什么意思了:
```
private class FragmentRequestManagerTreeNode implements RequestManagerTreeNode {

    // 编译器自动生成的注解？
    @Synthetic
    FragmentRequestManagerTreeNode() {}

    @SuppressWarnings("deprecation")
    @NonNull
    @Override
    public Set<RequestManager> getDescendants() {
      Set<RequestManagerFragment> descendantFragments = getDescendantRequestManagerFragments();
      Set<RequestManager> descendants = new HashSet<>(descendantFragments.size());
      for (RequestManagerFragment fragment : descendantFragments) {
        if (fragment.getRequestManager() != null) {
          descendants.add(fragment.getRequestManager());
        }
      }
      return descendants;
    }
  }
```
大致意思就是每个RequestManagerFragment都有一个RequestManager，这里获取了所有的descendantFragments(意思是后裔fragment们)，然后获取他们的RequestManager，添加到集合中并返回。

有点懵，看完下面看下descendantFragments是怎么来的，就明白了:
```
  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
  @Synthetic
  @NonNull
  Set<RequestManagerFragment> getDescendantRequestManagerFragments() {
    // 根节点的RequestManagerFragment，childRequestManagerFragments保存了它派生的childRequestManagerFragments
    if (equals(rootRequestManagerFragment)) {
      return Collections.unmodifiableSet(childRequestManagerFragments);
      
    // 没有根节点(或无法获取根节点)，就没有派生child
    } else if (rootRequestManagerFragment == null
        || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
      // Pre JB MR1 doesn't allow us to get the parent fragment so we can't introspect hierarchy,
      // so just return an empty set.
      return Collections.emptySet();
      
    // 派生的RequestManagerFragment，遍历其根节点，保存当前fragment派生的fragment(有RequestManage的fragment)
    } else {
      Set<RequestManagerFragment> descendants = new HashSet<>();
      for (RequestManagerFragment fragment :
          rootRequestManagerFragment.getDescendantRequestManagerFragments()) {
          
        // 判断是否是当前fragment的后裔。。(当前fragment)和(遍历fragment的parent)拥有相同parent
        if (isDescendant(fragment.getParentFragment())) {
          descendants.add(fragment);
        }
      }
      return Collections.unmodifiableSet(descendants);
    }
  }
  
  // 有点绕，意思就是这个fragment和传进来的fragment，有同一个parent
  private boolean isDescendant(@NonNull Fragment fragment) {
    Fragment root = getParentFragment();
    Fragment parentFragment;
    while ((parentFragment = fragment.getParentFragment()) != null) {
      if (parentFragment.equals(root)) {
        return true;
      }
      fragment = fragment.getParentFragment();
    }
    return false;
  }
```
我总结了下，理解下下面这几点，应该就清楚了:
- 一个RequestManagerFragment可以派生多个child RequestManagerFragment，所以要得到所有RequestManager，就要获取到所有的RequestManagerFragment。
- 所有派生的RequestManagerFragment都有同一个rootRequestManagerFragment，它的childRequestManagerFragments保存了所有派生fragment的信息。
- 对于当前的RequestManagerFragment，它需要拿到的是它派生出来的RequestManagerFragment

#### RequestTracker
RequestTracker是在RequestManager构造方法new出来的，我们就不用找它哪里来的了，直接看它的功能:
![image.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/4fac64b7d9b44cfcb7a0a678618cfb85~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=647&h=484&s=50256&e=png&b=3c3f41)

下面挑几个看一下，首先看下runRequest，前面应该有提到:
```
  private final Set<Request> requests =
      Collections.newSetFromMap(new WeakHashMap<Request, Boolean>());
  private final List<Request> pendingRequests = new ArrayList<>();
  private boolean isPaused;
    
  public void runRequest(@NonNull Request request) {
    // 记录
    requests.add(request);
    if (!isPaused) {
      // 启动
      request.begin();
    } else {
      request.clear();
      // 添加到等待队列
      pendingRequests.add(request);
    }
  }
```
剩下的基本也差不多，稍微看一眼就行:
```
  void addRequest(Request request) { requests.add(request); }
  public boolean isPaused() { return isPaused; }
  
  public void pauseRequests() {
    isPaused = true;
    for (Request request : Util.getSnapshot(requests)) {
      if (request.isRunning()) {
        // Avoid clearing parts of requests that may have completed (thumbnails) to avoid blinking
        // in the UI, while still making sure that any in progress parts of requests are immediately
        // stopped.
        request.pause();
        pendingRequests.add(request);
      }
    }
  }
  
  public void resumeRequests() {
    isPaused = false;
    for (Request request : Util.getSnapshot(requests)) {
      // We don't need to check for cleared here. Any explicit clear by a user will remove the
      // Request from the tracker, so the only way we'd find a cleared request here is if we cleared
      // it. As a result it should be safe for us to resume cleared requests.
      if (!request.isComplete() && !request.isRunning()) {
        request.begin();
      }
    }
    pendingRequests.clear();
  }
```
就是对request的控制功能了，没什么好说的，这里RequestManager里面还对RequestTracker做了一层封装，向外提供对request的控制:
```
public synchronized boolean isPaused()
public synchronized void pauseRequests()
public synchronized void pauseAllRequests() 
public synchronized void resumeRequests()
```

#### RequestBuilder相关
下面我们会新开一节来讲RequestBuilder，所以这里就看下RequestManager有哪些通过RequestBuilder去操作的功能:
- as系列方法
- load系列方法
- download方法

download方法有点陌生，不过比较简单，就是标记只下载，可以看下:
```
@NonNull
@CheckResult
public RequestBuilder<File> download(@Nullable Object model) {
    return downloadOnly().load(model);
}

@NonNull
@CheckResult
public RequestBuilder<File> downloadOnly() {
    return as(File.class).apply(DOWNLOAD_ONLY_OPTIONS);
}
```
比较有意思的是RequestBuilder继承了BaseRequestOptions。

> ps. 2023-11-13 隔了一个月，继续写Glide，可能和上面没那么连贯
## RequestBuilder
上面讲了RequestManager，由它通过load方法可以生成RequestBuilder对象，核心还是在其as方法:
```
@NonNull
@CheckResult
public <ResourceType> RequestBuilder<ResourceType> as(
  @NonNull Class<ResourceType> resourceClass) {
    return new RequestBuilder<>(glide, this, resourceClass, context);
}
```
as方法会给RequestBuilder赋值泛型的类型，并创建这个RequestBuilder，再传递load方法给RequestBuilder去操作。

---
上面说到了RequestBuilder继承了BaseRequestOptions，在RequestBuilder的构造方法里，会应用RequestManager内的RequestOptions:
```
protected RequestBuilder(
      @NonNull Glide glide,
      RequestManager requestManager,
      Class<TranscodeType> transcodeClass,
      Context context) {
    this.glide = glide;
    this.requestManager = requestManager;
    this.transcodeClass = transcodeClass;
    this.context = context;
    this.transitionOptions = requestManager.getDefaultTransitionOptions(transcodeClass);
    this.glideContext = glide.getGlideContext();

    initRequestListeners(requestManager.getDefaultRequestListeners());
    apply(requestManager.getDefaultRequestOptions());
}

@CheckResult
@Override
public RequestBuilder<TranscodeType> apply(@NonNull BaseRequestOptions<?> requestOptions) {
    Preconditions.checkNotNull(requestOptions);
    return super.apply(requestOptions);
}
```
也就是Glide中设置的requestOptions，会设置到RequestBuilder里面。

下面再来具体看看RequestBuilder的功能。

### 设置相关属性
首先我们看下用来设置属性的一些链式方法，他们会设置属性并返回RequestBuilder自身:
```
// 加载结束后的transitionOptions
public RequestBuilder<TranscodeType> transition(
    @NonNull TransitionOptions<?, ? super TranscodeType> transitionOptions)

// 设置加载成功或失败的监听
public RequestBuilder<TranscodeType> listener(
    @Nullable RequestListener<TranscodeType> requestListener)
public RequestBuilder<TranscodeType> addListener(
    @Nullable RequestListener<TranscodeType> requestListener)

// 失败图
public RequestBuilder<TranscodeType> error(@Nullable RequestBuilder<TranscodeType> errorBuilder) 

// 设置略缩图(比原图更快，占位)
public RequestBuilder<TranscodeType> thumbnail(
      @Nullable RequestBuilder<TranscodeType> thumbnailRequest)
public RequestBuilder<TranscodeType> thumbnail(
      @Nullable RequestBuilder<TranscodeType>... thumbnails)
public RequestBuilder<TranscodeType> thumbnail(float sizeMultiplier) {
    if (sizeMultiplier < 0f || sizeMultiplier > 1f) {
      throw new IllegalArgumentException("sizeMultiplier must be between 0 and 1");
    }
    this.thumbSizeMultiplier = sizeMultiplier;
    return this;
}
```
没什么好说的，就是设置属性。

### load方法
接下来就是一系列的load方法，也是从RequestManager来的:
```
public RequestBuilder<TranscodeType> load(@Nullable Object model)
public RequestBuilder<TranscodeType> load(@Nullable Bitmap bitmap)
public RequestBuilder<TranscodeType> load(@Nullable Drawable drawable)
public RequestBuilder<TranscodeType> load(@Nullable String string)
public RequestBuilder<TranscodeType> load(@Nullable Uri uri)
public RequestBuilder<TranscodeType> load(@Nullable File file)
public RequestBuilder<TranscodeType> load(@RawRes @DrawableRes @Nullable Integer resourceId)
public RequestBuilder<TranscodeType> load(@Nullable URL url) 
public RequestBuilder<TranscodeType> load(@Nullable byte[] model)
```
最后都是走的loadGeneric方法:
```
@NonNull
private RequestBuilder<TranscodeType> loadGeneric(@Nullable Object model) {
    this.model = model;
    isModelSet = true;
    return this;
}
```
还只是设置了属性，返回了自身，再就到into方法里面去加载。

### into方法
上面都是一些设置参数的方法，最后到into方法才是真正去加载的，这里into方法有三个public方法:
```
public <Y extends Target<TranscodeType>> Y into(@NonNull Y target)
public ViewTarget<ImageView, TranscodeType> into(@NonNull ImageView view)
public FutureTarget<TranscodeType> into(int width, int height)
```
这三个原理还不太一样，下面分别分析下。

#### into(@NonNull Y target)
这个方法比较简单，直接就调用了另外的非公开方法，最终到达私有的into方法，这个私有方法我们第一篇有讲到，request就是在这最终创建:
```
@NonNull
public <Y extends Target<TranscodeType>> Y into(@NonNull Y target) {
    return into(target, /*targetListener=*/ null, Executors.mainThreadExecutor());
}
  
@Synthetic
<Y extends Target<TranscodeType>> Y into(
    @NonNull Y target,
    @Nullable RequestListener<TranscodeType> targetListener,
    Executor callbackExecutor) {
        return into(target, targetListener, /*options=*/ this, callbackExecutor);
}

// 最终执行方法
private <Y extends Target<TranscodeType>> Y into(
    @NonNull Y target,
    @Nullable RequestListener<TranscodeType> targetListener,
    BaseRequestOptions<?> options,
    Executor callbackExecutor)
```
先看下这个过程中创建、传递了什么参数:
- target，加载目标
- targetListener，加载完的回调
- options，配置参数，即RequestBuilder自身
- callbackExecutor，执行的线程池

看完传递的参数，就很明了了，就是用线程池根据配置参数去加载，加载完会回调，把数据放到加载目标去，这里再抄一遍第一篇的代码，再详细解析下里面的细节:
```
  private <Y extends Target<TranscodeType>> Y into(
      @NonNull Y target,
      @Nullable RequestListener<TranscodeType> targetListener,
      BaseRequestOptions<?> options,
      Executor callbackExecutor) {
    Preconditions.checkNotNull(target);
    if (!isModelSet) {
      throw new IllegalArgumentException("You must call #load() before calling #into()");
    }

    // 这里终于创建了request
    Request request = buildRequest(target, targetListener, options, callbackExecutor);

    // 判断了下要加载的目标imageView旧的请求是否和新的一致，做出处理
    Request previous = target.getRequest();
    if (request.isEquivalentTo(previous)
        && !isSkipMemoryCacheWithCompletePreviousRequest(options, previous)) {
      if (!Preconditions.checkNotNull(previous).isRunning()) {
        previous.begin();
      }
      return target;
    }

    // 重新加载
    requestManager.clear(target);
    target.setRequest(request);
    requestManager.track(target, request);

    return target;
  }
  
  // 是否跳过内存缓存，使用前一个request的结果
  private boolean isSkipMemoryCacheWithCompletePreviousRequest(
      BaseRequestOptions<?> options, Request previous) {
    return !options.isMemoryCacheable() && previous.isComplete();
  }
```
可以看到最后是通过requestManager去请求的:
```
  synchronized void track(@NonNull Target<?> target, @NonNull Request request) {
    // targetTracker会处理生命周期
    targetTracker.track(target);
    // 交给requestTracker处理请求
    requestTracker.runRequest(request);
  }
```
将请求的生命周期管理和请求事务的处理分给targetTracker和requestTracker去处理，最后通过request的begin方法启动请求。
```
  public void runRequest(@NonNull Request request) {
    requests.add(request);
    if (!isPaused) {
      request.begin();
    } else {
      request.clear();
      if (Log.isLoggable(TAG, Log.VERBOSE)) {
        Log.v(TAG, "Paused, delaying request");
      }
      pendingRequests.add(request);
    }
  }
```
关于request相关的细节，后面新开文章去解析了，这里讲的是第一个public的into方法，到此就差不多了。

#### into(@NonNull ImageView view)
其实这个方法才是我们经常使用到的into方法，第一篇文章解析的也是这个方法，继续看下它的代码:
```
  @NonNull
  public ViewTarget<ImageView, TranscodeType> into(@NonNull ImageView view) {
    Util.assertMainThread();
    Preconditions.checkNotNull(view);

    // 就是没设置ransformation的时候，根据ScaleType设置一个options
    BaseRequestOptions<?> requestOptions = this;
    if (!requestOptions.isTransformationSet()
        && requestOptions.isTransformationAllowed()
        && view.getScaleType() != null) {
        
      // 根据view的缩放模式设置option
      switch (view.getScaleType()) {
        case CENTER_CROP:
          requestOptions = requestOptions.clone().optionalCenterCrop();
          break;
        case CENTER_INSIDE:
          requestOptions = requestOptions.clone().optionalCenterInside();
          break;
        case FIT_CENTER:
        case FIT_START:
        case FIT_END:
          requestOptions = requestOptions.clone().optionalFitCenter();
          break;
        case FIT_XY:
          requestOptions = requestOptions.clone().optionalCenterInside();
          break;
        case CENTER:
        case MATRIX:
        default:
          // Do nothing.
      }
    }

    return into(
        glideContext.buildImageViewTarget(view, transcodeClass),
        /*targetListener=*/ null,
        requestOptions,
        Executors.mainThreadExecutor());
  }
```
这里分了两步，第一步是根据view的ScaleType生成了一个requestOptions，第二步是将ImageView转换成ViewTarget并调用上一步的实际into方法去加载。

先看下buildImageViewTarget，好像前面文章也有说到过:
```
@NonNull
public <X> ViewTarget<ImageView, X> buildImageViewTarget(
  @NonNull ImageView imageView, @NonNull Class<X> transcodeClass) {
    return imageViewTargetFactory.buildTarget(imageView, transcodeClass);
}

public class ImageViewTargetFactory {
  @NonNull
  @SuppressWarnings("unchecked")
  public <Z> ViewTarget<ImageView, Z> buildTarget(
      @NonNull ImageView view, @NonNull Class<Z> clazz) {
    if (Bitmap.class.equals(clazz)) {
      return (ViewTarget<ImageView, Z>) new BitmapImageViewTarget(view);
    } else if (Drawable.class.isAssignableFrom(clazz)) {
      return (ViewTarget<ImageView, Z>) new DrawableImageViewTarget(view);
    } else {
      throw new IllegalArgumentException(
          "Unhandled class: " + clazz + ", try .as*(Class).transcode(ResourceTranscoder)");
    }
  }
}
```
默认只支持Bitmap和Drawable加载到ImageView里面去，再回忆下，这个transcodeClass是as方法来的，默认是asDrawable。

---
这里看到了isTransformationSet，我觉得还能再研究下:
```
  // BaseRequestOptions中
  public final boolean isTransformationSet() {
    return isSet(TRANSFORMATION);
  }
  
  public final boolean isTransformationAllowed() {
    return isTransformationAllowed;
  }
```
两个方法都在BaseRequestOptions中，就是对TRANSFORMATION的判断，看了下BaseRequestOptions里面的代码，非常多的transform，找了下资料，发现这些就是对加载图片的一个处理。

比如我们上面的操作:
```
  switch (view.getScaleType()) {
    case CENTER_CROP:
      requestOptions = requestOptions.clone().optionalCenterCrop();
      break;
```
走optionalCenterCrop方法:
```
  @NonNull
  @CheckResult
  public T optionalCenterCrop() {
    return optionalTransform(DownsampleStrategy.CENTER_OUTSIDE, new CenterCrop());
  }
  
  @NonNull
  final T optionalTransform(
      @NonNull DownsampleStrategy downsampleStrategy,
      @NonNull Transformation<Bitmap> transformation) {
    if (isAutoCloneEnabled) {
      return clone().optionalTransform(downsampleStrategy, transformation);
    }

    downsample(downsampleStrategy);
    return transform(transformation, /*isRequired=*/ false);
  }
```
其中CenterCrop就是一个BitmapTransformation子类:
```
public class CenterCrop extends BitmapTransformation
```
说的有点乱了，我就是想想提下有这东西，后面实际用到转换的时候再说！

#### into(int width, int height)
实际上这个方法被标记废弃了，实际上它做了一些封装，最后还是走到前面的into里面去:
```
  @Deprecated
  public FutureTarget<TranscodeType> into(int width, int height) {
    return submit(width, height);
  }
  
  @NonNull
  public FutureTarget<TranscodeType> submit(int width, int height) {
    final RequestFutureTarget<TranscodeType> target = new RequestFutureTarget<>(width, height);
    return into(target, target, Executors.directExecutor());
  }
```
RequestFutureTarget既是加载的目标，又是回调的监听者，它不是一个ImageView，更多的像是FutureTask一样在未来能拿到数据:
```
  @Override
  public R get(long time, @NonNull TimeUnit timeUnit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return doGet(timeUnit.toMillis(time));
  }
```
有点像把所有功能都放在里面执行，最终得到结果，类似一个上帝类，这就不多研究了，毕竟废弃了。

### 其他方法
还剩下几个方法，submit和上面最后一个into一起的就不说了，就还有preload和downloadOnly，下面分别看下。

#### preload方法
先看下代码:
```
  @NonNull
  public Target<TranscodeType> preload(int width, int height) {
    final PreloadTarget<TranscodeType> target = PreloadTarget.obtain(requestManager, width, height);
    return into(target);
  }
  
  @NonNull
  public Target<TranscodeType> preload() {
    return preload(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL);
  }
  
  @NonNull
  public <Y extends Target<TranscodeType>> Y into(@NonNull Y target) {
    return into(target, /*targetListener=*/ null, Executors.mainThreadExecutor());
  }
```
貌似和上面submit类似，但是只是一个Target，没有设置targetListener，PreloadTarget还是挺简单的，看下里面的关键代码:
```
  private static final int MESSAGE_CLEAR = 1;
  private static final Handler HANDLER =
      new Handler(
          Looper.getMainLooper(),
          new Callback() {
            @Override
            public boolean handleMessage(Message message) {
              // 收到消息后好像就是做了清除操作
              if (message.what == MESSAGE_CLEAR) {
                ((PreloadTarget<?>) message.obj).clear();
                return true;
              }
              return false;
            }
          });
          
  @Override
  public void onResourceReady(@NonNull Z resource, @Nullable Transition<? super Z> transition) {
    HANDLER.obtainMessage(MESSAGE_CLEAR, this).sendToTarget();
  }
  
  @Synthetic
  void clear() {
    requestManager.clear(this);
  }
```
就是下载完了清除任务了。

#### downloadOnly方法
downloadOnly方法也被标记废弃了，这里推荐用RequestManager的downloadOnly，我们看下代码:
```
  @Deprecated
  @CheckResult
  public <Y extends Target<File>> Y downloadOnly(@NonNull Y target) {
    return getDownloadOnlyRequest().into(target);
  }
  
  @Deprecated
  @CheckResult
  public FutureTarget<File> downloadOnly(int width, int height) {
    return getDownloadOnlyRequest().submit(width, height);
  }
  
  @NonNull
  @CheckResult
  protected RequestBuilder<File> getDownloadOnlyRequest() {
    return new RequestBuilder<>(File.class, this).apply(DOWNLOAD_ONLY_OPTIONS);
  }
```
呃，好像就是新建了一个RequestBuilder用上了DOWNLOAD_ONLY_OPTIONS属性，果然还是用RequestManager的downloadOnly好一些。

## 小结
这里就是讲了下RequestManager和RequestBuilder的内容，RequestManager章节包含了RequestOptions属性的设置、RequestManagerTreeNode在fragment中的查找、RequestTracker请求管理等，RequestBuilder讲解了和请求相关的一些操作。