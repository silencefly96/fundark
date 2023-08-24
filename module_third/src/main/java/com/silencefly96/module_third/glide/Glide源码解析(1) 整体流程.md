# Glide源码解析(1): 整体流程
## 前言
最近一段时间打算把一些常用的第三方开源库，自己好好地看一遍，毕竟纸上得来终觉浅，绝知此事要躬行啊。另外也是要给自己一点事去做，因为人一旦放松下来，脑子就容易滑丝，明明什么都没做，每天都觉得挺忙的，又玩个手机电脑一整天，休息没休息到，还更累了。我不想这样。

Glide是一个很常用的图片加载库，我从实习的时候就开始用了，看过一些原理，还模仿过一个加载库([自定义安卓图片懒加载](https://juejin.cn/post/7221559896215191610)，很有意思，但是真没系统性地去读它的源码，那现在就开始吧！

ps. 以下Glide版本为4.11.0。

## 使用例子
Glide最简单的使用就是下面三步了:
```
    fun useGlide(context: Context, url: String, imgView: ImageView) {
        Glide.with(context)         // RequestManager
            .load(url)              // RequestBuilder<Drawable>
            .into(imgView)
    }
```
其中with会生成一个RequestManager对象，它提供了对request的一些管理功能(例如: pauseRequests/resumeRequests):
```
    fun useGlide(context: Context, url: String, imgView: ImageView) {
        Glide.with(context)         // RequestManager
            .pauseRequests()
    }
```
而load会生成一个RequestBuilder<Drawable>对象，这一步可以设置缩放模式、圆角、占位图之类的:
```
    Glide.with(context)
        .load(url)
        .centerCrop() //centerCrop缩放模式
        .circleCrop() //裁剪为圆形
        .placeholder(ColorDrawable(Color.RED)) //占位drawable
        .into(imgView)
```
最后RequestBuilder<Drawable>的into方法会生成request，并发起请求，加载到imageView内，这里还会返回一个ViewTarget。

## 参考资料
刚开始点开Glide的源码还是唬住了我，有点懵逼，还是像看了一些资料后才继续往下看，下面也贴出来，希望读者也能少走些弯路:

> [Glide官方文档中文版](https://muyangmin.github.io/glide-docs-cn/)
> [Glide系列之——初识Glide](https://easyliu-ly.github.io/2021/01/16/android_source_analysis/glide_use/)
> [Glide系列之——Glide对象创建及功能扩展](https://easyliu-ly.github.io/2021/02/07/android_source_analysis/glide_new/)

ps. 后面两篇文章我觉得写的不错，可惜tj了，后面部分的内容作者没写了。

## 整体流程
作为Glide解析的第一篇文章，我打算简单地追踪下整体地流程，并绘制出一个流程图来，脑子里先有个概念，再去看源码具体思想，学习里面的精髓。

### 流程图

待做hhh

### with(context)
下面就来追踪第一步with地调用吧，先是到Glide中:
```
  @NonNull
  public static RequestManager with(@NonNull Context context) {
    return getRetriever(context).get(context);
  }
```
这里分了两步，第一步是获得一个RequestManagerRetriever，再由RequestManagerRetriever去获取RequestManager，先看第一步:
```
  @NonNull
  private static RequestManagerRetriever getRetriever(@Nullable Context context) {
    // ...
    return Glide.get(context).getRequestManagerRetriever();
  }
```
这里也分了两步，第一步是获得Glide对象，第二步才是获取RequestManagerRetriever，同样，先看第一步:
```
  @NonNull
  public static Glide get(@NonNull Context context) {
    if (glide == null) {
      GeneratedAppGlideModule annotationGeneratedModule =
          getAnnotationGeneratedGlideModules(context.getApplicationContext());
      synchronized (Glide.class) {
        if (glide == null) {
          // 初始化Glide单例
          checkAndInitializeGlide(context, annotationGeneratedModule);
        }
      }
    }

    return glide;
  }
```
这里是一个DCL，并在其中初始化了Glide，然后返回了glide这个单例，至于Glide地初始化我觉得看到着就可以了，更深入地后面再讲了。

回到上面获取RequestManagerRetriever那，只是返回了Glide对象地requestManagerRetriever域:
```
  @NonNull
  public RequestManagerRetriever getRequestManagerRetriever() {
    return requestManagerRetriever;
  }
```

再看最前面with中RequestManager的get方法:
```
  @NonNull
  public RequestManager get(@NonNull Context context) {
    if (context == null) {
      throw new IllegalArgumentException("You cannot start a load on a null Context");
    } else if (Util.isOnMainThread() && !(context instanceof Application)) {
      if (context instanceof FragmentActivity) {
        return get((FragmentActivity) context);
      } else if (context instanceof Activity) {
        return get((Activity) context);
      } else if (context instanceof ContextWrapper
          // ...
          && ((ContextWrapper) context).getBaseContext().getApplicationContext() != null) {
        return get(((ContextWrapper) context).getBaseContext());
      }
    }

    return getApplicationManager(context);
  }
```
这里好像复杂点，我们可以先知道这个RequestManager会根据context的不同创建不同的对象，默认是Application级别的。

好了，小结下，with方法里面流程如下:
> with(context)  -> getRetriever(context)    -> Glide.get(context) -> - |
> RequestManager <- RequestManagerRetriever  <- Glide              <- - |

### load(url)
上一步得到一个RequestManager对象，下面继续看这个对象的load方法做了什么:
```
  @Override
  public RequestBuilder<Drawable> load(@Nullable String string) {
    return asDrawable().load(string);
  }
  public RequestBuilder<Drawable> asDrawable() {
    return as(Drawable.class);
  }
  public <ResourceType> RequestBuilder<ResourceType> as(
      @NonNull Class<ResourceType> resourceClass) {
    return new RequestBuilder<>(glide, this, resourceClass, context);
  }
```
这里先经过一系列的调用，创建了一个RequestBuilder对象，最后通过这个RequestBuilder去load:
```
  public RequestBuilder<TranscodeType> load(@Nullable String string) {
    return loadGeneric(string);
  }
  private RequestBuilder<TranscodeType> loadGeneric(@Nullable Object model) {
    this.model = model;
    isModelSet = true;
    return this;
  }
```
额，所以就这，只是调用RequestBuilder的构造方法创建了个对象，然后调用了下它的loadGeneric方法，设置了些参数。

### into(imgView)
既然前面并不复杂，那感觉复杂的就要来了，下面我们开始看into方法:
```
  @NonNull
  public ViewTarget<ImageView, TranscodeType> into(@NonNull ImageView view) {
    Util.assertMainThread();
    Preconditions.checkNotNull(view);

    // 先不管这个条件
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
这里先不顾那个if的逻辑，代码流程进入到了下面这个into方法内:
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
```
我们就看新创建的请求是如何加载的，主要在requestManager的track方法:
```
  // 注意加了同步锁
  synchronized void track(@NonNull Target<?> target, @NonNull Request request) {
    targetTracker.track(target);
    requestTracker.runRequest(request);
  }
  
  public void track(@NonNull Target<?> target) {
    targets.add(target);
  }
  
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
requestManager的track方法做了两步操作，第一个就是把target加到targets里面，然后通过requestTracker去run request:
```
  // requestTracker中
  public void runRequest(@NonNull Request request) {
    requests.add(request);
    
    // 判断了下UI界面是否被pause了，是的话就加到pendingRequests中等待执行
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
终于啊，一路追踪下来看到了request的begin方法，这里终于要开始网络请求了，我们下面新开一小节。

### request.begin()

看到这又有点懵了，这个Request到底是哪个实现类？这里有三个实现类:

- ErrorRequestCoordinator
- SingleRequest
- ThumbnailRequestCoordinator

只能回到request创建的地方追踪下:
```
  Request request = buildRequest(target, targetListener, options, callbackExecutor);
  private Request buildRequest(...) {
    return buildRequestRecursive(...);
  }
```
好家伙，Recursive都出来了么？这也能递归，看下代码:
```
  private Request buildRequestRecursive(...) {

    // 处理失败的协调器？
    // Build the ErrorRequestCoordinator first if necessary so we can update parentCoordinator.
    ErrorRequestCoordinator errorRequestCoordinator = null;
    if (errorBuilder != null) {
      errorRequestCoordinator = new ErrorRequestCoordinator(requestLock, parentCoordinator);
      parentCoordinator = errorRequestCoordinator;
    }

    // 略缩图请求？
    Request mainRequest = buildThumbnailRequestRecursive(...);

    if (errorRequestCoordinator == null) {
      return mainRequest;
    }

    // 失败图片的宽高
    int errorOverrideWidth = errorBuilder.getOverrideWidth();
    int errorOverrideHeight = errorBuilder.getOverrideHeight();
    if (Util.isValidDimensions(overrideWidth, overrideHeight) && !errorBuilder.isValidOverride()) {
      errorOverrideWidth = requestOptions.getOverrideWidth();
      errorOverrideHeight = requestOptions.getOverrideHeight();
    }

    // 失败请求
    Request errorRequest = errorBuilder.buildRequestRecursive(...);
    
    // 这里把略缩图请求和失败请求都给了errorRequestCoordinator(失败协调器)
    errorRequestCoordinator.setRequests(mainRequest, errorRequest);
    return errorRequestCoordinator;
  }
```
这里代码很长，但是东西不多，大致就是一个协调的request里面又包了两个request，正常的话还是执行mainRequest:
```
  private Request buildThumbnailRequestRecursive(...) {
      // 套娃太长，后面研究，看重点
      Request fullRequest = obtainRequest(...);
  }
  
  private Request obtainRequest(...) {
    return SingleRequest.obtain(...);
  }
```
buildThumbnailRequestRecursive方法里面有点复杂，但是几个条件最后都是通过obtainRequest来生成request对象的，最后生成的对象是SingleRequest。

那终于可以看下我们这小节的流程到底是怎么执行的，下面是SingleRequest的begin方法:
```
  @Override
  public void begin() {
    synchronized (requestLock) {
      assertNotCallingCallbacks();
      stateVerifier.throwIfRecycled();
      startTime = LogTime.getLogTime();
      
      // 异常情况
      if (model == null) {
        if (Util.isValidDimensions(overrideWidth, overrideHeight)) {
          width = overrideWidth;
          height = overrideHeight;
        }
        int logLevel = getFallbackDrawable() == null ? Log.WARN : Log.DEBUG;
        onLoadFailed(new GlideException("Received null model"), logLevel);
        return;
      }

      if (status == Status.RUNNING) {
        throw new IllegalArgumentException("Cannot restart a running request");
      }

      // 状态异常？
      if (status == Status.COMPLETE) {
        onResourceReady(resource, DataSource.MEMORY_CACHE);
        return;
      }

      status = Status.WAITING_FOR_SIZE;
      if (Util.isValidDimensions(overrideWidth, overrideHeight)) {
        // 已获得view宽高，内部开始执行代码
        onSizeReady(overrideWidth, overrideHeight);
      } else {
        // 还未获得宽高，传入一个SizeReadyCallback，等onMeasure结束(获得宽高)后执行onSizeReady(这个方法就从接口来的)
        target.getSize(this);
      }

      // 这里onLoadStarted注释写的: 生命周期的callBack，点进去看好像有设置placeHoldeer
      if ((status == Status.RUNNING || status == Status.WAITING_FOR_SIZE)
          && canNotifyStatusChanged()) {
        target.onLoadStarted(getPlaceholderDrawable());
      }
    }
  }
```
代码有点复杂了，但就三点，一个是异常情况，第二个是等view measure完成获取宽高，第三个是通知onLoadStarted。

下面就看下但view宽高确定后，如何操作的:
```
  // A callback method that should never be invoked directly. 注释注意下
  @Override
  public void onSizeReady(int width, int height) {
    stateVerifier.throwIfRecycled();
    synchronized (requestLock) {
      // 修改状态到RUNNING
      if (status != Status.WAITING_FOR_SIZE) { return; }
      status = Status.RUNNING;

      float sizeMultiplier = requestOptions.getSizeMultiplier();
      this.width = maybeApplySizeMultiplier(width, sizeMultiplier);
      this.height = maybeApplySizeMultiplier(height, sizeMultiplier);

      // 原来加载交到了engine去执行
      loadStatus = engine.load(...);
      
      // 这里只对同步操作有关系，用于测试是否再主线程执行，不用管它
      if (status != Status.RUNNING) { loadStatus = null; }
    }
  }
```
看完上面代码，也就是说Glide先等view measure完成获取宽高后，将加载任务交到了engine去执行，里面可能是异步执行的。在engine执行过程中，onLoadStarted方法还会做一些操作，例如通知、设置placeHolder之类的。

### engine.load(...)
上面流程讲到了engine里面，感觉东西不会少，新开一小节。下面看下engine里面的load方法:
```
  public <R> LoadStatus load(...) {
    long startTime = VERBOSE_IS_LOGGABLE ? LogTime.getLogTime() : 0;
    
    // 缓存用的key？
    EngineKey key = keyFactory.buildKey(...);

    // 加锁，取内存缓存
    EngineResource<?> memoryResource;
    synchronized (this) {
      memoryResource = loadFromMemory(key, isMemoryCacheable, startTime);
      
      // 没有拿到内存缓存
      if (memoryResource == null) {
        return waitForExistingOrStartNewJob(...);
      }
    }

    // 拿到内存缓存
    cb.onResourceReady(memoryResource, DataSource.MEMORY_CACHE);
    return null;
  }
```
内存缓存我们后面再讲，先看流程走势，这里没有内存缓存的话就到了waitForExistingOrStartNewJob方法:
```
private <R> LoadStatus waitForExistingOrStartNewJob(...) {
    // 先获取了下现在执行的任务
    EngineJob<?> current = jobs.get(key, onlyRetrieveFromCache);
    if (current != null) {
      // 延迟执行？
      current.addCallback(cb, callbackExecutor);
      return new LoadStatus(cb, current);
    }

    // 下载任务？好像不对
    EngineJob<R> engineJob = engineJobFactory.build(...);
    // 解码任务？好像逻辑都在它里面
    DecodeJob<R> decodeJob = decodeJobFactory.build(...);

    jobs.put(key, engineJob);

    // 增加回调
    engineJob.addCallback(cb, callbackExecutor);
    // 启动engineJob
    engineJob.start(decodeJob);

    return new LoadStatus(cb, engineJob);
  }
```
上面代码也就是说engine实际把任务分成了两部分，engineJob和decodeJob，并将decodeJob传到engineJob中，启动engineJob完成加载。

那流程就来到了EngineJob的start方法:
```
  public synchronized void start(DecodeJob<R> decodeJob) {
    this.decodeJob = decodeJob;
    // 根据是否加载缓存选择线程池
    GlideExecutor executor =
        decodeJob.willDecodeFromCache() ? diskCacheExecutor : getActiveSourceExecutor();
    executor.execute(decodeJob);
  }
  
  private GlideExecutor getActiveSourceExecutor() {
    // 上面一个，这里又来三个？
    return useUnlimitedSourceGeneratorPool
        ? sourceUnlimitedExecutor
        : (useAnimationPool ? animationExecutor : sourceExecutor);
  }
```
这就有点懵了，这里出现了四个线程池:

- diskCacheExecutor，硬盘缓存线程池
- sourceUnlimitedExecutor，无限资源线程池？
- animationExecutor，动画线程池？
- sourceExecutor，资源线程池？

不过好在他们四个都是GlideExecutor，那就看下GlideExecutor的execute做了什么:
```
  public void execute(@NonNull Runnable command) {
    // GlideExecutor里面都有代理线程池完成
    delegate.execute(command);
  }
  
  public GlideExecutor build() {
      if (TextUtils.isEmpty(name)) { throw new IllegalArgumentException(...; }
      
      // 线程池的相关参数
      ThreadPoolExecutor executor =
          new ThreadPoolExecutor(
              corePoolSize,
              maximumPoolSize,
              /*keepAliveTime=*/ threadTimeoutMillis,
              TimeUnit.MILLISECONDS,
              new PriorityBlockingQueue<Runnable>(),
              new DefaultThreadFactory(name, uncaughtThrowableStrategy, preventNetworkOperations));

      if (threadTimeoutMillis != NO_THREAD_TIMEOUT) {
        executor.allowCoreThreadTimeOut(true);
      }

      return new GlideExecutor(executor);
    }
```
呃，好像和普通线程池没什么不一样，所以核心内容实际是在decodeJob里面？那就看下decodeJob的run方法做了什么:
```
  public void run() {
    // 不知道什么意思，日志记录器么？
    GlideTrace.beginSectionFormat("DecodeJob#run(model=%s)", model);
    
    // 获取数据
    DataFetcher<?> localFetcher = currentFetcher;
    try {
      if (isCancelled) {
        notifyFailed();
        return;
      }
      
      // 核心代码
      runWrapped();
    } catch (CallbackException e) {
      // 不受Glide控制的异常
      throw e;
    } catch (Throwable t) {
      // 失败回调，可能不安全？
      if (stage != Stage.ENCODE) {
        throwables.add(t);
        notifyFailed();
      }
      
      // 为什么多此一举。。。
      if (!isCancelled) {
        throw t;
      }
      throw t;
    } finally {
      // 加载完清理？
      if (localFetcher != null) {
        localFetcher.cleanup();
      }
      GlideTrace.endSection();
    }
  }
```
run方法里面就是在try和catch中执行了runWrapped方法，处理了下异常以及finally情形。下面看runWrapped方法:
```
  private void runWrapped() {
    switch (runReason) {
      case INITIALIZE:
        stage = getNextStage(Stage.INITIALIZE);
        currentGenerator = getNextGenerator();
        runGenerators();
        break;
      case SWITCH_TO_SOURCE_SERVICE:
        runGenerators();
        break;
      case DECODE_DATA:
        decodeFromRetrievedData();
        break;
      default:
        throw new IllegalStateException("Unrecognized run reason: " + runReason);
    }
  }
```
真复杂，这里的意思感觉就是会执行好几遍，runReason控制当前执行到哪一步，那我们就从INITIALIZE看下去，这里先获取了一个currentGenerator:
```
  private DataFetcherGenerator getNextGenerator() {
    switch (stage) {
      case RESOURCE_CACHE:
        return new ResourceCacheGenerator(decodeHelper, this);
      case DATA_CACHE:
        return new DataCacheGenerator(decodeHelper, this);
      case SOURCE:
        return new SourceGenerator(decodeHelper, this);
      case FINISHED:
        return null;
      default:
        throw new IllegalStateException("Unrecognized stage: " + stage);
    }
  }
```
不是很明白它的作用，继续看INITIALIZE里面的runGenerators方法:
```
private void runGenerators() {
    currentThread = Thread.currentThread();
    startFetchTime = LogTime.getLogTime();
    boolean isStarted = false;
    
    // 所以核心代码就是startNext方法了
    while (!isCancelled
        && currentGenerator != null
        && !(isStarted = currentGenerator.startNext())) {
      
      // 新状态，新currentGenerator
      stage = getNextStage(stage);
      currentGenerator = getNextGenerator();

      // 重新定时？
      if (stage == Stage.SOURCE) {
        reschedule();
        return;
      }
    }
    
    // 到这就是失败了
    if ((stage == Stage.FINISHED || isCancelled) && !isStarted) {
      notifyFailed();
    }

    // Otherwise a generator started a new load and we expect to be called back in
    // onDataFetcherReady.
  }
```
上面代码指示说是currentGenerator的startNext方法起效，也就说说是下面几个DataFetcherGenerator的在执行代码:

ResourceCacheGenerator，从采样、转换过的缓存中取
> Generates DataFetchers from cache files containing downsampled/transformed resource data.

DataCacheGenerator，从未修改的缓存中取
> Generates DataFetchers from cache files containing original unmodified source data.

SourceGenerator，从原始数据中取
> Generates DataFetchers from original source data using registered ModelLoaders and the model provided for the load.

这里就不深入了，大致就是取数据的一些流程，下一节看下网络的，这里我们先看下runWrapped中DECODE_DATA里面的decodeFromRetrievedData方法:
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
在这里通过decodeFromData方法处理了currentGenerator中得到的数据，生成resource资源，并用notifyEncodeAndRelease方法通知数据获取完成:
```
  private void notifyEncodeAndRelease(Resource<R> resource, DataSource dataSource) {
    if (resource instanceof Initializable) {
      ((Initializable) resource).initialize();
    }

    Resource<R> result = resource;
    LockedResource<R> lockedResource = null;
    if (deferredEncodeManager.hasResourceToEncode()) {
      lockedResource = LockedResource.obtain(resource);
      result = lockedResource;
    }

    // 关键生命周期函数，通知Encode完成
    notifyComplete(result, dataSource);

    stage = Stage.ENCODE;
    try {
      if (deferredEncodeManager.hasResourceToEncode()) {
        deferredEncodeManager.encode(diskCacheProvider, options);
      }
    } finally {
      if (lockedResource != null) {
        lockedResource.unlock();
      }
    }
    
    // 做一些释放资源的操作
    onEncodeComplete();
  }
```
这里就直接到了notifyComplete方法，通知资源获取结束了:
```
  private void notifyComplete(Resource<R> resource, DataSource dataSource) {
    setNotifiedOrThrow();
    callback.onResourceReady(resource, dataSource);
  }
```
这里通过callback传递出去，那就是SingleRequest的事了。

### SourceGenerator
前面虽然把engineJob如何把数据获取并传递出去讲完了，但是获取数据的部分省略了，这节来简单看下，看它的startNext方法:
```
  @Override
  public boolean startNext() {
    if (dataToCache != null) {
      Object data = dataToCache;
      dataToCache = null;
      cacheData(data);
    }

    // 还想着用缓存呢
    if (sourceCacheGenerator != null && sourceCacheGenerator.startNext()) {
      return true;
    }
    sourceCacheGenerator = null;

    loadData = null;
    boolean started = false;
    while (!started && hasNextModelLoader()) {
      loadData = helper.getLoadData().get(loadDataListIndex++);
      if (loadData != null
          && (helper.getDiskCacheStrategy().isDataCacheable(loadData.fetcher.getDataSource())
              || helper.hasLoadPath(loadData.fetcher.getDataClass()))) {
        started = true;
        
        // 在这里加载
        startNextLoad(loadData);
      }
    }
    return started;
  }
```
这里经过一系列判断，最后走到了startNextLoad方法:
```
  private void startNextLoad(final LoadData<?> toStart) {
    loadData.fetcher.loadData(
        helper.getPriority(),
        new DataCallback<Object>() {
          @Override
          public void onDataReady(@Nullable Object data) {
            if (isCurrentRequest(toStart)) {
              onDataReadyInternal(toStart, data);
            }
          }

          @Override
          public void onLoadFailed(@NonNull Exception e) {
            if (isCurrentRequest(toStart)) {
              onLoadFailedInternal(toStart, e);
            }
          }
        });
  }
```
先不管其他的，这里加载的对象就是loadData.fetcher了，也就是DataFetcher类，DataFetcher有很多实现类，下面是几个例子:

- StreamLocalUriFetcher：用于从本地文件系统加载图片。
- HttpGlideUrlFetcher：用于从网络加载图片。
- FileDescriptorLocalUriFetcher：用于从本地文件描述符加载图片。
- AssetUriFetcher：用于从应用程序的 assets 文件夹加载图片。
- MediaStoreImageThumbFetcher：用于从 MediaStore 中加载缩略图。
- MediaStoreVideoThumbFetcher：用于从 MediaStore 中加载视频缩略图。

我感觉我们要的是HttpGlideUrlFetcher，下面看下它的loadData方法:
```
  @Override
  public void loadData(
      @NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
    long startTime = LogTime.getLogTime();
    try {
    
      // 带着重定向去下载
      InputStream result = loadDataWithRedirects(glideUrl.toURL(), 0, null, glideUrl.getHeaders());
      callback.onDataReady(result);
    } catch (IOException e) {
      callback.onLoadFailed(e);
    } finally {
      //...
    }
  }
```
最终下载任务到了loadDataWithRedirects方法:
```
  private InputStream loadDataWithRedirects(
      URL url, int redirects, URL lastUrl, Map<String, String> headers) throws IOException {
    if (redirects >= MAXIMUM_REDIRECTS) {
      throw new HttpException("Too many (> " + MAXIMUM_REDIRECTS + ") redirects!");
    } else {
      try {
        // 异常情况
        if (lastUrl != null && url.toURI().equals(lastUrl.toURI())) {
          throw new HttpException("In re-direct loop");
        }
      } catch (URISyntaxException e) {
        // Do nothing, this is best effort.
      }
    }

    // 使用urlConnection去下载
    urlConnection = connectionFactory.build(url);
    for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
      urlConnection.addRequestProperty(headerEntry.getKey(), headerEntry.getValue());
    }
    urlConnection.setConnectTimeout(timeout);
    urlConnection.setReadTimeout(timeout);
    urlConnection.setUseCaches(false);
    urlConnection.setDoInput(true);

    urlConnection.setInstanceFollowRedirects(false);

    // 连接
    urlConnection.connect();
    // 获取流
    stream = urlConnection.getInputStream();
    if (isCancelled) {
      return null;
    }
    
    // 获取状态码
    final int statusCode = urlConnection.getResponseCode();
    if (isHttpOk(statusCode)) {
      return getStreamForSuccessfulRequest(urlConnection);
    } else if (isHttpRedirect(statusCode)) {
      String redirectUrlString = urlConnection.getHeaderField("Location");
      if (TextUtils.isEmpty(redirectUrlString)) {
        throw new HttpException("Received empty or null redirect url");
      }
      URL redirectUrl = new URL(url, redirectUrlString);
      
      // 清理
      cleanup();
      return loadDataWithRedirects(redirectUrl, redirects + 1, url, headers);
    } else if (statusCode == INVALID_STATUS_CODE) {
      throw new HttpException(statusCode);
    } else {
      throw new HttpException(urlConnection.getResponseMessage(), statusCode);
    }
  }
```
看到这里就不深入了吧，毕竟这篇博客讲的是整体流程，到urlConnection去下载我觉得就差不多了。

### onResourceReady
上上节我们讲到engineJob获取到资源后，会通过回调，执行SingleRequest的onResourceReady方法，下面我们看下这个方法:
```
  @Override
  public void onResourceReady(Resource<?> resource, DataSource dataSource) {
    stateVerifier.throwIfRecycled();
    Resource<?> toRelease = null;
    try {
      synchronized (requestLock) {
        loadStatus = null;
        
        // 没获取到资源
        if (resource == null) {
          GlideException exception = new GlideException(...);
          onLoadFailed(exception);
          return;
        }

        // 有resource，但是收到空资源
        Object received = resource.get();
        if (received == null || !transcodeClass.isAssignableFrom(received.getClass())) {
          toRelease = resource;
          this.resource = null;
          GlideException exception = new GlideException(...);
          onLoadFailed(exception);
          return;
        }

        // 有效资源
        if (!canSetResource()) {
          toRelease = resource;
          this.resource = null;
          // We can't put the status to complete before asking canSetResource().
          status = Status.COMPLETE;
          return;
        }

        // 额，这里还有个多态方法
        onResourceReady((Resource<R>) resource, (R) received, dataSource);
      }
    } finally {
      if (toRelease != null) {
        // 设置完回收资源？
        engine.release(toRelease);
      }
    }
  }
```
接着看下另一个onResourceReady方法:
```
  @GuardedBy("requestLock")
  private void onResourceReady(Resource<R> resource, R result, DataSource dataSource) {
    // We must call isFirstReadyResource before setting status.
    boolean isFirstResource = isFirstReadyResource();
    status = Status.COMPLETE;
    this.resource = resource;

    isCallingCallbacks = true;
    try {
      boolean anyListenerHandledUpdatingTarget = false;
      
      // 通知自定义监听？
      if (requestListeners != null) {
        for (RequestListener<R> listener : requestListeners) {
          anyListenerHandledUpdatingTarget |=
              listener.onResourceReady(result, model, target, dataSource, isFirstResource);
        }
      }
      
      // 回调监听？
      anyListenerHandledUpdatingTarget |=
          targetListener != null
              && targetListener.onResourceReady(result, model, target, dataSource, isFirstResource);

      // 可以拦截图像的设置？
      if (!anyListenerHandledUpdatingTarget) {
        // 动画？
        Transition<? super R> animation = animationFactory.build(dataSource, isFirstResource);
        // 这里才加载到目标去
        target.onResourceReady(result, animation);
      }
    } finally {
      isCallingCallbacks = false;
    }

    notifyLoadSuccess();
  }
```
看了下notifyLoadSuccess好像不是通知view更新的，仅仅是对request的Coordinator的通知:
```
  @GuardedBy("requestLock")
  private void notifyLoadSuccess() {
    if (requestCoordinator != null) {
      // 前面那个request的Coordinator
      requestCoordinator.onRequestSuccess(this);
    }
  }
```
所以是在target的onResourceReady方法里面去更新图像的，这里target又很多，我觉得是ImageViewTarget:
```
  @Override
  public void onResourceReady(@NonNull Z resource, @Nullable Transition<? super Z> transition) {
    if (transition == null || !transition.transition(resource, this)) {
      // 不需要动画
      setResourceInternal(resource);
    } else {
      // 带动画
      maybeUpdateAnimatable(resource);
    }
  }

```
我们就看下不带动画的maybeUpdateAnimatable方法:
```
  private void setResourceInternal(@Nullable Z resource) {
    setResource(resource);
    maybeUpdateAnimatable(resource);
  }
  
  // 执行动画
  private void maybeUpdateAnimatable(@Nullable Z resource) {
    if (resource instanceof Animatable) {
      animatable = (Animatable) resource;
      animatable.start();
    } else {
      animatable = null;
    }
  }

  protected abstract void setResource(@Nullable Z resource);
```
这里setResource居然是个抽象方法，那就看下ImageViewTarget的实现类，又有挺多个，我就拿DrawableImageViewTarget看一下:
```
  @Override
  protected void setResource(@Nullable Drawable resource) {
    // 通过ImageView的setImageDrawable设置图片
    view.setImageDrawable(resource);
  }
```

## 小结
终于把Glide的流程走了一遍了，有点马虎，但是我觉得还是挺有用，后面会多写几篇详细看看。

ps. 流程图慢慢画，今天的任务完成了