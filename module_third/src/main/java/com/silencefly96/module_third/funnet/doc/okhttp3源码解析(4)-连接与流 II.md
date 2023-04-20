# okhttp3源码解析(5)-RealConnection、Http2Connection
## 前言
上一篇文章我们讲了StreamAllocation和HttpCodec的内容，本来想一篇文章讲完连接与流的，但是篇幅有点长了，多分几篇吧。我这看了下RealConnection，感觉没看得多懂，内容也有点多，HTTPS以及HTTP2相关的东西我不是很明白，学得也迷迷糊糊，下面只算是我的理解了，如有问题可以评论区指出，大家一起进步！

这篇文章还是主要讲RealConnection，然后由RealConnection到Http2Connection，再讲部分Http2Stream，不算深入，也就是有个大致了解吧。

[okhttp3源码解析(4)-StreamAllocation、HttpCodec](https://blog.csdn.net/lfq88/article/details/130056328)

## RealConnection
RealConnection在ConnectInterceptor中由StreamAllocation创建，在上一篇文章中StreamAllocation的newStream部分，我们讲到了RealConnection的实际创建地方：
```
        // Create a connection and assign it to this allocation immediately. This makes it possible
        // for an asynchronous cancel() to interrupt the handshake we're about to do.
        route = selectedRoute;
        refusedStreamCount = 0;
        
        // (4)没找到可以复用的connection，新创建RealConnection并保存到connectionPool去
        result = new RealConnection(connectionPool, selectedRoute);
        // 让当前实例持有connection
        acquire(result, false);
```
创建的时候传入了两个参数：connectionPool以及selectedRoute，newStream部分的逻辑比较复杂，这里不在详细说，这里知道它的创建地方就可以了。

RealConnection使用的地方也在StreamAllocation中，这里调用了它的connect方法，其他地方就没用到了。
```
    // Do TCP + TLS handshakes. This is a blocking operation.
    // 调用RealConnection的connect发起连接
    result.connect(connectTimeout, readTimeout, writeTimeout, pingIntervalMillis,
        connectionRetryEnabled, call, eventListener);
```

### 第一步，验证route参数
```
        RouteException routeException = null;
        List<ConnectionSpec> connectionSpecs = route.address().connectionSpecs();
        ConnectionSpecSelector connectionSpecSelector = new ConnectionSpecSelector(connectionSpecs);

        // sslSocketFactory: null 的时候不是 HTTPS address
        if (route.address().sslSocketFactory() == null) {
            // CLEARTEXT: 不加密、不认证的http url
            if (!connectionSpecs.contains(ConnectionSpec.CLEARTEXT)) {
                throw new RouteException(new UnknownServiceException(
                        "CLEARTEXT communication not enabled for client"));
            }
            String host = route.address().url().host();
            // 是否允许CLEARTEXT
            if (!Platform.get().isCleartextTrafficPermitted(host)) {
                throw new RouteException(new UnknownServiceException(
                        "CLEARTEXT communication to " + host + " not permitted by network security policy"));
            }
        } else {
            // HTTPS链接：CLEARTEXT需要H2_PRIOR_KNOWLEDGE，但HTTPS不需要这个
            if (route.address().protocols().contains(Protocol.H2_PRIOR_KNOWLEDGE)) {
                throw new RouteException(new UnknownServiceException(
                        "H2_PRIOR_KNOWLEDGE cannot be used with HTTPS"));
            }
        }
```
### 第二步，在循环中进行连接
```
        while (true) {
            try {
                // 是否需要隧道: Returns true if this route tunnels HTTPS through an HTTP proxy
                // 如果此路由通过 HTTP 代理隧道传输 HTTPS，则返回 true
                if (route.requiresTunnel()) {
                    // 第三步，对于HTTPS连接创建隧道
                    connectTunnel(connectTimeout, readTimeout, writeTimeout, call, eventListener);
                    if (rawSocket == null) {
                        // We were unable to connect the tunnel but properly closed down our resources.
                        break;
                    }
                } else {
                    // 第四步，根据route信息创建socket连接，获得输入输出流
                    connectSocket(connectTimeout, readTimeout, call, eventListener);
                }
                // 第五步，根据协议进行连接，HTTP直接返回，HTTPS使用http2Connection去处理，完全HTTPS还有Tls
                establishProtocol(connectionSpecSelector, pingIntervalMillis, call, eventListener);
                eventListener.connectEnd(call, route.socketAddress(), route.proxy(), protocol);
                break;
            } catch (IOException e) {
                // 其他代码...
            }
            
            // 其他代码...
```
### 第三步，对于HTTPS连接创建隧道
```
private void connectTunnel(int connectTimeout, int readTimeout, int writeTimeout, Call call,
                               EventListener eventListener) throws IOException {
        // 创建一个request
        Request tunnelRequest = createTunnelRequest();
        HttpUrl url = tunnelRequest.url();
        // 是做MAX_TUNNEL_ATTEMPTS次尝试创建Tunnel吗？
        for (int i = 0; i < MAX_TUNNEL_ATTEMPTS; i++) {
            // 根据route信息创建socket连接，获得输入输出流
            connectSocket(connectTimeout, readTimeout, call, eventListener);
            // 通过HTTP代理创建HTTPS连接，发送一次请求，正常情况返回null
            tunnelRequest = createTunnel(readTimeout, writeTimeout, tunnelRequest, url);

            // 正常情况(HTTP_OK)返回null
            if (tunnelRequest == null) break; // Tunnel successfully created.

            // The proxy decided to close the connection after an auth challenge. We need to create a new
            // connection, but this time with the auth credentials.
            closeQuietly(rawSocket);
            rawSocket = null;
            sink = null;
            source = null;
            eventListener.connectEnd(call, route.socketAddress(), route.proxy(), null);
        }
    }
```
下面是创建tunnelRequest，会为了为了支持先发的认证(preemptive authentication)，对proxyConnectRequest进行一层包装。
```
private Request createTunnelRequest() throws IOException {
        // 对代理服务器的request(TLS tunnel)，request是未加密的，不要发敏感数据(cookies)
        Request proxyConnectRequest = new Request.Builder()
                .url(route.address().url())
                .method("CONNECT", null)
                .header("Host", Util.hostHeader(route.address().url(), true))
                .header("Proxy-Connection", "Keep-Alive") // For HTTP/1.0 proxies like Squid.
                .header("User-Agent", Version.userAgent())
                .build();

        // 为了支持先发的认证(preemptive authentication)，创建一个假的“Auth Failed” response给authenticator
        Response fakeAuthChallengeResponse = new Response.Builder()
                .request(proxyConnectRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(HttpURLConnection.HTTP_PROXY_AUTH)
                .message("Preemptive Authenticate")
                .body(Util.EMPTY_RESPONSE)
                .sentRequestAtMillis(-1L)
                .receivedResponseAtMillis(-1L)
                .header("Proxy-Authenticate", "OkHttp-Preemptive")
                .build();

        // authenticatedRequest=null 则不需要先发的认证
        Request authenticatedRequest = route.address().proxyAuthenticator()
                .authenticate(route, fakeAuthChallengeResponse);

        return authenticatedRequest != null
                ? authenticatedRequest
                : proxyConnectRequest;
    }
```
connectSocket部分见下一步，下面看createTunnel做了什么，这里就是对TLS做验证吧，会发送header之类的，正常的话返回位null。
```
private Request createTunnel(int readTimeout, int writeTimeout, Request tunnelRequest,
                                 HttpUrl url) throws IOException {
        // Make an SSL Tunnel on the first message pair of each SSL + proxy connection.
        String requestLine = "CONNECT " + Util.hostHeader(url, true) + " HTTP/1.1";
        while (true) {
            // Http1Codec用来操作socket输入输出流，createTunnel前面已经connectSocket了
            // 关键是Http1Codec吗？所以用的是Http，SSL放在Header内吗？
            Http1Codec tunnelConnection = new Http1Codec(null, null, source, sink);
            source.timeout().timeout(readTimeout, MILLISECONDS);
            sink.timeout().timeout(writeTimeout, MILLISECONDS);

            // 向输出流写入header，finishRequest发送
            tunnelConnection.writeRequest(tunnelRequest.headers(), requestLine);
            tunnelConnection.finishRequest();

            // 读取返回流的header
            Response response = tunnelConnection.readResponseHeaders(false)
                    .request(tunnelRequest)
                    .build();

            // The response body from a CONNECT should be empty, but if it is not then we should consume
            // it before proceeding.
            long contentLength = HttpHeaders.contentLength(response);
            if (contentLength == -1L) {
                contentLength = 0L;
            }
            // 这里要求response body为empty，如果不是就跳过内容(到达exhausted)，或者deadline到达
            Source body = tunnelConnection.newFixedLengthSource(contentLength);
            Util.skipAll(body, Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
            body.close();

            switch (response.code()) {
                case HTTP_OK:
                    // Assume the server won't send a TLS ServerHello until we send a TLS ClientHello. If
                    // that happens, then we will have buffered bytes that are needed by the SSLSocket!
                    // This check is imperfect: it doesn't tell us whether a handshake will succeed, just
                    // that it will almost certainly fail because the proxy has sent unexpected data.
                    if (!source.buffer().exhausted() || !sink.buffer().exhausted()) {
                        throw new IOException("TLS tunnel buffered too many bytes!");
                    }
                    return null;

                case HTTP_PROXY_AUTH:
                    // 代理需要认证认证，修改tunnelRequest进入下一个循环
                    tunnelRequest = route.address().proxyAuthenticator().authenticate(route, response);
                    if (tunnelRequest == null) throw new IOException("Failed to authenticate with proxy");

                    // response告知连接结束，直接返回tunnelRequest,上一步中会做关闭流等操作
                    if ("close".equalsIgnoreCase(response.header("Connection"))) {
                        return tunnelRequest;
                    }
                    break;

                default:
                    throw new IOException(
                            "Unexpected response code for CONNECT: " + response.code());
            }
        }
    }
```
到这里，就结束了connectTunnel方法，这里会先创建socket连接，然后做了一些数据传输(createTunnel)。

### 第四步，根据route信息创建socket连接，获得输入输出流
```
private void connectSocket(int connectTimeout, int readTimeout, Call call,
                               EventListener eventListener) throws IOException {
        Proxy proxy = route.proxy();
        Address address = route.address();

        rawSocket = proxy.type() == Proxy.Type.DIRECT || proxy.type() == Proxy.Type.HTTP
                ? address.socketFactory().createSocket()
                : new Socket(proxy);

        eventListener.connectStart(call, route.socketAddress(), proxy);
        rawSocket.setSoTimeout(readTimeout);
        try {
            // 调用socket的connect
            Platform.get().connectSocket(rawSocket, route.socketAddress(), connectTimeout);
        } catch (ConnectException e) {
            ConnectException ce = new ConnectException("Failed to connect to " + route.socketAddress());
            ce.initCause(e);
            throw ce;
        }

        // The following try/catch block is a pseudo hacky way to get around a crash on Android 7.0
        // More details:
        // https://github.com/square/okhttp/issues/3245
        // https://android-review.googlesource.com/#/c/271775/
        try {
            // 获得原始socket的输入输出流
            source = Okio.buffer(Okio.source(rawSocket));
            sink = Okio.buffer(Okio.sink(rawSocket));
        } catch (NullPointerException npe) {
            if (NPE_THROW_WITH_NULL.equals(npe.getMessage())) {
                throw new IOException(npe);
            }
        }
    }
```
这里实际就是socket的实际连接了，到这里客户端和服务端应该就已经建立连接了，下面就是HTTP协议的内容了。

### 第五步，根据协议进行连接，HTTP直接返回，HTTPS使用http2Connection去处理
```
private void establishProtocol(ConnectionSpecSelector connectionSpecSelector,
                                   int pingIntervalMillis, Call call, EventListener eventListener) throws IOException {
        // 为null的时候为HTTP连接
        if (route.address().sslSocketFactory() == null) {
            // 通过HTTP运行的HTTPS
            if (route.address().protocols().contains(Protocol.H2_PRIOR_KNOWLEDGE)) {
                socket = rawSocket;
                protocol = Protocol.H2_PRIOR_KNOWLEDGE;
                // 上面设置好socket、协议，用http2Connection去处理
                startHttp2(pingIntervalMillis);
                return;
            }

            // HTTP协议使用rawSocket就可以了，并且到这就连接结束了
            socket = rawSocket;
            protocol = Protocol.HTTP_1_1;
            return;
        }

        eventListener.secureConnectStart(call);
        // 进行TLS连接，sslSocket会代替原始socket，输入输出流会更新
        // HTTPS是运行在SSL\TLS协议之上的，HTTP2使用了SSL\TLS协议，实际就是HTTPS
        connectTls(connectionSpecSelector);
        eventListener.secureConnectEnd(call, handshake);

        if (protocol == Protocol.HTTP_2) {
            // 使用Http2Connection进行处理
            startHttp2(pingIntervalMillis);
        }
    }
```
这里有三种情况，一个是运行在HTTP上的HTTPS，一个正常的HTTP，一个正常的HTTPS。到这HTTP协议就结束了，可以使用原始socket进行输入输出流读写数据，使用Http1Codec处理。

剩下的HTTPS就到了startHttp2这个方法中：
```
private void startHttp2(int pingIntervalMillis) throws IOException {
        // HTTP/2 连接超时是按流设置的，指后面再设置吗？
        socket.setSoTimeout(0); // HTTP/2 connection timeouts are set per-stream.
        http2Connection = new Http2Connection.Builder(true)
                .socket(socket, route.address().url().host(), source, sink)
                // onStream和onSetting两个方法
                .listener(this)
                .pingIntervalMillis(pingIntervalMillis)
                .build();
        http2Connection.start();
    }
```
下面新开一个一个章节来学习Http2Connection，这个也超级复杂。。我也还挺懵的。

## Http2Connection
这里看源码之前最好学习下HTTP2相关的内容，HTTP2就是HTTPS，HTTPS就是HTTP+TLS，至少要知道HTTP2是分帧的、多路复用、流的概念等。下面是我找的的一篇不错的文章：

[半小时搞懂 HTTP、HTTPS和HTTP2](https://juejin.cn/post/6894053426112495629)

读完上面这篇文章，估计就能对HTTP2有个大致了解了，去读源码没那么懵逼了，下面是我理解的源码：

### Http2Connection构建方法
```
Http2Connection(Builder builder) {
        pushObserver = builder.pushObserver;
        client = builder.client;
        listener = builder.listener;
        // http://tools.ietf.org/html/draft-ietf-httpbis-http2-17#section-5.1.1

        // 这里做了说明，client用单数StreamId，server用偶数StreamId，并且1作为Upgrade帧？
        nextStreamId = builder.client ? 1 : 2;
        if (builder.client) {
            nextStreamId += 2; // In HTTP/2, 1 on client is reserved for Upgrade.
        }

        // Flow control was designed more for servers, or proxies than edge clients.
        // If we are a client, set the flow control window to 16MiB.  This avoids
        // thrashing window updates every 64KiB, yet small enough to avoid blowing
        // up the heap.
        if (builder.client) {
            okHttpSettings.set(Settings.INITIAL_WINDOW_SIZE, OKHTTP_CLIENT_WINDOW_SIZE);
        }

        hostname = builder.hostname;

        writerExecutor = new ScheduledThreadPoolExecutor(1,
                Util.threadFactory(Util.format("OkHttp %s Writer", hostname), false));
        if (builder.pingIntervalMillis != 0) {
            writerExecutor.scheduleAtFixedRate(new Http2Connection1.PingRunnable(false, 0, 0),
                    builder.pingIntervalMillis, builder.pingIntervalMillis, MILLISECONDS);
        }

        // Like newSingleThreadExecutor, except lazy creates the thread.
        pushExecutor = new ThreadPoolExecutor(0, 1, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(),
                Util.threadFactory(Util.format("OkHttp %s Push Observer", hostname), true));
        peerSettings.set(Settings.INITIAL_WINDOW_SIZE, DEFAULT_INITIAL_WINDOW_SIZE);
        peerSettings.set(Settings.MAX_FRAME_SIZE, Http2.INITIAL_MAX_FRAME_SIZE);
        bytesLeftInWriteWindow = peerSettings.getInitialWindowSize();
        socket = builder.socket;
        writer = new Http2Writer(builder.sink, client);

        // 这里，Http2Writer和Http2Reader传入了socket的输入输出流
        readerRunnable = new Http2Connection1.ReaderRunnable(new Http2Reader(builder.source, client));
    }
```
上面的startHttp2方法通过Builder创建了Http2Connection，这个构造方法还是要瞧一下的，关键的地方我注释了。

client应该是一个标识位，也就是说当前TCP连接中，一端既可以作为peer也可以作为client，不同的角色选择不同奇偶性的nextStreamId。

### start方法
```
    public void start() throws IOException {
        start(true);
    }
    
    void start(boolean sendConnectionPreface) throws IOException {
        if (sendConnectionPreface) {
            // 向输出流写入一些配置
            writer.connectionPreface();
            writer.settings(okHttpSettings);
            // stream的窗口值
            int windowSize = okHttpSettings.getInitialWindowSize();
            if (windowSize != Settings.DEFAULT_INITIAL_WINDOW_SIZE) {
                writer.windowUpdate(0, windowSize - Settings.DEFAULT_INITIAL_WINDOW_SIZE);
            }
        }
        new Thread(readerRunnable).start(); // Not a daemon thread.
    }
```
RealConnection中调用了startHttp2方法，这里做了一些处理后，居然进入到了一个new Thread。。。okhttp也和我这个野生程序员一样写吗？

这个readerRunnable是在上面的构造方法里创建的，也就是说后面的功能都在readerRunnable处理了，下面我们看下这个runnable：
```
    class ReaderRunnable extends NamedRunnable implements Http2Reader.Handler {
        final Http2Reader reader;

        ReaderRunnable(Http2Reader reader) {
            super("OkHttp %s", hostname);
            this.reader = reader;
        }

        @Override protected void execute() {
            ErrorCode connectionErrorCode = ErrorCode.INTERNAL_ERROR;
            ErrorCode streamErrorCode = ErrorCode.INTERNAL_ERROR;
            try {
                // server读取connectionPreface进行比较，client读取下一帧
                reader.readConnectionPreface(this);

                // 进入Http2Reader中，循环读取帧，通过回调在当前runnable中处理
                // HTTP/2 是基于帧的协议。采用分帧是为了将重要信息封装起来，让协议的解析方可以轻松阅读、解析并还原信息。
                // 有多种帧类型，每次处理不同的帧，回调到当前runnable中处理 //see: https://zhuanlan.zhihu.com/p/141458270
                while (reader.nextFrame(false, this)) {
                }
                // 循环体结束，连接结束
                connectionErrorCode = ErrorCode.NO_ERROR;
                streamErrorCode = ErrorCode.CANCEL;
            } catch (IOException e) {
                connectionErrorCode = ErrorCode.PROTOCOL_ERROR;
                streamErrorCode = ErrorCode.PROTOCOL_ERROR;
            } finally {
                try {
                    close(connectionErrorCode, streamErrorCode);
                } catch (IOException ignored) {
                }
                Util.closeQuietly(reader);
            }
        }
        
        // 其他代码。。。
```
这个NamedRunnable前面见过了，大致就是在run方法里调用execute方法，所以着重看execute方法。这里就是用Http2Reader去读取数据了，readConnectionPreface好像是HTTP/2连接前奏的连接前奏：
> https://quafoo.gitbooks.io/http2-rfc7540-zh-cn-en/content/chapter3/section3.5.html

然后，就是最重要的部分了: nextFrame，点进去看下: 
```
public boolean nextFrame(boolean requireSettings, Handler handler) throws IOException {
    try {
      source.require(9); // Frame header size
    } catch (IOException e) {
      return false; // This might be a normal socket close.
    }

    //  0                   1                   2                   3
    //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |                 Length (24)                   |
    // +---------------+---------------+---------------+
    // |   Type (8)    |   Flags (8)   |
    // +-+-+-----------+---------------+-------------------------------+
    // |R|                 Stream Identifier (31)                      |
    // +=+=============================================================+
    // |                   Frame Payload (0...)                      ...
    // +---------------------------------------------------------------+
    int length = readMedium(source);
    if (length < 0 || length > INITIAL_MAX_FRAME_SIZE) {
      throw ioException("FRAME_SIZE_ERROR: %s", length);
    }
    byte type = (byte) (source.readByte() & 0xff);
    if (requireSettings && type != TYPE_SETTINGS) {
      throw ioException("Expected a SETTINGS frame but was %s", type);
    }
    byte flags = (byte) (source.readByte() & 0xff);
    int streamId = (source.readInt() & 0x7fffffff); // Ignore reserved bit.
    if (logger.isLoggable(FINE)) logger.fine(frameLog(true, streamId, length, type, flags));

    switch (type) {
      case TYPE_DATA:
        readData(handler, length, flags, streamId);
        break;

      // 省略了一些情况。。。
      
      default:
        // Implementations MUST discard frames that have unknown or unsupported types.
        source.skip(length);
    }
    return true;
  }
```
这里的注释写得很漂亮，这里就是对HTTP2的帧进行读取，我们取TYPE_DATA看下调用链:
```
private void readData(Handler handler, int length, byte flags, int streamId)
      throws IOException {
    if (streamId == 0) throw ioException("PROTOCOL_ERROR: TYPE_DATA streamId == 0");

    // TODO: checkState open or half-closed (local) or raise STREAM_CLOSED
    boolean inFinished = (flags & FLAG_END_STREAM) != 0;
    boolean gzipped = (flags & FLAG_COMPRESSED) != 0;
    if (gzipped) {
      throw ioException("PROTOCOL_ERROR: FLAG_COMPRESSED without SETTINGS_COMPRESS_DATA");
    }

    short padding = (flags & FLAG_PADDED) != 0 ? (short) (source.readByte() & 0xff) : 0;
    length = lengthWithoutPadding(length, flags, padding);

    handler.data(inFinished, streamId, source, length);
    source.skip(padding);
  }
```
标志位的读取我们忽略，最终到了handler.data方法，这个handler实际就是我们nextFrame(false, this)闯进去的Http2Connection，那就回到了Http2Connection的data方法：
```
    @Override public void data(boolean inFinished, int streamId, BufferedSource source, int length)
                throws IOException {
            // (推测)由于多路复用、乱序发送，所以提前读取的帧延时稍后处理？还是别的流的帧?
            // 好像是pushedStream是服务端，所以作为服务端要晚一点发送数据？等启动后台任务后发送吗？
            if (pushedStream(streamId)) {
                pushDataLater(streamId, source, length, inFinished);
                return;
            }
            // 根据streamId拿到对应的dataStream，每个HTTPS请求会在TCP连接上创建一个流
            Http2Stream dataStream = getStream(streamId);
            if (dataStream == null) {
                writeSynResetLater(streamId, ErrorCode.PROTOCOL_ERROR);
                updateConnectionFlowControl(length);
                source.skip(length);
                return;
            }
            // 将拿到的帧数据写到对应dataStream
            dataStream.receiveData(source, length);
            if (inFinished) {
                dataStream.receiveFin();
            }
        }
```
还记得上面的ReaderRunnable吗？他这里实现了Http2Reader.Handler这个接口，data等一系列的方法就是从这里面继承过来的，目的就是处理不同的HTTP2的帧。
```
class ReaderRunnable extends NamedRunnable implements Http2Reader.Handler {
```
这里有个pushedStream和pushDataLater我不是很懂，一开始以为是帧多路复用、乱序发送要排序呢，又看了下下面方法的注释，好像是说作为服务端的时候，先收到了数据，要延后处理，等启动后台任务再处理(我是这么理解的)
```
    boolean pushedStream(int streamId) {
        // 这就用到上面说的streamId奇偶性吧，这里(streamId & 1)验证是不是奇数，返回true:非0且为偶数，偶数是服务端
        return streamId != 0 && (streamId & 1) == 0;
    }
    
    /**
     * Eagerly reads {@code byteCount} bytes from the source before launching a background task to
     * process the data.  This avoids corrupting the stream.
     */
    // 在启动后台任务以处理数据之前，急切地从源读取 byteCount 字节。这避免了破坏流。
    void pushDataLater(final int streamId, final BufferedSource source, final int byteCount,
                       final boolean inFinished) throws IOException {
        final Buffer buffer = new Buffer();
        // 在触发客户端线程之前急切地阅读框架。
        source.require(byteCount); // Eagerly read the frame before firing client thread.
        source.read(buffer, byteCount);()
        if (buffer.size() != byteCount) throw new IOException(buffer.size() + " != " + byteCount);
        pushExecutorExecute(new NamedRunnable("OkHttp %s Push Data[%s]", hostname, streamId) {
            @Override public void execute() {
                try {
                    // 该方法注释: 与推送请求对应的响应数据块。必须读取或跳过此数据
                    // pushObserver在构造方法中通过builder传入，默认是PushObserver.CANCEL，即跳过
                    boolean cancel = pushObserver.onData(streamId, buffer, byteCount, inFinished);
                    if (cancel) writer.rstStream(streamId, ErrorCode.CANCEL);
                    if (cancel || inFinished) {
                        synchronized (Http2Connection.this) {
                            currentPushRequests.remove(streamId);
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        });
    }
```
接下来的代码就到了拿去对应streamId的Http2Stream，去接收数据，这里Http2Stream我就不追进去了，大致看了下，就是把这里的数据写入到里面的source里面，这个source是Http2Stream构造的时候根据connection.okHttpSettings创建的。

其他读取帧的操作应该类似，这里就不管了。

### newStream方法
前面讲解HttpCodec的时候，我只讲了Http1Codec，然后说Http2Codec差不多，好吧，我错了，这里讲到Http2Stream之后，Http2Codec就是通过Http2Stream来实现流的操作的，也还是HttpCodec那一套，这里就看下下面这个方法：
```
  @Override public void writeRequestHeaders(Request request) throws IOException {
    if (stream != null) return;

    boolean hasRequestBody = request.body() != null;
    List<Header> requestHeaders = http2HeadersList(request);
    stream = connection.newStream(requestHeaders, hasRequestBody);
    stream.readTimeout().timeout(chain.readTimeoutMillis(), TimeUnit.MILLISECONDS);
    stream.writeTimeout().timeout(chain.writeTimeoutMillis(), TimeUnit.MILLISECONDS);
  }
```
这里通过connection(Http2Connection)创建了stream，下面就来分析下：
```
    public Http2Stream newStream(List<Header> requestHeaders, boolean out) throws IOException {
        // Http2Codec的writeRequestHeaders会调用这里，创建一个Http2Stream，输入输出都是在一个流上
        return newStream(0, requestHeaders, out);
    }
    
    private Http2Stream newStream(
            int associatedStreamId, List<Header> requestHeaders, boolean out) throws IOException {
        boolean outFinished = !out;
        boolean inFinished = false;
        boolean flushHeaders;
        Http2Stream stream;
        int streamId;

        synchronized (writer) {
            synchronized (this) {
                if (nextStreamId > Integer.MAX_VALUE / 2) {
                    shutdown(REFUSED_STREAM);
                }
                if (shutdown) {
                    throw new ConnectionShutdownException();
                }
                // 输入输出流，所以加2，奇偶用来区分是服务端还是客户端(奇)
                streamId = nextStreamId;
                nextStreamId += 2;
                // 创建的Http2Stream会用来接收Http2Reader里面的帧数据，写入的时候会通过Http2Reader写入
                stream = new Http2Stream(streamId, this, outFinished, inFinished, null);
                flushHeaders = !out || bytesLeftInWriteWindow == 0L || stream.bytesLeftInWriteWindow == 0L;
                if (stream.isOpen()) {
                    // 保存起来，一个连接可以有多个HTTPS请求，即多个Http2Stream
                    streams.put(streamId, stream);
                }
            }
            if (associatedStreamId == 0) {
                // 发送请求头，associatedStreamId==0就是刚开始吧
                writer.synStream(outFinished, streamId, associatedStreamId, requestHeaders);
            } else if (client) {
                throw new IllegalArgumentException("client streams shouldn't have associated stream IDs");
            } else { // HTTP/2 has a PUSH_PROMISE frame.
                writer.pushPromise(associatedStreamId, streamId, requestHeaders);
            }
        }

        if (flushHeaders) {
            writer.flush();
        }

        return stream;
    }
```
上面一节写了很长的ReaderRunnable，这个是对帧的读取，那发送数据在哪里呢？没错，就是这里了，还记得前面Http2Connection构造函数里的writer吗？
```
writer = new Http2Writer(builder.sink, client);
```
发送数据以writer的synStream为例追踪，最终还是回到了sink里面:
```
  public synchronized void synStream(boolean outFinished, int streamId,
      int associatedStreamId, List<Header> headerBlock) throws IOException {
    if (closed) throw new IOException("closed");
    headers(outFinished, streamId, headerBlock);
  }
  
  void headers(boolean outFinished, int streamId, List<Header> headerBlock) throws IOException {
    if (closed) throw new IOException("closed");
    hpackWriter.writeHeaders(headerBlock);

    long byteCount = hpackBuffer.size();
    int length = (int) Math.min(maxFrameSize, byteCount);
    byte type = TYPE_HEADERS;
    byte flags = byteCount == length ? FLAG_END_HEADERS : 0;
    if (outFinished) flags |= FLAG_END_STREAM;
    frameHeader(streamId, length, type, flags);
    sink.write(hpackBuffer, length);

    if (byteCount > length) writeContinuationFrames(streamId, byteCount - length);
  }
```
上面也就是Http2Codec的writeRequestHeaders最终执行的地方，而Http2Codec的createRequestBody直接就把stream的Sink返回了，要写入body直接就往Sink写就行了。
```
  @Override public Sink createRequestBody(Request request, long contentLength) {
    return stream.getSink();
  }
```

## 小结
关于RealConnection的内容差不多就是这样了，整篇文章还是以我自己的理解为主吧，可能有不对的地方，可以评论区指出，一起进步！