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