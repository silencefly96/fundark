# okhttp3源码解析(6)-ConnectionPool、StreamAllocation补充
## 前言
上一篇文章把RealConnection和Http2Connection讲了讲，写得有点又臭又长了，这篇主要讲讲ConnectionPool，感觉会稍微简单点，顺带把Internal看看，并补一补StreamAllocation漏掉得一些东西。

## ConnectionPool
ConnectionPool是在OkHttpClient.Builder里面用默认构造方法创建的，设置最大空闲连接数和保持连接的时间(5分钟)。
```
    public ConnectionPool() {
        this(5, 5, TimeUnit.MINUTES);
    }

    public ConnectionPool(int maxIdleConnections, long keepAliveDuration, TimeUnit timeUnit) {
        this.maxIdleConnections = maxIdleConnections;
        this.keepAliveDurationNs = timeUnit.toNanos(keepAliveDuration);

        // Put a floor on the keep alive duration, otherwise cleanup will spin loop.
        if (keepAliveDuration <= 0) {
            throw new IllegalArgumentException("keepAliveDuration <= 0: " + keepAliveDuration);
        }
    }
```
下面来好好介绍下ConnectionPool的一些内容，代码从上往下看吧。

### 清理线程
ConnectionPool上来就是个线程池配Runnable，还是有点懵逼的，不过首先我们要知道它们的作用，即cleanup工作。

第一个线程池是直接创建的，我还记得有个阿里巴巴手册好像也是写的线程池要手动创建，要明白其中用的东西。
```
    // 配合下面的cleanupRunnable用来清楚超时的Connection，SynchronousQueue所以保证了只有一个线程嘛？
    private static final Executor executor = new ThreadPoolExecutor(0 /* corePoolSize */,
            Integer.MAX_VALUE /* maximumPoolSize */, 60L /* keepAliveTime */, TimeUnit.SECONDS,
            new SynchronousQueue<Runnable>(), Util.threadFactory("OkHttp ConnectionPool", true));
```
下面就是对应的runnable，maxIdleConnections和keepAliveDurationNs是构造传进来的。
```
    /** The maximum number of idle connections for each address. */
    private final int maxIdleConnections;
    private final long keepAliveDurationNs;
    // 用来清理connectionPool内的connection，清理一个只会会在延迟时间后清理下一个
    private final Runnable cleanupRunnable = new Runnable() {
        @Override public void run() {
            while (true) {
                // 得到延迟执行的时间
                long waitNanos = cleanup(System.nanoTime());
                // 对应没有connection的情况，会停止这个runnable，在put方法内会重新开始
                if (waitNanos == -1) return;
                if (waitNanos > 0) {
                    // 分成秒和纳秒两部分，Java的wait方法需要，接收纳秒范围0-999999
                    long waitMillis = waitNanos / 1000000L;
                    waitNanos -= (waitMillis * 1000000L);
                    // 连接池是全局单例？使用累锁进行卡线程
                    synchronized (ConnectionPool1.this) {
                        try {
                            ConnectionPool1.this.wait(waitMillis, (int) waitNanos);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }
        }
    };
```
这里就是清除的代码，不过放在这里看得不是很明白，这里只是数据域，接下来我们开始追踪到底是在哪调用的，会找到put方法;
```
    // 通过Internal在StreamAllocation的findConnection中使用
    void put(RealConnection connection) {
        assert (Thread.holdsLock(this));
        // 启动清理connection的线程，正常情况按keepAliveDurationNs周期执行
        if (!cleanupRunning) {
            cleanupRunning = true;
            executor.execute(cleanupRunnable);
        }
        connections.add(connection);
    }
```
Internal方法暂时不说，这里会判断下清理线程是否开启了，没开启的话，就运行cleanupRunnable，并将connection添加到ConnectionPool1中。



下面我们再回过头来分析cleanupRunnable，注释写的很清楚了，我们进去看cleanup方法，这里才是清除操作的核心所在：
```
    long cleanup(long now) {
        int inUseConnectionCount = 0;
        int idleConnectionCount = 0;
        RealConnection longestIdleConnection = null;
        long longestIdleDurationNs = Long.MIN_VALUE;

        // 找到要去除的connection，或者下一个要调用驱除的时间
        // Find either a connection to evict, or the time that the next eviction is due.
        synchronized (this) {
            for (Iterator<RealConnection> i = connections.iterator(); i.hasNext(); ) {
                RealConnection connection = i.next();

                // 修剪任何泄漏的分配(整理connection内的StreamAllocation)，返回正在使用的流的数量，并对泄露情况处理
                // If the connection is in use, keep searching.
                if (pruneAndGetAllocationCount(connection, now) > 0) {
                    inUseConnectionCount++;
                    continue;
                }

                // connection没有分配流，即该connection不在使用了
                idleConnectionCount++;

                // 拿到空闲最久的connection，只拿一个？
                // If the connection is ready to be evicted, we're done.
                long idleDurationNs = now - connection.idleAtNanos;
                if (idleDurationNs > longestIdleDurationNs) {
                    longestIdleDurationNs = idleDurationNs;
                    longestIdleConnection = connection;
                }
            }

            // 对当前最长空闲的connection处理，超时、超多时移除该connection
            // 这里会return 0 => 直接进入cleanupRunnable下一个cleanup
            if (longestIdleDurationNs >= this.keepAliveDurationNs
                    || idleConnectionCount > this.maxIdleConnections) {
                // We've found a connection to evict. Remove it from the list, then close it below (outside
                // of the synchronized block).
                connections.remove(longestIdleConnection);

                // 最长空闲的connection还没超时，计算下下次触发cleanup的等待时间
            } else if (idleConnectionCount > 0) {
                // A connection will be ready to evict soon.
                return keepAliveDurationNs - longestIdleDurationNs;

                // connection还在使用，进入下一个keepAliveDurationNs周期的cleanup
            } else if (inUseConnectionCount > 0) {
                // All connections are in use. It'll be at least the keep alive duration 'til we run again.
                return keepAliveDurationNs;

                // 当前connectionPool里面没有connection，return -1 => cleanupRunnable周期循环结束
            } else {
                // 为什么写在这，不写到cleanupRunnable里面去。。。
                // No connections, idle or in use.
                cleanupRunning = false;
                return -1;
            }
        }

        // 对应上面if第一个情况，所以最长等待的connection需要关闭socket
        closeQuietly(longestIdleConnection.socket());

        // Cleanup again immediately.
        return 0;
    }
```
这里比较长，可以结合注释看下，大致内容就是遍历当前池中的connection，看看有没有使用(需不需要使用)了，一次关闭一个，关闭那个到空闲最久的connection，并得到一个延迟时间，来关闭下一个connection。

为什么括号里有“需不需要使用”，这里我们要看下pruneAndGetAllocationCount这个方法了，发生泄露的时候他也会处理下：
```
    // 修剪任何泄漏的分配，然后返回连接上剩余的活动分配数。如果连接正在跟踪分配但应用程序代码已放弃它们，则分配会泄漏。
    // 泄漏检测是不精确的并且依赖于垃圾收集。
    /**
     * Prunes any leaked allocations and then returns the number of remaining live allocations on
     * {@code connection}. Allocations are leaked if the connection is tracking them but the
     * application code has abandoned them. Leak detection is imprecise and relies on garbage
     * collection.
     */
    private int pruneAndGetAllocationCount(RealConnection connection, long now) {
        // RealConnection中储存的流的情况，在ConnectionPool的get方法中创建的弱引用
        List<Reference<StreamAllocation>> references = connection.allocations;
        for (int i = 0; i < references.size(); ) {
            Reference<StreamAllocation> reference = references.get(i);

            // 流还在使用
            if (reference.get() != null) {
                i++;
                continue;
            }

            // 流已经被GC了，弱引用还存在，内存泄露了，可能是没有关闭response body的流
            // We've discovered a leaked allocation. This is an application bug.
            StreamAllocation.StreamAllocationReference streamAllocRef =
                    (StreamAllocation.StreamAllocationReference) reference;
            String message = "A connection to " + connection.route().address().url()
                    + " was leaked. Did you forget to close a response body?";
            Platform.get().logCloseableLeak(message, streamAllocRef.callStackTrace);

            // 没关闭response body的流所以不能创建新流了？等着已经有的流关闭？
            references.remove(i);
            connection.noNewStreams = true;

            // If this was the last allocation, the connection is eligible for immediate eviction.
            if (references.isEmpty()) {
                // 用当前的time减去连接保持的时间，修改开始的time，在cleanup里面就会认为这个连接正好超时了，会被清理
                connection.idleAtNanos = now - keepAliveDurationNs;
                return 0;
            }
        }

        return references.size();
    }
```
这里就是说的一个connection里面可能有多个StreamAllocation，里面如果有泄露的话(response body)，需要将这个StreamAllocation去除掉，然后返回还在使用中的StreamAllocation数量。

到这清理的功能就看完了，下面看下其他数据域。

### 其他数据域
```
    // 内部保存的RealConnection，遍历比较多所以用ArrayDeque不用LinkedList
    private final Deque<RealConnection> connections = new ArrayDeque<>();
    // RouteDatabase是对失败Route的一个记录，会在Internal中向外公开
    // 在StreamAllocation中参与routeSelector的创建，在findConnection中记录成功，在routeSelector中会有连接失败
    final RouteDatabase routeDatabase = new RouteDatabase();
    boolean cleanupRunning;
```
cleanupRunning上面已经讲到了，connections也比较简单，上面put方法也是放在它里面，注意下遍历比较多所以用的ArrayDeque，routeDatabase比较有意思，我们这里追踪下。
```
public final class RouteDatabase {
  private final Set<Route> failedRoutes = new LinkedHashSet<>();

  /** Records a failure connecting to {@code failedRoute}. */
  public synchronized void failed(Route failedRoute) {
    failedRoutes.add(failedRoute);
  }

  /** Records success connecting to {@code route}. */
  public synchronized void connected(Route route) {
    failedRoutes.remove(route);
  }

  /** Returns true if {@code route} has failed recently and should be avoided. */
  public synchronized boolean shouldPostpone(Route route) {
    return failedRoutes.contains(route);
  }
}
```
里面实现比较简单，但是我发现前面文章漏了很多东西，所以这里值得我追踪下，这里会跳到Internal里面，OkHttpClient -> Internal:
```
  static {
    Internal.instance = new Internal() {
      //...其他代码
    
      @Override public RouteDatabase routeDatabase(ConnectionPool connectionPool) {
        return connectionPool.routeDatabase;
      }
    
    }
  }
```
这里Internal是在OkHttpClient里面的一个匿名类，并在static块中完成初始化，看看它的说明：
```
/**
 * Escalate internal APIs in {@code okhttp3} so they can be used from OkHttp's implementation
 * packages. The only implementation of this interface is in {@link OkHttpClient}.
 */
 // 升级 okhttp3 中的内部 API，以便它们可以从 OkHttp 的实现包中使用。这个接口的唯一实现是在 OkHttpClient 中。
```
也就是说，它是用来给okhttp3增加能力的工具，很有设计意思啊。里面还有一些方法，也是ConnectionPool里面的，后面讲到了再说。

再追踪RouteDatabase，就是在StreamAllocation中参与routeSelector的创建，和在findConnection中记录成功了，至于失败，会在routeSelector中会有连接失败。

### get方法
```
    @Nullable
    RealConnection get(Address address, StreamAllocation streamAllocation, Route route) {
        assert (Thread.holdsLock(this));
        for (RealConnection connection : connections) {
            // connection对address、route可用(Eligible)，connection能创建流、不在黑名单、特殊情况
            if (connection.isEligible(address, route)) {
                // 让当前的streamAllocation去持有这个connection，并且让connection中记录这个流的分配
                streamAllocation.acquire(connection, true);
                return connection;
            }
        }
        return null;
    }
```
这里get方法就是ConnectionPool的取了，就是遍历dequeue找出合适的connection，isEligible可以看下里面的内容，比较长，主要是判断是否合适。

如果connection合适的话，会调用streamAllocation去acquire，这里向connection的allocations里面加了一个StreamAllocation的弱引用，还记得HTTP2的内容不，HTTP可是没有多个StreamAllocation的。
```
  public void acquire(RealConnection connection, boolean reportedAcquired) {
    assert (Thread.holdsLock(connectionPool));
    if (this.connection != null) throw new IllegalStateException();

    this.connection = connection;
    this.reportedAcquired = reportedAcquired;
    connection.allocations.add(new StreamAllocationReference(this, callStackTrace));
  }
```
### deduplicate方法
```
    // 如果可能，将 streamAllocation 持有的连接替换为共享连接。当同时创建多个多路复用连接时，这会恢复
    // deduplicate在Internal.instance调用，最终用在StreamAllocation的findConnection中，防止异步创建两个相同地址的connection
    @Nullable
    Socket deduplicate(Address address, StreamAllocation streamAllocation) {
        assert (Thread.holdsLock(this));
        for (RealConnection connection : connections) {
            // isMultiplexed返回http2Connection!=null，只有HTTP2才复用连接
            if (connection.isEligible(address, null)
                    && connection.isMultiplexed()
                    && connection != streamAllocation.connection()) {
                return streamAllocation.releaseAndAcquire(connection);
            }
        }
        return null;
    }
```
这个方法是在streamAllocation中使用的，用来消除重复情况，可能会异步创建多个同地址的连接。这里也是通过Internal拐了个弯。
```
    Socket socket = null;
    synchronized (connectionPool) {
      reportedAcquired = true;

      // Pool the connection.
      Internal.instance.put(connectionPool, result);

      // If another multiplexed connection to the same address was created concurrently, then
      // release this connection and acquire that one.
      if (result.isMultiplexed()) {
        socket = Internal.instance.deduplicate(connectionPool, address, this);
        result = connection;
      }
    }
```
### connectionBecameIdle方法
```
    // 通过Internal在StreamAllocation的deallocate(解除分配)方法中调用
    // 通知此连接已变为空闲。如果连接已从池中删除并应关闭，则返回 true。
    boolean connectionBecameIdle(RealConnection connection) {
        assert (Thread.holdsLock(this));
        if (connection.noNewStreams || maxIdleConnections == 0) {
            connections.remove(connection);
            return true;
        } else {
            // 唤醒cleanup线程，我们可能已经超过空闲连接限制
            // 在cleanup方法中synchronized (this)了，其他地方对connection加了对象锁的话，会卡住cleanupRunnable的cleanup方法
            notifyAll(); // Awake the cleanup thread: we may have exceeded the idle connection limit.
            return false;
        }
    }
```
注释已经写了，这个也是通过Internal来给okhttpClient扩展的，在解除分配的时候用来清除无效connection，或者释放当前ConnectionPool的锁，其他代码地方大量对ConnectionPool进行了锁，释放对象锁，让清除线程能正常工作。

### evictAll方法
```
    // 向外提供的方法，通过client.connectionPool().evictAll()调用
    /** Close and remove all idle connections in the pool. */
    public void evictAll() {
        List<RealConnection> evictedConnections = new ArrayList<>();
        synchronized (this) {
            for (Iterator<RealConnection> i = connections.iterator(); i.hasNext(); ) {
                RealConnection connection = i.next();
                if (connection.allocations.isEmpty()) {
                    connection.noNewStreams = true;
                    evictedConnections.add(connection);
                    i.remove();
                }
            }
        }

        for (RealConnection connection : evictedConnections) {
            closeQuietly(connection.socket());
        }
    }
```
前面的方法都是在Internal中拐弯，向外提供，但是这个evictAll好像不一样，这个我没有找到调用的地方，不过看了OkHttpClient的说明，里面说到了用法：
```
Clear the connection pool with evictAll(). Note that the connection pool's daemon thread may not exit immediately.

     client.connectionPool().evictAll();
```
到这ConnectionPool就说完了，整个类加注释也就300多行，不多里面出现了一些前面没注意的东西，下面还得继续说说。

## Internal
前面已经说过了，Internal是在OkHttpClient里面的一个匿名类，并在static块中完成初始化，它是用来给okhttp3增加能力的工具，，说明如下：
```
/**
 * Escalate internal APIs in {@code okhttp3} so they can be used from OkHttp's implementation
 * packages. The only implementation of this interface is in {@link OkHttpClient}.
 */
 // 升级 okhttp3 中的内部 API，以便它们可以从 OkHttp 的实现包中使用。这个接口的唯一实现是在 OkHttpClient 中。
```
里面的方法下面整理了一下，就这么几类:
```
builder.addLenient(line);
builder.addLenient(name, value);
builder.setInternalCache(internalCache);

pool.connectionBecameIdle(connection);
pool.get(address, streamAllocation, route);
pool.deduplicate(address, streamAllocation);
pool.put(connection);
connectionPool.routeDatabase;

address_a.equalsNonHost(address_b);

responseBuilder.code;

tlsConfiguration.apply(sslSocket, isFallback);

e.getMessage().startsWith(HttpUrl.Builder.INVALID_HOST);

((RealCall) call).streamAllocation();
((RealCall) call).timeoutExit(e);
RealCall.newRealCall(client, originalRequest, true);
```

## StreamAllocation再补充
看完这节的ConnectionPool，我就发现前面StreamAllocation有些比较难理解的东西，真就拨云见日了，下面再对StreamAllocation做一些补充。

### reportedAcquired
reportedAcquired是个数据标志，前面我们跳过了，现在终于知道了，就是connection的allocations添加了StreamAllocation的弱引用嘛，上面有讲到。

### releaseIfNoNewStreams方法
```
  private Socket releaseIfNoNewStreams() {
    assert (Thread.holdsLock(connectionPool));
    RealConnection allocatedConnection = this.connection;
    if (allocatedConnection != null && allocatedConnection.noNewStreams) {
      return deallocate(false, false, true);
    }
    return null;
  }
```
这里看不出什么，再看下deallocate方法：
```
// 释放此分配持有的资源。如果分配了足够的资源，连接将被分离或关闭。调用者必须在连接池上同步。
private Socket deallocate(boolean noNewStreams, boolean released, boolean streamFinished) {
    assert (Thread.holdsLock(connectionPool));

    // 注意这里streamFinished将this.codec置空了，下面逻辑用到了
    if (streamFinished) {
      this.codec = null;
    }
    if (released) {
      this.released = true;
    }
    Socket socket = null;
    if (connection != null) {
      if (noNewStreams) {
        connection.noNewStreams = true;
      }
      // 不再使用或不能使用connection了
      if (this.codec == null && (this.released || connection.noNewStreams)) {
        // 释放连接
        release(connection);
        if (connection.allocations.isEmpty()) {
          connection.idleAtNanos = System.nanoTime();
          if (Internal.instance.connectionBecameIdle(connectionPool, connection)) {
            socket = connection.socket();
          }
        }
        // 注意这里对StreamAllocation持有的connection置空了
        connection = null;
      }
    }
    return socket;
  }
```
这里代码一大堆，实际就做了一件事，就是关闭流(分离流)，release关闭了当前connection的当前StreamAllocation。后面判断了下connection里面是否还有流，空了的话，设置连接的最新空闲的时刻，并调用connectionBecameIdle方法，在ConnectionPool中清除连接。

这里清除成功会返回true，所以这里得到的socket就是一个没用使用到的socket了，回到releaseIfNoNewStreams被调用的findConnection方法，这里会把它关闭了

结合releaseIfNoNewStreams这个名字，再来理解下这个过程，也就是说StreamAllocation里面持有的一个connection不能新建流了，且没有其他的StreamAllocation在使用这个connection了，那就会关闭这个socket，这个过程在StreamAllocation创建connection的时候，目的时用于清除旧的且没有用了的connection.

### streamFinished方法
```
public void streamFinished(boolean noNewStreams, HttpCodec codec, long bytesRead, IOException e) {
    eventListener.responseBodyEnd(call, bytesRead);

    Socket socket;
    Connection releasedConnection;
    boolean callEnd;
    synchronized (connectionPool) {
      // 这里保证了this.codec==codec!=null
      if (codec == null || codec != this.codec) {
        throw new IllegalStateException("expected " + this.codec + " but was " + codec);
      }
      
      // 要复用这个connection
      if (!noNewStreams) {
        connection.successCount++;
      }
      
      // 核心代码
      releasedConnection = connection;
      socket = deallocate(noNewStreams, false, true);
      
      // deallocate会将connection关闭并置空，但是条件如下：
      // if (this.codec == null && (this.released || connection.noNewStreams)) 
      if (connection != null) releasedConnection = null;
      callEnd = this.released;
    }
    // 这里socket只有在connection里面的流空了才会返回，否则是空的
    closeQuietly(socket);
    if (releasedConnection != null) {
      // releasedConnection就是为了做下记录？
      eventListener.connectionReleased(call, releasedConnection);
    }
    
    //eventListener代码 。。。
  }
```
这个方法主要是用在HttpCodec里面(endOfInput)，用来关闭流，下面是Http1Codec调用的地方：
```
    // 关闭缓存条目并使套接字可供重用。这应该在到达主体末尾时调用。
    protected final void endOfInput(boolean reuseConnection, IOException e) throws IOException {
      if (state == STATE_CLOSED) return;
      if (state != STATE_READING_RESPONSE_BODY) throw new IllegalStateException("state: " + state);

      detachTimeout(timeout);

      state = STATE_CLOSED;
      if (streamAllocation != null) {
        // noNewStreams=!reuseConnection，所以复用socket就不传noNewStreams
        streamAllocation.streamFinished(!reuseConnection, Http1Codec.this, bytesRead, e);
      }
    }
  }
```
在回到streamFinished里面，实际它就是调用了下上面的deallocate方法，这里有个releasedConnection让我很懵逼，一般来说deallocate方法会让connection置空：
```
      if (this.codec == null && (this.released || connection.noNewStreams)) {
        release(connection);
        if (connection.allocations.isEmpty()) {
          connection.idleAtNanos = System.nanoTime();
          if (Internal.instance.connectionBecameIdle(connectionPool, connection)) {
            socket = connection.socket();
          }
        }
        connection = null;
      }
```
如果connection!=null那就是这里条件没符合，但是最后releasedConnection只是用eventListener记录了下，也就是connection==null记录二零关闭的那个connection，connection!=null就不记录，所以就是指上面的条件没关闭connection吗？好吧，这里就是为了记录下吧。

### release和noNewStreams方法
讲完streamFinished方法，下面还有两个方法类似的，都是和deallocate有关系，一并讲了吧，就是传入deallocate的参数不一样，
```
  public void release() {
    Socket socket;
    Connection releasedConnection;
    synchronized (connectionPool) {
      releasedConnection = connection;
      // 这里会不会触发对connection的清理，取决于this.codec是不是null
      socket = deallocate(false, true, false);
      if (connection != null) releasedConnection = null;
    }
    // 这里socket只有在connection里面的流空了才会返回，否则是空的
    closeQuietly(socket);
    if (releasedConnection != null) {
      Internal.instance.timeoutExit(call, null);
      eventListener.connectionReleased(call, releasedConnection);
      eventListener.callEnd(call);
    }
  }
```
这里deallocate传进去的参数不会触发上一小节说的清理，还是要看release前面会不会对this.codec置空，看了下用的地方都是在RetryAndFollowUpInterceptor里面，那就只是关闭了当前streamAllocation，并没有关闭这个连接，还是可以复用的。
```
  /** Forbid new streams from being created on the connection that hosts this allocation. */
  public void noNewStreams() {
    Socket socket;
    Connection releasedConnection;
    synchronized (connectionPool) {
      releasedConnection = connection;
      socket = deallocate(true, false, false);
      if (connection != null) releasedConnection = null;
    }
    // 这里socket只有在connection里面的流空了才会返回，否则是空的
    closeQuietly(socket);
    if (releasedConnection != null) {
      eventListener.connectionReleased(call, releasedConnection);
    }
  }
```
而noNewStreams传进去的参数，也是不能保证connection被置空，它会让connection变为noNewStreams，估计是让线程池的cleanup线程自行处理吧。

这里还要注意下这个socket的关闭，在deallocate里面清理了connection的流，还需要connection的流空了才会返回这个socket，还要复用呢，哪能随便关掉！

### streamFailed方法
下面又是一个特别长的方法，streamFailed一听名字应该就是知道是对创建流失败后的操作，用在RetryAndFollowUpInterceptor里面，对releaseConnection的情况进行处理：
```
public void streamFailed(IOException e) {
    Socket socket;
    Connection releasedConnection;
    boolean noNewStreams = false;

    // 锁住连接池？为什么不锁住connection呢？不让其他地方get、put这个connection？
    synchronized (connectionPool) {
      if (e instanceof StreamResetException) {
        ErrorCode errorCode = ((StreamResetException) e).errorCode;
        // 重试一次
        if (errorCode == ErrorCode.REFUSED_STREAM) {
          // Retry REFUSED_STREAM errors once on the same connection.
          refusedStreamCount++;
          if (refusedStreamCount > 1) {
            noNewStreams = true;
            route = null;
          }
          
          // 不让再创建流
        } else if (errorCode != ErrorCode.CANCEL) {
          // Keep the connection for CANCEL errors. Everything else wants a fresh connection.
          noNewStreams = true;
          route = null;
        }
        
        // connection可以了，但是HTTP或者连接关闭异常？
      } else if (connection != null
          && (!connection.isMultiplexed() || e instanceof ConnectionShutdownException)) {
        // 不要忽略了这句，不再创建流  
        noNewStreams = true;

        // 是route就连接失败了吗？connection.successCount在上面的streamFinished增加的，就是没成功过吧
        // If this route hasn't completed a call, avoid it for new connections.
        if (connection.successCount == 0) {
          if (route != null && e != null) {
            routeSelector.connectFailed(route, e);
          }
          route = null;
        }
      }
      
      // 又来一遍deallocate，注意传入了streamFinished，会置空codec，可能触发对connection里面流的关闭
      releasedConnection = connection;
      socket = deallocate(noNewStreams, false, true);
      if (connection != null || !reportedAcquired) releasedConnection = null;
    }

    // 这里socket只有在connection里面的流空了才会返回，否则是空的
    closeQuietly(socket);
    if (releasedConnection != null) {
      eventListener.connectionReleased(call, releasedConnection);
    }
  }
```
大致整理了下，就是操作了下noNewStreams和route，调用了一下deallocate，传入了streamFinished，会置空codec，可能触发对connection里面流的关闭。

为什么是可能，因为还要看noNewStreams啊，noNewStreams和released都传false就不会触发，第一个ErrorCode.REFUSED_STREAM重试的时候就不会触发。

### releaseAndAcquire方法
看到这我也头疼了，还好这里是StreamAllocation最后一个重要方法了，前面已经讲到release方法了，是对当前StreamAllocation从对应connection中移除的功能，而下面这个这方法则是让StreamAllocation释放旧connection，更换新的connection：
```
  // 释放此连接持有的连接并改为获取 newConnection。只有在保持的连接是新连接但被 newConnection 复制的情况下调用它才是安全的。这通常发生在同时连接到 HTTP/2 网络服务器时。
  public Socket releaseAndAcquire(RealConnection newConnection) {
    assert (Thread.holdsLock(connectionPool));
    // 旧的connection里面只允许有一个allocation元素，且没有在使用流了，多个allocation元素也没法这样换啊！这里会release的。
    if (codec != null || connection.allocations.size() != 1) throw new IllegalStateException();

    // 直接把旧连接里面的StreamAllocation弱引用放到新的connection里面就行了
    // Release the old connection.
    Reference<StreamAllocation> onlyAllocation = connection.allocations.get(0);
    
    // 这里就传了noNewStreams=true，需要注意这里codec==null了，里面会触发对旧connection的清理，清理了就会得到socket
    Socket socket = deallocate(true, false, false);

    // Acquire the new connection.
    this.connection = newConnection;
    newConnection.allocations.add(onlyAllocation);

    return socket;
  }
```
这里就是deallocate最后一个调用地方了，和上面五个类似，上面注释写的很清楚了，deallocate应该能理解了吧。至于release和互换的逻辑比较简单。

这个方法唯一用的地方是在ConnectionPool的deduplicate方法，上面也有讲，再提一下，估计就好理解了，就是异步可能创建了相同的connection，调整一下。
```
    // 如果可能，将 streamAllocation 持有的连接替换为共享连接。当同时创建多个多路复用连接时，这会恢复
    // deduplicate在Internal.instance调用，最终用在StreamAllocation的findConnection中，防止异步创建两个相同地址的connection
    @Nullable
    Socket deduplicate(Address address, StreamAllocation streamAllocation) {
        assert (Thread.holdsLock(this));
        for (RealConnection connection : connections) {
            // isMultiplexed返回http2Connection!=null，只有HTTP2才复用连接
            if (connection.isEligible(address, null)
                    && connection.isMultiplexed()
                    && connection != streamAllocation.connection()) {
                return streamAllocation.releaseAndAcquire(connection);
            }
        }
        return null;
    }
```

## 小结
这篇文章写的又有点长了，写了ConnectionPool和StreamAllocation比较多的内容，也附带讲了下Internal，看完ConnectionPool回过头来再看StreamAllocation真就理解通畅了好多。