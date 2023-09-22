# Glide源码解析(2) Glide的创建及生命周期感知

## 前言
上篇文章我们对Glide的一个简单调用过程进行了追踪，也算对Glide有了个大致的了解。从这篇文章开始，我们继续对Glide进行详细的学习和理解。

首先是with和load过程，在with过程中，我们遇到了几个对象的创建，最终得到RequestManager，通过它去load链接之类的。

几个对象创建顺序:
> Glide -> RequestManagerRetriever -> RequestManager

下面我们就按这个顺序，开始源码之旅吧。

## Glide对象创建
首先看Glide对象的创建:
```
  // Glide单例也是volatile的，禁止指令重排
  private static volatile Glide glide;
  
  @NonNull
  public static Glide get(@NonNull Context context) {
    if (glide == null) {
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
这里用了DCL创建单例，并且glide被标注了volatile，保证线程安全。GeneratedAppGlideModule是一个自动生成的模块，用于自定义功能，我们后续文章讲解。

get中下一步到达:
```
  // 加了个isInitializing来防止多线程创建
  private static volatile boolean isInitializing;
  
  // GuardedBy是Java提供的注解，指需要在synchronized环境中执行
  @GuardedBy("Glide.class")
  private static void checkAndInitializeGlide(
      @NonNull Context context, @Nullable GeneratedAppGlideModule generatedAppGlideModule) {
      
    // 大致意思多线程调用Glide.get(context)，不检查会触发无限递归，why？
    // In the thread running initGlide(), one or more classes may call Glide.get(context).
    // Without this check, those calls could trigger infinite recursion.
    if (isInitializing) {
    
      // 是指用户直接调用get方法吗? 但是Glide.get(context)有DCL校验啊？
      throw new IllegalStateException(
          "You cannot call Glide.get() in registerComponents(),"
              + " use the provided Glide instance instead");
    }
    isInitializing = true;
    initializeGlide(context, generatedAppGlideModule);
    isInitializing = false;
  }
```
这里有点没搞懂，Glide.get(context)中已经加了DCL校验了，为什么还要加个volatile的isInitializing再校验初始化？多线程调用Glide.get(context)应该也是线程安全的吧！无法理解，就当DCL没那么安全吧。

```
  @GuardedBy("Glide.class")
  private static void initializeGlide(
      @NonNull Context context, @Nullable GeneratedAppGlideModule generatedAppGlideModule) {
    initializeGlide(context, new GlideBuilder(), generatedAppGlideModule);
  }
```
initializeGlide中创建了一个GlideBuilder后，到了Glide真正创建的initializeGlide方法:
```
  @GuardedBy("Glide.class")
  @SuppressWarnings("deprecation")
  private static void initializeGlide(
      @NonNull Context context,
      @NonNull GlideBuilder builder,
      @Nullable GeneratedAppGlideModule annotationGeneratedModule) {
      
    // 使用application的context
    Context applicationContext = context.getApplicationContext();
    
    // 兼容v3版本: 只有注解的自定义模块为空(v4版本)，或者注解自定义模块允许了manifest设置模块才从manifest中加载模块
    List<com.bumptech.glide.module.GlideModule> manifestModules = Collections.emptyList();
    if (annotationGeneratedModule == null || annotationGeneratedModule.isManifestParsingEnabled()) {
      manifestModules = new ManifestParser(applicationContext).parse();
    }

    // 看起来很长，实际就是遍历一遍，把manifest中定义的要排除(Excluded)的模块排除
    if (annotationGeneratedModule != null
        && !annotationGeneratedModule.getExcludedModuleClasses().isEmpty()) {
      Set<Class<?>> excludedModuleClasses = annotationGeneratedModule.getExcludedModuleClasses();
      Iterator<com.bumptech.glide.module.GlideModule> iterator = manifestModules.iterator();
      while (iterator.hasNext()) {
        com.bumptech.glide.module.GlideModule current = iterator.next();
        if (!excludedModuleClasses.contains(current.getClass())) {
          continue;
        }
        iterator.remove();
      }
    }
    
    // 设置factory，有的话就是GeneratedRequestManagerFactory，参与创建requestManagerRetriever
    RequestManagerRetriever.RequestManagerFactory factory =
        annotationGeneratedModule != null
            ? annotationGeneratedModule.getRequestManagerFactory()
            : null;
    builder.setRequestManagerFactory(factory);
    
    // 调用模块的applyOptions方法给builder设置参数
    for (com.bumptech.glide.module.GlideModule module : manifestModules) {
      module.applyOptions(applicationContext, builder);
    }
    if (annotationGeneratedModule != null) {
      annotationGeneratedModule.applyOptions(applicationContext, builder);
    }
    
    // 通过GlideBuilder最终创建glide
    Glide glide = builder.build(applicationContext);
    
    // 最终了下代码，应该是给各个GlideModule一个hook方法，默认是empty impl
    for (com.bumptech.glide.module.GlideModule module : manifestModules) {
      try {
        module.registerComponents(applicationContext, glide, glide.registry);
      } catch (AbstractMethodError e) {
        throw new IllegalStateException(“...”, e);
      }
    }
    if (annotationGeneratedModule != null) {
      annotationGeneratedModule.registerComponents(applicationContext, glide, glide.registry);
    }
    
    // 注册ComponentCallbacks，包含两个方法: ComponentCallbacks、onLowMemory
    applicationContext.registerComponentCallbacks(glide);
    Glide.glide = glide;
  }
```
代码挺长，但是和manifestModules的代码我觉得可以忽略，完全是为了兼容v3版本，后面可能会被删除。

剩下的除了设置参数，就是Glide的真实创建了，在GlideBuilder的build方法中:
```
  // 代码很长，就是建造者模式吧，各个参数混个眼熟吧
  @NonNull
  Glide build(@NonNull Context context) {
    // 几个GlideExecutor，线程池
    if (sourceExecutor == null) { sourceExecutor = GlideExecutor.newSourceExecutor(); }
    if (diskCacheExecutor == null) { diskCacheExecutor = GlideExecutor.newDiskCacheExecutor(); }
    if (animationExecutor == null) { animationExecutor = GlideExecutor.newAnimationExecutor(); }

    if (memorySizeCalculator == null) {
      memorySizeCalculator = new MemorySizeCalculator.Builder(context).build();
    }

    // 用于创建网络监听
    if (connectivityMonitorFactory == null) {
      connectivityMonitorFactory = new DefaultConnectivityMonitorFactory();
    }

    // 位图池
    if (bitmapPool == null) {
      int size = memorySizeCalculator.getBitmapPoolSize();
      if (size > 0) {
        bitmapPool = new LruBitmapPool(size);
      } else {
        bitmapPool = new BitmapPoolAdapter();
      }
    }

    // 数组池
    if (arrayPool == null) {
      arrayPool = new LruArrayPool(memorySizeCalculator.getArrayPoolSizeInBytes());
    }

    // 资源池
    if (memoryCache == null) {
      memoryCache = new LruResourceCache(memorySizeCalculator.getMemoryCacheSize());
    }

    // 用于创建硬盘缓存
    if (diskCacheFactory == null) {
      diskCacheFactory = new InternalCacheDiskCacheFactory(context);
    }

    // Glide功能基本都是交给engine处理
    if (engine == null) {
      engine =
          new Engine(
              memoryCache,
              diskCacheFactory,
              diskCacheExecutor,
              sourceExecutor,
              GlideExecutor.newUnlimitedSourceExecutor(),
              animationExecutor,
              isActiveResourceRetentionAllowed);
    }

    if (defaultRequestListeners == null) {
      defaultRequestListeners = Collections.emptyList();
    } else {
      defaultRequestListeners = Collections.unmodifiableList(defaultRequestListeners);
    }

    // RequestManagerRetriever参与了Glide的创建，注意下
    RequestManagerRetriever requestManagerRetriever =
        new RequestManagerRetriever(requestManagerFactory);

    return new Glide(
        context,
        engine,
        memoryCache,
        bitmapPool,
        arrayPool,
        requestManagerRetriever,
        connectivityMonitorFactory,
        logLevel,
        defaultRequestOptionsFactory,
        defaultTransitionOptions,
        defaultRequestListeners,
        isLoggingRequestOriginsEnabled,
        isImageDecoderEnabledForBitmaps);
  }
```
代码很长，大致看下就行，至此Glide对象就创建成功了。

## RequestManagerRetriever

RequestManagerRetriever从字面意思上看，就是获取RequestManager的一个类，实际上它就是。

### 简述
上面Glide的创建过程中讲到了RequestManagerRetriever对象在GlideBuilder的build方法中被创建了:
```
  // GlideBuilder的build方法中
  RequestManagerRetriever requestManagerRetriever =
      new RequestManagerRetriever(requestManagerFactory);
  
  // RequestManagerRetriever构造方法
  public RequestManagerRetriever(@Nullable RequestManagerFactory factory) {
    this.factory = factory != null ? factory : DEFAULT_FACTORY;
    handler = new Handler(Looper.getMainLooper(), this /* Callback */);
  }
```
而在Glide的with流程中，getRequestManagerRetriever对象从Glide中直接取出:
```
  @NonNull
  private static RequestManagerRetriever getRetriever(@Nullable Context context) {
    Preconditions.checkNotNull(context, "...");
    return Glide.get(context).getRequestManagerRetriever();
  }
```
RequestManagerRetriever实际上只有只有一个get方法，但是是多态的，用于获取RequestManager，并且被Glide对应的with方法调用:
```
  // 在Glide类中以静态方法提供
  @NonNull
  public static RequestManager with(@NonNull Context context) {
    return getRetriever(context).get(context);
  }
  
  @NonNull
  public static RequestManager with(@NonNull FragmentActivity activity) {
    return getRetriever(activity).get(activity);
  }
  
  @NonNull
  public static RequestManager with(@NonNull Activity activity) {
    return getRetriever(activity).get(activity);
  }
  
  @NonNull
  public static RequestManager with(@NonNull Fragment fragment) {
    return getRetriever(fragment.getContext()).get(fragment);
  }
  
  @Deprecated
  @NonNull
  public static RequestManager with(@NonNull android.app.Fragment fragment) {
    return getRetriever(fragment.getActivity()).get(fragment);
  }
  
  @NonNull
  public static RequestManager with(@NonNull View view) {
    return getRetriever(view.getContext()).get(view);
  }
```
对于RequestManagerRetriever，我们只要看后面的get方法，它实际对于着RequestManagerRetriever的六个get方法:
```
  public RequestManager get(@NonNull Context context) {...}
  public RequestManager get(@NonNull FragmentActivity activity) {...}
  public RequestManager get(@NonNull Activity activity) {...}
  public RequestManager get(@NonNull Fragment fragment) {...}
  public RequestManager get(@NonNull android.app.Fragment fragment) {...}
  public RequestManager get(@NonNull View view) {...}
```
下面就来讲讲其中功能。

### 功能
#### get(Context)
下面先来看下context参数的get方法:
```
  @NonNull
  public RequestManager get(@NonNull Context context) {
    if (context == null) {
      throw new IllegalArgumentException("You cannot start a load on a null Context");
      
      // 主线程，但是不是Application的context，要区分处理
    } else if (Util.isOnMainThread() && !(context instanceof Application)) {
    
      if (context instanceof FragmentActivity) {
        return get((FragmentActivity) context);
        
      } else if (context instanceof Activity) {
        return get((Activity) context);
        
        // 不是activity但是包含了contextImpl的context，service或者broadcast？view的context？
      } else if (context instanceof ContextWrapper
          // Only unwrap a ContextWrapper if the baseContext has a non-null application context.
          // Context#createPackageContext may return a Context without an Application instance,
          // in which case a ContextWrapper may be used to attach one.
          && ((ContextWrapper) context).getBaseContext().getApplicationContext() != null) {
          
        // 重新调回该方法  
        return get(((ContextWrapper) context).getBaseContext());
      }
    }

    // 子线程、Application的context
    return getApplicationManager(context);
  }
```
看来核心是getApplicationManager方法，其他的调用有另外的get方法实现。
```
  @NonNull
  private RequestManager getApplicationManager(@NonNull Context context) {
    // Either an application context or we're on a background thread.
    if (applicationManager == null) {
      synchronized (this) {
        if (applicationManager == null) {
        
          // Glide在这应该是已经完成了单例初始化
          Glide glide = Glide.get(context.getApplicationContext());
          
          // factory为构造方法来的，没设置的话默认为DEFAULT_FACTORY(直接new)
          // 因为不是fragment或者activity，使用application的生命周期
          applicationManager = factory.build(
                  glide,
                  new ApplicationLifecycle(),
                  new EmptyRequestManagerTreeNode(),
                  context.getApplicationContext());
        }
      }
    }

    return applicationManager;
  }
```
这里就是使用factory去创建了一个applicationManager，使用application的生命周期。默认就是用DEFAULT_FACTORY去new一个:
```
  private static final RequestManagerFactory DEFAULT_FACTORY =
      new RequestManagerFactory() {
        @NonNull
        @Override
        public RequestManager build(
            @NonNull Glide glide,
            @NonNull Lifecycle lifecycle,
            @NonNull RequestManagerTreeNode requestManagerTreeNode,
            @NonNull Context context) {
          return new RequestManager(glide, lifecycle, requestManagerTreeNode, context);
        }
      };
```
#### get(FragmentActivity)
下一个是传入FragmentActivity的情况:
```
  @NonNull
  public RequestManager get(@NonNull FragmentActivity activity) {
    if (Util.isOnBackgroundThread()) {
      // 子线程用application代码
      return get(activity.getApplicationContext());
    } else {
      assertNotDestroyed(activity);
      FragmentManager fm = activity.getSupportFragmentManager();
      
      // 通过传入FragmentManager去获取
      return supportFragmentGet(activity, fm, /*parentHint=*/ null, isActivityVisible(activity));
    }
  }
```
下面看下supportFragmentGet方法:
```
  @NonNull
  private RequestManager supportFragmentGet(
      @NonNull Context context,
      @NonNull FragmentManager fm,
      @Nullable Fragment parentHint,
      boolean isParentVisible) {
      
    // 获取一个support的fragment进行生命周期管理
    SupportRequestManagerFragment current =
        getSupportRequestManagerFragment(fm, parentHint, isParentVisible);
    
    // 还是通过factory创建
    RequestManager requestManager = current.getRequestManager();
    if (requestManager == null) {
      // TODO(b/27524013): Factor out this Glide.get() call.
      Glide glide = Glide.get(context);
      requestManager =
          factory.build(
              glide, current.getGlideLifecycle(), current.getRequestManagerTreeNode(), context);
      current.setRequestManager(requestManager);
    }
    
    return requestManager;
  }
```
还是通过factory去创建RequestManager，但是传入的是一个fragment的生命周期。

这里就要着重说一下了，在传入FragmentActivity的context的时候，Glide会在它里面创建一个fragment，并通过这个fragment的生命周期去处理事务。

这个用来获取生命周期感知的fragment的创建方法如下:
```
  @NonNull
  private SupportRequestManagerFragment getSupportRequestManagerFragment(
      @NonNull final FragmentManager fm, @Nullable Fragment parentHint, boolean isParentVisible) {
    
    // 先通过TAG找一下创建了没有，没的话再创建
    SupportRequestManagerFragment current =
        (SupportRequestManagerFragment) fm.findFragmentByTag(FRAGMENT_TAG);
    if (current == null) {
    
      // 这里还有道缓存呢
      current = pendingSupportRequestManagerFragments.get(fm);
      if (current == null) {
        
        // 直接创建fragment
        current = new SupportRequestManagerFragment();
        current.setParentFragmentHint(parentHint);
        if (isParentVisible) {
          current.getGlideLifecycle().onStart();
        }
        
        // 存缓存
        pendingSupportRequestManagerFragments.put(fm, current);
        // 添加到事务并提交
        fm.beginTransaction().add(current, FRAGMENT_TAG).commitAllowingStateLoss();
        // 添加成功后，从pendingSupportRequestManagerFragments中移除？
        handler.obtainMessage(ID_REMOVE_SUPPORT_FRAGMENT_MANAGER, fm).sendToTarget();
      }
    }
    return current;
  }
```
#### get(Fragment | android.app.Fragment)
这里两个Fragment的代码都差不多，下面一起看下:
```
  @NonNull
  public RequestManager get(@NonNull Fragment fragment) {
    Preconditions.checkNotNull(fragment.getContext(), "...");
    if (Util.isOnBackgroundThread()) {
      // 子线程
      return get(fragment.getContext().getApplicationContext());
    } else {
      // 同样通过FragmentManager创建fragment实现
      FragmentManager fm = fragment.getChildFragmentManager();
      return supportFragmentGet(fragment.getContext(), fm, fragment, fragment.isVisible());
    }
  }
  
  // 被标记为废弃了
  @Deprecated
  @NonNull
  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
  public RequestManager get(@NonNull android.app.Fragment fragment) {
    if (fragment.getActivity() == null) { throw new IllegalArgumentException( "..."); }
    
    // Nested Fragment（Fragment内部嵌套Fragment的能力）是Android 4.2提出的
    if (Util.isOnBackgroundThread() || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
      return get(fragment.getActivity().getApplicationContext());
    } else {
      android.app.FragmentManager fm = fragment.getChildFragmentManager();
      return fragmentGet(fragment.getActivity(), fm, fragment, fragment.isVisible());
    }
  }
```
Fragment最后的调用和FragmentActivity一样，都是通过supportFragmentGet方法。

而android.app.Fragment就有点不太一样了。首先，这里限制了Android4.2，因为要用到fragment中嵌套fragment; 然后，获取RequestManager的方法变成了fragmentGet:
```
  // 被标记为废弃了
  @Deprecated
  @NonNull
  private RequestManager fragmentGet(
      @NonNull Context context,
      @NonNull android.app.FragmentManager fm,
      @Nullable android.app.Fragment parentHint,
      boolean isParentVisible) {
    
    // 和getSupportRequestManagerFragment类似
    RequestManagerFragment current = getRequestManagerFragment(fm, parentHint, isParentVisible);
    
    // 创建方式也和SupportRequestManagerFragment类似
    RequestManager requestManager = current.getRequestManager();
    if (requestManager == null) {
      // TODO(b/27524013): Factor out this Glide.get() call.
      Glide glide = Glide.get(context);
      requestManager =
          factory.build(
              glide, current.getGlideLifecycle(), current.getRequestManagerTreeNode(), context);
      current.setRequestManager(requestManager);
    }
    return requestManager;
  }
```
下面是类似getSupportRequestManagerFragment的getRequestManagerFragment方法:
```
  @NonNull
  private RequestManagerFragment getRequestManagerFragment(
      @NonNull final android.app.FragmentManager fm,
      @Nullable android.app.Fragment parentHint,
      boolean isParentVisible) {
      
    RequestManagerFragment current = (RequestManagerFragment) fm.findFragmentByTag(FRAGMENT_TAG);
    if (current == null) {

      current = pendingRequestManagerFragments.get(fm);
      if (current == null) {
      
        // 创建的是RequestManagerFragment
        current = new RequestManagerFragment();
        current.setParentFragmentHint(parentHint);
        if (isParentVisible) {
          current.getGlideLifecycle().onStart();
        }
        pendingRequestManagerFragments.put(fm, current);
        fm.beginTransaction().add(current, FRAGMENT_TAG).commitAllowingStateLoss();
        handler.obtainMessage(ID_REMOVE_FRAGMENT_MANAGER, fm).sendToTarget();
      }
    }
    return current;
  }
```
#### get(Activity)
对于Activity的情况就比较简单了:
```
  @NonNull
  public RequestManager get(@NonNull Activity activity) {
    if (Util.isOnBackgroundThread()) {
      // 子线程
      return get(activity.getApplicationContext());
    } else {
      assertNotDestroyed(activity);
      
      // 直接交给activity的FragmentManager去做了
      android.app.FragmentManager fm = activity.getFragmentManager();
      
      // 调用的和上面android.app.Fragment一样
      return fragmentGet(activity, fm, /*parentHint=*/ null, isActivityVisible(activity));
    }
  }
```
#### get(View)
这里把View的情形放到最后讲，这个方法需要结合下上面的内容一起看:
```
  @NonNull
  public RequestManager get(@NonNull View view) {
    if (Util.isOnBackgroundThread()) {
      // 子线程
      return get(view.getContext().getApplicationContext());
    }

    Preconditions.checkNotNull(view);
    Preconditions.checkNotNull(view.getContext(), "。。。");
    
    // 查找view对应的activity
    Activity activity = findActivity(view.getContext());
    // The view might be somewhere else, like a service.
    if (activity == null) {
      return get(view.getContext().getApplicationContext());
    }

    // Support Fragments.
    // 大致意思就是在FragmentActivity中可能有non-support Fragments，但是情况少且费时费力，把它丢给activity的情形处理
    // Although the user might have non-support Fragments attached to FragmentActivity, searching
    // for non-support Fragments is so expensive pre O and that should be rare enough that we
    // prefer to just fall back to the Activity directly.
    if (activity instanceof FragmentActivity) {
      Fragment fragment = findSupportFragment(view, (FragmentActivity) activity);
      return fragment != null ? get(fragment) : get((FragmentActivity) activity);
    }

    // Standard Fragments.
    android.app.Fragment fragment = findFragment(view, activity);
    if (fragment == null) {
      return get(activity);
    }
    return get(fragment);
  }
```
## RequestManager

### 生命周期感知
前面花了那么长的篇幅讲解RequestManagerRetriever中获取RequestManager的get方法，我们应该就能知道RequestManager一个重要的功能就是生命周期感知了。

实际上没有自定义factory的时候，都是通过DEFAULT_FACTORY创建RequestManager的:
```
  private static final RequestManagerFactory DEFAULT_FACTORY =
      new RequestManagerFactory() {
        @NonNull
        @Override
        public RequestManager build(
            @NonNull Glide glide,
            @NonNull Lifecycle lifecycle,
            @NonNull RequestManagerTreeNode requestManagerTreeNode,
            @NonNull Context context) {
          return new RequestManager(glide, lifecycle, requestManagerTreeNode, context);
        }
      };
```
对上面总结下，大致有三种生命周期(Lifecycle):
- ApplicationLifecycle: 子线程、application、view、其他情况
- android.app.Fragment: android.app.Fragment(高于Android4.2)、Activity、view
- SupportFragment: FragmentActivity、SupportFragment、view

实际上两个Fragment都是用的ActivityFragmentLifecycle，application对应的是ApplicationLifecycle。

先看下ApplicationLifecycle:
```
class ApplicationLifecycle implements Lifecycle {
  @Override
  public void addListener(@NonNull LifecycleListener listener) {
    // 直接开始
    listener.onStart();
  }

  @Override
  public void removeListener(@NonNull LifecycleListener listener) {
    // Do nothing.
  }
}
```
哈哈，就这样嘛-_-||，什么都没做，自生自灭是吧！看来它不是重点，重点应该是fragment的生命周期:
```
class ActivityFragmentLifecycle implements Lifecycle {
  // 通过WeakHashMap创建Set，实现弱引用，good!
  private final Set<LifecycleListener> lifecycleListeners =
      Collections.newSetFromMap(new WeakHashMap<LifecycleListener, Boolean>());
      
  private boolean isStarted;
  private boolean isDestroyed;

  @Override
  public void addListener(@NonNull LifecycleListener listener) {
    lifecycleListeners.add(listener);

    // 添加后立即通知一次
    if (isDestroyed) {
      listener.onDestroy();
    } else if (isStarted) {
      listener.onStart();
    } else {
      listener.onStop();
    }
  }

  @Override
  public void removeListener(@NonNull LifecycleListener listener) {
    lifecycleListeners.remove(listener);
  }

  void onStart() {
    isStarted = true;
    for (LifecycleListener lifecycleListener : Util.getSnapshot(lifecycleListeners)) {
      lifecycleListener.onStart();
    }
  }

  void onStop() {
    isStarted = false;
    for (LifecycleListener lifecycleListener : Util.getSnapshot(lifecycleListeners)) {
      lifecycleListener.onStop();
    }
  }

  void onDestroy() {
    isDestroyed = true;
    for (LifecycleListener lifecycleListener : Util.getSnapshot(lifecycleListeners)) {
      lifecycleListener.onDestroy();
    }
  }
}
```
也就是说Glide会监听fragment三个生命周期方法: onStart、onStop、onDestroy，并对request做出一些响应。

下面看下SupportRequestManagerFragment中的ActivityFragmentLifecycle是怎么处理的:
```
  @NonNull
  ActivityFragmentLifecycle getGlideLifecycle() {
    return lifecycle;
  }
  
  @Override
  public void onStart() {
    super.onStart();
    lifecycle.onStart();
  }

  @Override
  public void onStop() {
    super.onStop();
    lifecycle.onStop();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    lifecycle.onDestroy();
    unregisterFragmentWithRoot();
  }
```
也是挺简单的，在对于生命周期方法里面调用lifecycle对于的方法就是了。

### 生命周期处理
上面讲到了RequestManager如何通过fragment(主要是这个)去感知start、stop、destroyed，下面就来看下感知了生命周期变化后，RequestManager是怎么做的。

先看构造方法和lifecycle有关的内容:
```
    // 构造方法内
    if (Util.isOnBackgroundThread()) {
      
      mainHandler.post(addSelfToLifecycle);
    } else {
      lifecycle.addListener(this);
    }
    lifecycle.addListener(connectivityMonitor);
    
    // 在主线程把自身当成listener监听生命周期
    private final Runnable addSelfToLifecycle =
      new Runnable() {
        @Override
        public void run() {
          lifecycle.addListener(RequestManager.this);
        }
      };
```
这里向lifecycle内注册了两个listener，一个是RequestManager自身，并且通过主线程去注册的，另一个是connectivityMonitor。

先看RequestManager是如何处理生命周期变化的:
```
  @Override
  public synchronized void onStart() {
    resumeRequests();
    targetTracker.onStart();
  }
  
    @Override
  public synchronized void onStop() {
    pauseRequests();
    targetTracker.onStop();
  }
  
  @Override
  public synchronized void onDestroy() {
    targetTracker.onDestroy();
    
    // 清空所有请求
    for (Target<?> target : targetTracker.getAll()) {
      clear(target);
    }
    targetTracker.clear();
    requestTracker.clearRequests();
    
    // 取消两个监听
    lifecycle.removeListener(this);
    lifecycle.removeListener(connectivityMonitor);
    
    // 移除添加RequestManager到lifecycle的runnable，还没执行完的？
    mainHandler.removeCallbacks(addSelfToLifecycle);
    // 从glide的managers列表中删除自身引用
    glide.unregisterRequestManager(this);
  }
```
大致就是通知targetTracker生命周期变化，在onDestroy时特别一点，会做一个释放资源的动作。

继续看下connectivityMonitor中时如何对生命周期做处理的:
```
    // 通过ConnectivityMonitorFactory创建，来自gilde.getConnectivityMonitorFactory()
    // 前面应该混了个眼熟，时GlideBuilder里面创建的，默认是DefaultConnectivityMonitorFactory
    connectivityMonitor = factory.build(
        context.getApplicationContext(),
        new RequestManagerConnectivityListener(requestTracker));
```
下面看下默认的DefaultConnectivityMonitorFactory如何创建connectivityMonitor:
```
  // 获取网络状态权限的字符串
  private static final String NETWORK_PERMISSION = "android.permission.ACCESS_NETWORK_STATE";
  
  @NonNull
  @Override
  public ConnectivityMonitor build(
      @NonNull Context context, @NonNull ConnectivityMonitor.ConnectivityListener listener) {
    
    // 检查是否有获取网络状态的权限
    int permissionResult = ContextCompat.checkSelfPermission(context, NETWORK_PERMISSION);
    boolean hasPermission = permissionResult == PackageManager.PERMISSION_GRANTED;
    
    // 有权限才返回有功能的DefaultConnectivityMonitor，NullConnectivityMonitor内所有方法都是空方法
    return hasPermission
        ? new DefaultConnectivityMonitor(context, listener)
        : new NullConnectivityMonitor();
  }
```
DefaultConnectivityMonitorFactory原来是利用工厂模式，生成有权限和没权限的两种ConnectivityMonitor，又学到了啊！

下面继续看下DefaultConnectivityMonitor，主要是下面四个方法:
```
  // 监听网络变化的动态广播
  private final BroadcastReceiver connectivityReceiver =
      (context, intent) -> {
          boolean wasConnected = isConnected;
          isConnected = isConnected(context);
          if (wasConnected != isConnected) {
            listener.onConnectivityChanged(isConnected);
          }
      };
  
  // 注册广播    
  private void register() {
    if (isRegistered) { return; }

    // 先用ConnectivityManager直接获取网络状态
    isConnected = isConnected(context);
    try {
      
      // 通过注册广播，监听网络变化，获得实时的网络状态
      context.registerReceiver(
          connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
      isRegistered = true;
    } catch (SecurityException e) {
      // See #1417, registering the receiver can throw SecurityException.
    }
  }

  // 取消注册广播
  private void unregister() {
    if (!isRegistered) { return; }
    context.unregisterReceiver(connectivityReceiver);
    isRegistered = false;
  }
  
  @Synthetic
  // 权限在factory校验了
  @SuppressLint("MissingPermission")
  boolean isConnected(@NonNull Context context) {
  
    // 广播是被动获取，这里ConnectivityManager是直接获取
    ConnectivityManager connectivityManager =
        Preconditions.checkNotNull(
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
    
    // networkInfo包含了当前网络状态信息
    NetworkInfo networkInfo;
    try {
      networkInfo = connectivityManager.getActiveNetworkInfo();
    } catch (RuntimeException e) {
      // #1405 shows that this throws a SecurityException.
      // b/70869360 shows that this throws NullPointerException on APIs 22, 23, and 24.
      // b/70869360 also shows that this throws RuntimeException on API 24 and 25.
      return true;
    }
    return networkInfo != null && networkInfo.isConnected();
  }
```
也就是说DefaultConnectivityMonitor会在register的时候，先通过ConnectivityManager获取当前网络isConnected，随后通过注册动态广播的形式，去动态接受网络变化，更新isConnected。而在unregister的时候，取消广播注册。

那么register和unregister是什么时候调用的呢？不要忘了这节内容是生命周期处理，在DefaultConnectivityMonitor中还有三个感知生命周期变化的方法:
```
  @Override
  public void onStart() {
    register();
  }

  @Override
  public void onStop() {
    unregister();
  }

  @Override
  public void onDestroy() {
    // Do nothing.
  }
```
也就是说当页面start的时候开始监听网络状态，在页面stop的时候取消监听，网络状态放在isConnected变量中。

到这RequestManager中两个处理生命周期的listener就看完了，不过也不是完全完了，在RequestManager中生命周期的处理又传递到了targetTracker，不过这个后面再讲了。

### load过程
看完RequestManager的生命周期管理，我们继续看下RequestManager的load过程，上一篇文章我们简单看了下，好像很简单，不过这里还是要认真分析下。

load方法有很多个重载方法:
- load(@Nullable Bitmap bitmap)
- load(@Nullable Drawable drawable)
- load(@Nullable String string)
- load(@Nullable Uri uri)
- load(@Nullable File file)
- load(@RawRes @DrawableRes @Nullable Integer resourceId)
- load(@Nullable URL url)
- load(@Nullable byte[] model)
- load(@Nullable Object model)

这么多方法，实际都是交给asDrawable()的load去处理的:
```
  @NonNull
  @CheckResult
  public RequestBuilder<Drawable> asDrawable() {
    return as(Drawable.class);
  }
  
  @NonNull
  @CheckResult
  public <ResourceType> RequestBuilder<ResourceType> as(
      @NonNull Class<ResourceType> resourceClass) {
    return new RequestBuilder<>(glide, this, resourceClass, context);
  }
```
实际就是创建了一个RequestBuilder去load，这里就是主流程了，先不讲，后面再看。

RequestManager除了上面生命周期管理和load的调用，最重要的就是request的管理了，这个下篇文章讲。

## 小结
篇幅较长了，这里做个小结吧！其实这篇文章包含了with和load两个过程，当然load部分比较少，主要还是在讲with过程的三个对象: Glide、RequestManagerRetriever以及RequestManager。

RequestManager只讲了部分内容，我们下篇文章继续说。