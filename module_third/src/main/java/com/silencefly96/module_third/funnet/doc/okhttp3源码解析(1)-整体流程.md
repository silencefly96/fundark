# okhttp3源码解析-整体流程
## 前言
最近有时间看了看okhttp的源码，之前基本是通过别人的书籍、博客去学习源码的，这次是完全自己去看、去摸索，还挺有意思的，这里稍微记录下。

## okhttp3的主要流程
要研究一个复杂的源码框架，首先要搞定它的主要流程，知道个大概，再去研究细节，这里从网上找了一张图片，我觉得把okhttp的流程画的很清楚了：
![pic](https://img-blog.csdnimg.cn/ba1a19fd5867400c8b52e7dbd17bb510.png =300x)
然后我们再看下okhttp的一般调用情况：
```
// 1，创建OkHttpClient
OkHttpClient httpClient = new OkHttpClient.Builder().build();
// 2，创建Request
Request request = new Request.Builder().url("xxx").build();
// 3，创建Call
Call call = httpClient.newCall(request);
// 4，使用call发起请求：
// 同步使用
Response response = call.execute();
// 异步请求
call.enqueue(callback)
```
下面我们就根据这四个步骤去查看源码，理解其中的设计思想。

## 源码分析
### 创建OkHttpClient
建造者模式，设计模式了，我这里不讲，只讲在OkHttpClient的使用。这里实际就三部分内容：
1. 参数及setter和getter函数
2. 构造函数，用于创建参数的默认值
3. build函数，调用OkHttpClient构造传入builder创建实例
这里就可以实现只设置部分参数创建OkHttpClient实例的功能。
   
### 创建Request
这里也使用了建造者模式，和上面类似，需要注意的是request是不可修改的，看下其说明：
> An HTTP request. Instances of this class are immutable if their body is null or itself immutable.

### 创建Call
前面创建了OkHttpClient和Request的实例，都还没有到核心代码，接下来是实际内容：
```
  @Override public Call newCall(Request request) {
    return RealCall.newRealCall(this, request, false /* for web socket */);
  }
```
这里直接调用RealCall创建了一个RealCall对象，代码进入了RealCall内部的static方法，这里做了两件事：
1. 调用RealCall的构建方法创建了一个RealCall对象
2. 向OkHttpClient中传入了一个eventListener
```
  static RealCall newRealCall(OkHttpClient client, Request originalRequest, boolean forWebSocket) {
    // Safely publish the Call instance to the EventListener.
    RealCall call = new RealCall(client, originalRequest, forWebSocket);
    call.eventListener = client.eventListenerFactory().create(call);
    return call;
  }
```

#### RealCall的构建方法：
```
  private RealCall(OkHttpClient client, Request originalRequest, boolean forWebSocket) {
    this.client = client;
    this.originalRequest = originalRequest;
    this.forWebSocket = forWebSocket;
    this.retryAndFollowUpInterceptor = new RetryAndFollowUpInterceptor(client, forWebSocket);
    this.timeout = new AsyncTimeout() {
      @Override protected void timedOut() {
        cancel();
      }
    };
    this.timeout.timeout(client.callTimeoutMillis(), MILLISECONDS);
  }
```
这里向RealCall内设置了传入的参数，并创建了retryAndFollowUpInterceptor和timeout对象。retryAndFollowUpInterceptor这个拦截器比较重要，需要注意下它是在这里创建的，后面会详解这个拦截器。对于timeout这个属性，我没有花时间去研究，不过可以提供一个别人写的很好的博文，有兴趣的读者可以看下：
> https://juejin.cn/post/6962464239864774664

#### eventListener事件监听
eventListener虽然和请求的主流程没什么太大关系，但是这个也是OkHttp一个强大的功能，它会在每个功能调用的时候提示事件发生的情况，可以查看下其中的方法：
![pic](https://img-blog.csdnimg.cn/6ff2102f088c46aab4482427ccdf5bf3.png)

### 使用call发起请求
前面通过三步操作得到了一个RealCall的实例，最后一步就是通过RealCall去发起同步或异步请求，下面我们先看同步请求，再去看异步请求，虽然异步请求麻烦了一些，但是最后都会汇聚在getResponseWithInterceptorChain方法。

#### 同步请求
同步请求通过调用RealCall的execute方法得到Response，这里使用executed验证了请求是否已执行，并设置了超时计时、事件调用等。
```
  @Override public Response execute() throws IOException {
    synchronized (this) {
      if (executed) throw new IllegalStateException("Already Executed");
      executed = true;
    }
    captureCallStackTrace();
    timeout.enter();
    eventListener.callStart(this);
    try {
      client.dispatcher().executed(this);
      Response result = getResponseWithInterceptorChain();
      if (result == null) throw new IOException("Canceled");
      return result;
    } catch (IOException e) {
      e = timeoutExit(e);
      eventListener.callFailed(this, e);
      throw e;
    } finally {
      client.dispatcher().finished(this);
    }
  }
```
这里我们着重注意try-catch内部的内容，这里又做了两件事，一是调用OkHttpClient内的dispatcher执行当前RealCall，二是通过getResponseWithInterceptorChain获取response。
点开dispatcher的executed方法，里面只是将RealCall对象保存到一个数组里去了：
```
  /** Used by {@code Call#execute} to signal it is in-flight. */
  synchronized void executed(RealCall call) {
    runningSyncCalls.add(call);
  }
```
关于Dispatcher我们后面再讲，先看下getResponseWithInterceptorChain里面的内容：
```
  Response getResponseWithInterceptorChain() throws IOException {
    // Build a full stack of interceptors.
    List<Interceptor> interceptors = new ArrayList<>();
    interceptors.addAll(client.interceptors());
    interceptors.add(retryAndFollowUpInterceptor);
    interceptors.add(new BridgeInterceptor(client.cookieJar()));
    interceptors.add(new CacheInterceptor(client.internalCache()));
    interceptors.add(new ConnectInterceptor(client));
    if (!forWebSocket) {
      interceptors.addAll(client.networkInterceptors());
    }
    interceptors.add(new CallServerInterceptor(forWebSocket));

    Interceptor.Chain chain = new RealInterceptorChain(interceptors, null, null, null, 0,
        originalRequest, this, eventListener, client.connectTimeoutMillis(),
        client.readTimeoutMillis(), client.writeTimeoutMillis());

    return chain.proceed(originalRequest);
  }
```
这里就通过一系列的拦截器，通过责任链的形式处理，最后得到了Response，看起来很简单，我们暂且就认为这里就拿到了Response，责任链内的代码后面博文研究，下面先看异步请求。

#### 异步请求
异步请求通过OkHttpClient的enqueue方法，传入responseCallback得到结果，代码如下：
```
  @Override public void enqueue(Callback responseCallback) {
    synchronized (this) {
      if (executed) throw new IllegalStateException("Already Executed");
      executed = true;
    }
    captureCallStackTrace();
    eventListener.callStart(this);
    client.dispatcher().enqueue(new AsyncCall(responseCallback));
  }
```
这里和同步请求的execute类似，但是最后是通过调用dispatcher的enqueue实现调用，传入了封装responseCallback的AsyncCall对象，下面我们看下AsyncCall对象：
```
final class AsyncCall extends NamedRunnable {
    // 省略部分代码

    /**
     * Attempt to enqueue this async call on {@code executorService}. This will attempt to clean up
     * if the executor has been shut down by reporting the call as failed.
     */
    void executeOn(ExecutorService executorService) {
      assert (!Thread.holdsLock(client.dispatcher()));
      boolean success = false;
      try {
        executorService.execute(this);
        success = true;
      } catch (RejectedExecutionException e) {
        InterruptedIOException ioException = new InterruptedIOException("executor rejected");
        ioException.initCause(e);
        eventListener.callFailed(RealCall.this, ioException);
        responseCallback.onFailure(RealCall.this, ioException);
      } finally {
        if (!success) {
          client.dispatcher().finished(this); // This call is no longer running!
        }
      }
    }

    @Override protected void execute() {
      boolean signalledCallback = false;
      timeout.enter();
      try {
        Response response = getResponseWithInterceptorChain();
        if (retryAndFollowUpInterceptor.isCanceled()) {
          signalledCallback = true;
          responseCallback.onFailure(RealCall.this, new IOException("Canceled"));
        } else {
          signalledCallback = true;
          responseCallback.onResponse(RealCall.this, response);
        }
      } catch (IOException e) {
        e = timeoutExit(e);
        if (signalledCallback) {
          // Do not signal the callback twice!
          Platform.get().log(INFO, "Callback failure for " + toLoggableString(), e);
        } else {
          eventListener.callFailed(RealCall.this, e);
          responseCallback.onFailure(RealCall.this, e);
        }
      } finally {
        client.dispatcher().finished(this);
      }
    }
  }
```
这里省略了部分代码，我们着重关注下executeOn和execute两个方法。实际上AsyncCall就是一个Runnable，它的run方法里面会执行execute方法。
再来看executeOn方法，除去一些异常处理，实际就是调用了executorService的execute方法执行AsyncCall这个Runnable，这里的目的就是提供一个线程池来执行AsyncCall的execute()代码。
继续看execute方法，再次忽略异常处理，实际就是调用了getResponseWithInterceptorChain方法获得response，只是这个execute方法执行在指定线程池，也就是说实现了异步执行。
```
client.dispatcher().enqueue(new AsyncCall(responseCallback));
```
这里分析了AsyncCall这个类对responseCallback的封装，回过头来，我们再看下dispatcher的enqueue方法做了什么：
```
  void enqueue(AsyncCall call) {
    synchronized (this) {
      readyAsyncCalls.add(call);
    }
    promoteAndExecute();
  }
```
这里也比较简单，异步嘛，先加锁，再把AsyncCall存入数组里面，紧接着调用了promoteAndExecute()来推动代码执行。这里我们就在下面的Dispatcher中着重分析了。

#### Dispatcher
前面讲到的同步请求和异步请求，都是通过dispatcher去执行，再通过getResponseWithInterceptorChain拿到Response的，下面我们就来研究下这个Dispatcher，忽略部分功能，我们着重看下下面几个内容。

##### 数据域
```
  private @Nullable ExecutorService executorService;

  /** Ready async calls in the order they'll be run. */
  private final Deque<AsyncCall> readyAsyncCalls = new ArrayDeque<>();

  /** Running asynchronous calls. Includes canceled calls that haven't finished yet. */
  private final Deque<AsyncCall> runningAsyncCalls = new ArrayDeque<>();

  /** Running synchronous calls. Includes canceled calls that haven't finished yet. */
  private final Deque<RealCall> runningSyncCalls = new ArrayDeque<>();
```
这里忽略了最大请求数、同域名最大连接数和无任务时处理的idleCallback。executorService通过调用executorService()方法得到，是异步执行的线程池。
三个Deque对应着运行的同步队列、准备的异步队列、运行的异步队列，注意运行的队列包含了取消但未结束的Call，其中同步队列保存的是RealCall，而异步队列保存的是AsyncCall(Runnable)。

##### 方法域
方法域我们着重介绍下一下promoteAndExecute和finished方法，前面已经讲到同步请求和异步请求调用enqueue和executed只是将任务放到队列中去，异步请求会再执行下promoteAndExecute方法，下面详细介绍：
```
  /**
   * Promotes eligible calls from {@link #readyAsyncCalls} to {@link #runningAsyncCalls} and runs
   * them on the executor service. Must not be called with synchronization because executing calls
   * can call into user code.
   *
   * @return true if the dispatcher is currently running calls.
   */
  private boolean promoteAndExecute() {
    assert (!Thread.holdsLock(this));

    // 从ready列表中拿出call存到执行列表
    List<AsyncCall> executableCalls = new ArrayList<>();
    boolean isRunning;
    synchronized (this) {
      for (Iterator<AsyncCall> i = readyAsyncCalls.iterator(); i.hasNext(); ) {
        AsyncCall asyncCall = i.next();

        if (runningAsyncCalls.size() >= maxRequests) break; // Max capacity.
        if (runningCallsForHost(asyncCall) >= maxRequestsPerHost) continue; // Host max capacity.

        i.remove();
        executableCalls.add(asyncCall);
        runningAsyncCalls.add(asyncCall);
      }
      isRunning = runningCallsCount() > 0;
    }

    // 调度执行
    for (int i = 0, size = executableCalls.size(); i < size; i++) {
      AsyncCall asyncCall = executableCalls.get(i);
      asyncCall.executeOn(executorService());
    }

    return isRunning;
  }
```
promoteAndExecute就分两步，先将异步任务从ready队列中取出，并放到running的队列中，第二步再执行其中的任务，并不是很复杂，executeOn执行就到AsyncCall的execute方法了。
```
  /** Used by {@code AsyncCall#run} to signal completion. */
  void finished(AsyncCall call) {
    finished(runningAsyncCalls, call);
  }

  /** Used by {@code Call#execute} to signal completion. */
  void finished(RealCall call) {
    finished(runningSyncCalls, call);
  }

  private <T> void finished(Deque<T> calls, T call) {
    Runnable idleCallback;
    synchronized (this) {
      if (!calls.remove(call)) throw new AssertionError("Call wasn't in-flight!");
      idleCallback = this.idleCallback;
    }

    boolean isRunning = promoteAndExecute();

    if (!isRunning && idleCallback != null) {
      idleCallback.run();
    }
  }
```
finished方法通过泛型的形式实现了对同步任务和异步任务的结束，这里还运行了下idleCallback。

#### 使用call发起请求——小结
至此，同步请求和异步请求的整体流程就差不多结束了，逻辑进入到了几个拦截器的责任链中，我们下篇博文分析，这里稍微小结下.
同步请求和异步请求，都经过了两步，一是存入dispatcher的队列中，二是通过getResponseWithInterceptorChain拿到结果，异步请求会在dispatcher中通过线程池执行，并将getResponseWithInterceptorChain方法带到异步线程。