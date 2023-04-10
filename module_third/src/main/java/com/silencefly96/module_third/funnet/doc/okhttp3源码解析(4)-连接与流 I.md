# okhttp3源码解析(4)-连接与流 I
## 前言
上两篇文章我们讲解了okhttp的拦截器，虽然不是很深入，但是主要流程与内容大致时有个了解了，这篇文章来看下okhttp连接与流相关的内容。

[okhttp3源码解析(3)-拦截器 II](https://blog.csdn.net/lfq88/article/details/129958544)

## 概述
经过前面三篇文章，我认为啊，大概就剩下三个功能类了：StreamAllocation、HttpCodec、RealConnection。

StreamAllocation是连接、流、请求三者关联的工具，HttpCodec是对socket流的一个封装，RealConnection是连接的管理工具。下面我们来详细学习下！

## StreamAllocation
根据前面对拦截器的分析，我们知道StreamAllocation是这三个类中第一个创建的，它创建在RetryAndFollowUpInterceptor中，但是在ConnectInterceptor中才用到，创建了httpCodec和connection两个对象。下面着重讲下它的核心方法。
 
### newStream
newStream是用来创建HttpCodec的，它会获得Connection，路由选择，发起socket连接，生成httpCodec(流对象：提供的I/O操作完成网络通信)。

#### 第一步，通过 findHealthyConnection() 获取可用的Connection
```
    int connectTimeout = chain.connectTimeoutMillis();
    int readTimeout = chain.readTimeoutMillis();
    int writeTimeout = chain.writeTimeoutMillis();
    int pingIntervalMillis = client.pingIntervalMillis();
    boolean connectionRetryEnabled = client.retryOnConnectionFailure();

    try {
      RealConnection resultConnection = findHealthyConnection(connectTimeout, readTimeout,
          writeTimeout, pingIntervalMillis, connectionRetryEnabled, doExtensiveHealthChecks);
```

#### 第二步，不停通过findConnection拿到RealConnection，并进行检查，直到可用
```
  /**
   * Finds a connection and returns it if it is healthy. If it is unhealthy the process is repeated
   * until a healthy connection is found.
   */
  private RealConnection findHealthyConnection(int connectTimeout, int readTimeout,
      int writeTimeout, int pingIntervalMillis, boolean connectionRetryEnabled,
      boolean doExtensiveHealthChecks) throws IOException {
    while (true) {
      RealConnection candidate = findConnection(connectTimeout, readTimeout, writeTimeout,
          pingIntervalMillis, connectionRetryEnabled);

      // If this is a brand new connection, we can skip the extensive health checks.
      synchronized (connectionPool) {
        if (candidate.successCount == 0) {
          return candidate;
        }
      }

      // Do a (potentially slow) check to confirm that the pooled connection is still good. If it
      // isn't, take it out of the pool and start again.
      if (!candidate.isHealthy(doExtensiveHealthChecks)) {
        noNewStreams();
        continue;
      }

      return candidate;
    }
  }
```
#### 第三步，获取Connection，对旧的Connection验证，或从connectionPool里面获取新的
```
  /**
   * Returns a connection to host a new stream. This prefers the existing connection if it exists,
   * then the pool, finally building a new connection.
   */
  private RealConnection findConnection(int connectTimeout, int readTimeout, int writeTimeout,
      int pingIntervalMillis, boolean connectionRetryEnabled) throws IOException {
    boolean foundPooledConnection = false;
    RealConnection result = null;
    Route selectedRoute = null;
    Connection releasedConnection;
    Socket toClose;
    synchronized (connectionPool) {
      if (released) throw new IllegalStateException("released");
      if (codec != null) throw new IllegalStateException("codec != null");
      if (canceled) throw new IOException("Canceled");

      // Attempt to use an already-allocated connection. We need to be careful here because our
      // already-allocated connection may have been restricted from creating new streams.
      releasedConnection = this.connection;
      toClose = releaseIfNoNewStreams();
      if (this.connection != null) {
        // We had an already-allocated connection and it's good.
        // (1)已有旧的connection
        result = this.connection;
        releasedConnection = null;
      }
      if (!reportedAcquired) {
        // If the connection was never reported acquired, don't report it as released!
        releasedConnection = null;
      }

      if (result == null) {
        // Attempt to get a connection from the pool.
        Internal.instance.get(connectionPool, address, this, null);
        if (connection != null) {
          foundPooledConnection = true;
          // (2)当前连接池里有connection
          result = connection;
        } else {
          selectedRoute = route;
        }
      }
    }
    closeQuietly(toClose);

    if (releasedConnection != null) {
      eventListener.connectionReleased(call, releasedConnection);
    }
    if (foundPooledConnection) {
      eventListener.connectionAcquired(call, result);
    }
    if (result != null) {
      // If we found an already-allocated or pooled connection, we're done.
      return result;
    }
```
英文注释写得非常清楚，不多解释了。这里注意Internal.instance.get方法传入了connectionPool、address以及当前StreamAllocation(this)。

Internal的实例是OkHttpClient，里面会从connectionPool取出connection，最终调用this的acquire方法修改this的connection和route，也就是说这里并不是通过返回的形式拿到返回值，而是传入了this，在里面修改this的成员属性，有利有弊吧。

#### 第四步，根据路由选择(指代理发送数据路径的选择: DNS返回多个IP)情况复用或者创建connection
```
    // If we need a route selection, make one. This is a blocking operation.
    boolean newRouteSelection = false;
    // 注意这个条件，没有selectedRoute且没有routeSelection(未初始化)、没有selectedRoute且当前routeSelection没有Route了
    if (selectedRoute == null && (routeSelection == null || !routeSelection.hasNext())) {
      newRouteSelection = true;
      // 切换routeSelection，routeSelector.next()会获得合适的路由集合(Selection)用于连接目标服务器
      routeSelection = routeSelector.next();
    }

    synchronized (connectionPool) {
      if (canceled) throw new IOException("Canceled");

      // routeSelection换了，在其所有路线(Route)的connectionPool里找connection
      if (newRouteSelection) {
        // Now that we have a set of IP addresses, make another attempt at getting a connection from
        // the pool. This could match due to connection coalescing.
        List<Route> routes = routeSelection.getAll();
        for (int i = 0, size = routes.size(); i < size; i++) {
          Route route = routes.get(i);
          
          // Internal的实例是OkHttpClient，里面会从connectionPool取出connection
          // 最终调用this的acquire方法修改this的connection和route
          Internal.instance.get(connectionPool, address, this, route);
          if (connection != null) {
            foundPooledConnection = true;
            // (3)不同路由选择路线里面的connection
            result = connection;
            this.route = route;
            break;
          }
        }
      }// 结束newRouteSelection块

      // 旧的routeSelection没拿到、新的routeSelection(新建了的话)也没有拿到可复用的connection，就需要新创建了
      if (!foundPooledConnection) {
      
        // 没有选中Route，在当前routeSelection里面取一个，注意这个next和上面routeSelector的不一样
        if (selectedRoute == null) {
          selectedRoute = routeSelection.next();
        }

        // Create a connection and assign it to this allocation immediately. This makes it possible
        // for an asynchronous cancel() to interrupt the handshake we're about to do.
        route = selectedRoute;
        refusedStreamCount = 0;
        
        // (4)没找到可以复用的connection，新创建RealConnection并保存到connectionPool去
        result = new RealConnection(connectionPool, selectedRoute);
        // 让当前实例持有connection
        acquire(result, false);
      }
    }

    // If we found a pooled connection on the 2nd time around, we're done.
    if (foundPooledConnection) {
      eventListener.connectionAcquired(call, result);
      return result;
    }
```
这里又看得懵逼了，得先理解下RouteSelector、RouteSelector.Selection、Route几者的情况，这里我也说不太清楚，可以看下下面文章:
[Okhttp之RouteSelector简单解析](https://blog.csdn.net/chunqiuwei/article/details/74079916)
[Android | 彻底理解 OkHttp 代理与路由](https://juejin.cn/post/7208239217943101495)

我的理解就是访问一个url，其域名经过DNS解析后会有多个路由集合(Selection),使用RouteSelector的next方法获取。每个Selection内部有一个路由(Route)列表和下一个路由的索引，调用next会获得这个索引的路由。

在上一步result没有从上面的连接池拿到，仅仅说明当前Selection的当前Route没有该url的connection，可能其他Selection中还有connection缓存，实在不行再创建。

#### 第五步，使用connection发起socket连接，保存到connectionPool，如果连接地址相同进行复用
```
    // Do TCP + TLS handshakes. This is a blocking operation.
    // 调用RealConnection的connect发起连接
    result.connect(connectTimeout, readTimeout, writeTimeout, pingIntervalMillis,
        connectionRetryEnabled, call, eventListener);
    routeDatabase().connected(result.route());

    Socket socket = null;
    synchronized (connectionPool) {
      reportedAcquired = true;

      // Pool the connection.
      Internal.instance.put(connectionPool, result);

      // If another multiplexed connection to the same address was created concurrently, then
      // release this connection and acquire that one.
      // 防止异步创建了两个同地址的connection，关闭当前connection，取出socket并关闭其流
      if (result.isMultiplexed()) {
        socket = Internal.instance.deduplicate(connectionPool, address, this);
        result = connection;
      }
    }
    closeQuietly(socket);

    eventListener.connectionAcquired(call, result);
    return result;
  }
```
上一步经过四个地方终于获取到了result(即connection)，这里就是发起了下连接，并将connection存到connectionPool，前面只是用acquire让当前实例持有，最后还验证了下异步是否创建了两个connection。

#### 第六步，根据获得的Connection(已连接server)创建Codec
```
      HttpCodec resultCodec = resultConnection.newCodec(client, chain, this);

      synchronized (connectionPool) {
        codec = resultCodec;
        return resultCodec;
      }
    } catch (IOException e) {
      throw new RouteException(e);
    }
  }
```

到这里newStream就讲解完了，这也是StreamAllocation最重要的方法，其他方法就没这么复杂了。

### codec方法
```
  public HttpCodec codec() {
    synchronized (connectionPool) {
      return codec;
    }
  }
```
前面讲到了newStream会返回RealConnection对象，这里codec方法也会返回HttpCodec对象。这里的方法只是返回了当前对象的codec属性，这个值就是上面第六步的resultCodec。

### noNewStreams方法
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
    closeQuietly(socket);
    if (releasedConnection != null) {
      eventListener.connectionReleased(call, releasedConnection);
    }
  }
```
noNewStreams方法在前面分析中好像出现了很多次，不过没怎么讲，现在来看下。看下方法注释，禁止在此connection创建streams，再看下此类开头相关描述：
> prevents the connection from being used for new streams in the future. 
> Use this after a Connection: close header, or when the connection may be inconsistent.

大致意思就是防止创建新的streams，一是可能连接不一致了，二是已经读取完header了，header都读没人创建新的streams(HttpCodec)并没什么用了吧。

### release方法
```
  public void release() {
    Socket socket;
    Connection releasedConnection;
    synchronized (connectionPool) {
      releasedConnection = connection;
      socket = deallocate(false, true, false);
      if (connection != null) releasedConnection = null;
    }
    closeQuietly(socket);
    if (releasedConnection != null) {
      Internal.instance.timeoutExit(call, null);
      eventListener.connectionReleased(call, releasedConnection);
      eventListener.callEnd(call);
    }
  }
```
这里和上面差不多，大致看下该类相关描述：
> removes the call's hold on the connection. Note that this won't immediately free the connection if there is a stream still lingering. 
> That happens when a call is complete but its response body has yet to be fully consumed.

## HttpCodec
上面讲完了StreamAllocation的相关内容，这里我们先讲下HttpCodec，估计后面得再开一篇文章来讲RealConnection与ConnectionPool。

HttpCodec是一个接口，分Http1Codec和Http2Codec，还有点不一样，下面先来看Http1Codec。HttpCodec是在CallServerInterceptor使用到的，主要有四个地方，对请求、回复的header及body进行流的读写。

### writeRequestHeaders
```
    @Override 
    public void writeRequestHeaders(Request request) throws IOException {
        // write第一步，读取请求头
        String requestLine = RequestLine.get(
                request, streamAllocation.connection().route().proxy().type());

        // write第二步，写入header到sink(输入流，在RealConnection中创建并传到HttpCodec中)
        writeRequest(request.headers(), requestLine);
    }
```
这里很简单，就是用RequestLine获取了第一行的请求信息(GET xxx.com HTTP/1.1)，第二步将请求行和header全部写入到sink(socket输出流)里面，使用Utf8格式。
```
    /** Returns bytes of a request header for sending on an HTTP transport. */
    public void writeRequest(Headers headers, String requestLine) throws IOException {
        if (state != STATE_IDLE) throw new IllegalStateException("state: " + state);
        sink.writeUtf8(requestLine).writeUtf8("\r\n");
        for (int i = 0, size = headers.size(); i < size; i++) {
            sink.writeUtf8(headers.name(i))
                    .writeUtf8(": ")
                    .writeUtf8(headers.value(i))
                    .writeUtf8("\r\n");
        }
        sink.writeUtf8("\r\n");
        state = STATE_OPEN_REQUEST_BODY;
    }
```
这里还维护了一个state，用来控制流程的准确性。

### createRequestBody
上面写了request的header部分，这里就是request的body部分了。
```
    @Override public Sink createRequestBody(Request request, long contentLength) {
        // 根据request创建一个输出流
        if ("chunked".equalsIgnoreCase(request.header("Transfer-Encoding"))) {
            // Stream a request body of unknown length.
            return newChunkedSink();
        }

        if (contentLength != -1) {
            // Stream a request body of a known length.
            return newFixedLengthSink(contentLength);
        }

        throw new IllegalStateException(
                "Cannot stream a request body without chunked encoding or a known content length!");
    }
```
该方法就是创建了一个Sink对象，根据分段标准创建了不同的对象，newFixedLengthSink多了一个bytesRemaining来控制分段。其实这里的Sink的对象，就是对流的一个封装，更好控制罢了，唯一目的就是向流写入数据。
```
        @Override public void write(Buffer source, long byteCount) throws IOException {
            if (closed) throw new IllegalStateException("closed");
            if (byteCount == 0) return;

            sink.writeHexadecimalUnsignedLong(byteCount);
            sink.writeUtf8("\r\n");
            sink.write(source, byteCount);
            sink.writeUtf8("\r\n");
        }

        @Override public synchronized void flush() throws IOException {
            if (closed) return; // Don't throw; this stream might have been closed on the caller's behalf.
            sink.flush();
        }

        @Override public synchronized void close() throws IOException {
            if (closed) return;
            closed = true;
            sink.writeUtf8("0\r\n\r\n");
            detachTimeout(timeout);
            state = STATE_READ_RESPONSE_HEADERS;
        }
```

### flushRequest
```
    @Override public void flushRequest() throws IOException {
        sink.flush();
    }
```
还记得前面说发送request吗？就是这里了，对sink(socket输出流)刷新就把数据发送出去了，接下来只要等着读取socket的返回流就行了。

### readResponseHeaders
```
@Override public Response.Builder readResponseHeaders(boolean expectContinue) throws IOException {
        if (state != STATE_OPEN_REQUEST_BODY && state != STATE_READ_RESPONSE_HEADERS) {
            throw new IllegalStateException("state: " + state);
        }

        try {
            // read第一步，从source流中读取header的字符串，并处理成statusLine
            StatusLine statusLine = StatusLine.parse(readHeaderLine());

            // read第二步，根据statusLine创建responseBuilder
            Response.Builder responseBuilder = new Response.Builder()
                    .protocol(statusLine.protocol)
                    .code(statusLine.code)
                    .message(statusLine.message)
                    .headers(readHeaders());

            // read第三步，根据CallServerInterceptor里面三个调用，得到不同结果
            if (expectContinue && statusLine.code == HTTP_CONTINUE) {
                return null;
            } else if (statusLine.code == HTTP_CONTINUE) {
                state = STATE_READ_RESPONSE_HEADERS;
                return responseBuilder;
            }

            state = STATE_OPEN_RESPONSE_BODY;
            return responseBuilder;
        } catch (EOFException e) {
            // Provide more context if the server ends the stream before sending a response.
            IOException exception = new IOException("unexpected end of stream on " + streamAllocation);
            exception.initCause(e);
            throw exception;
        }
    }
```
这里其实和request类似，不过麻烦了一点，看下注释，我这里分了三点。需要注意下第三点，我们在CallServerInterceptor中传入了一个expectContinue，并且有三次对readResponseHeaders的调用。

第一个是client发送HTTP_CONTINUE的情况，传入expectContinue为true，得到一个null的Response.Builder，这样就等于读了一次只有header没有body的response，且把Response.Builder置空，就好像没发生这次读取一样，下面和正常逻辑一样执行。

第二个是正常读取response，第三个是client收到HTTP_CONTINUE的情况，这时候要多读取一次回复，以第二条为准，也就是说服务器再HTTP_CONTINUE情况下会连发两条回复。

### openResponseBody
最后我们来看response的body是如何封装的:
```
@Override public ResponseBody openResponseBody(Response response) throws IOException {
        streamAllocation.eventListener.responseBodyStart(streamAllocation.call);
        String contentType = response.header("Content-Type");

        // 这里只是根据response的header创建不同的source(输入流)，并创建不同的ResponseBody，
        // response包含响应头和响应体，这里的响应体是对原始httpCodec的source封装
        if (!HttpHeaders.hasBody(response)) {

            // !!注意AbstractSource是内部类，持有httpCodec的source，并从中读取内容到ResponseBody
            Source source = newFixedLengthSource(0);
            return new RealResponseBody(contentType, 0, Okio.buffer(source));
        }

        if ("chunked".equalsIgnoreCase(response.header("Transfer-Encoding"))) {
            Source source = newChunkedSource(response.request().url());
            return new RealResponseBody(contentType, -1L, Okio.buffer(source));
        }

        long contentLength = HttpHeaders.contentLength(response);
        if (contentLength != -1) {
            Source source = newFixedLengthSource(contentLength);
            return new RealResponseBody(contentType, contentLength, Okio.buffer(source));
        }

        return new RealResponseBody(contentType, -1L, Okio.buffer(newUnknownLengthSource()));
    }
```
这里先根据情况创建不同AbstractSource实现类，实际就是对长度的一个控制，FixedLengthSource是固定长度或无长度，ChunkedSource是对分段的处理，内部有一个bytesRemainingInChunk来控制剩余读取数据长度。

AbstractSource实现类中最重要的就是read了，最终会调用super.read方法，里面对对source(socket输入流)读取，进入到传入的sink，这里涉及到Okio，后面看看吧。
```
        @Override public long read(Buffer sink, long byteCount) throws IOException {
            try {
                long read = source.read(sink, byteCount);
                if (read > 0) {
                    bytesRead += read;
                }
                return read;
            } catch (IOException e) {
                endOfInput(false, e);
                throw e;
            }
        }
```
经过RealResponseBody的封装后就成了RealResponseBody的BufferedSource，这里如果我很要对ResponseBody读取还是要按流的形式读取的，并且得关闭流。
```
        long contentLength = HttpHeaders.contentLength(response);
        if (contentLength != -1) {
            Source source = newFixedLengthSource(contentLength);
            return new RealResponseBody(contentType, contentLength, Okio.buffer(source));
        }
```
```
public final class RealResponseBody extends ResponseBody {
  private final @Nullable String contentTypeString;
  private final long contentLength;
  private final BufferedSource source;

  public RealResponseBody(
      @Nullable String contentTypeString, long contentLength, BufferedSource source) {
    this.contentTypeString = contentTypeString;
    this.contentLength = contentLength;
    this.source = source;
  }
```
这里小结下，大致意思就是根据header信息(长度、分段)去读取socket输入流中得body，并封装到ResponseBody中得过程。

### Http2Codec
Http2Codec和Http1Codec类似，我们注意上面五个方法就行，看了下好像还更简单些，不过多了个Http2Stream，有兴趣得读者可以看下。

## 结语
这里就写了下StreamAllocation和HttpCodec，没有特别深入但是也算大致功能都有了解了，下面一篇再来研究RealConnection和ConnectionPool。
