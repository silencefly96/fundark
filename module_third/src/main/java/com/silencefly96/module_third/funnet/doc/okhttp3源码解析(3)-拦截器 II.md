# okhttp3源码解析(3)-拦截器 II
## 前言
上篇博文从RealInterceptorChain开始，讲解了RetryAndFollowUpInterceptor和BridgeInterceptor两个拦截器，后面还有三个系统拦截器，其实都类似，只是实现的功能不一样罢了，这篇文章将来介绍剩下的这几个系统拦截器。

[okhttp3源码解析(2)-拦截器 I](https://blog.csdn.net/lfq88/article/details/129952216)

## CacheInterceptor
CacheInterceptor是一个缓存拦截器，主要功能就是根据缓存策略取缓存、发起请求、保存缓存，下面开讲。

### InternalCache
CacheInterceptor通过持有的InternalCache对象来实现缓存：
```
  // OkHttpClient内
  InternalCache internalCache() {
    return cache != null ? cache.internalCache : internalCache;
  }
```
CacheInterceptor的InternalCache对象从构造函数传入，最终来源为OkHttpClient内的internalCache方法，这里有两个cache，我们看下他们来源：
```
    /** Sets the response cache to be used to read and write cached responses. */
    void setInternalCache(@Nullable InternalCache internalCache) {
      this.internalCache = internalCache;
      this.cache = null;
    }

    /** Sets the response cache to be used to read and write cached responses. */
    public Builder cache(@Nullable Cache cache) {
      this.cache = cache;
      this.internalCache = null;
      return this;
    }
```
这里绕的有点晕了，大致意思就是internalCache和cache两个不能同时存在吧，如果设置了就用其中有的那个。

看起来Cache对象包含了一个InternalCache成员，我们继续看下Cache类：
```
  final InternalCache internalCache = new InternalCache() {
    @Override public Response get(Request request) throws IOException {
      return Cache.this.get(request);
    }

    @Override public CacheRequest put(Response response) throws IOException {
      return Cache.this.put(response);
    }

    @Override public void remove(Request request) throws IOException {
      Cache.this.remove(request);
    }

    @Override public void update(Response cached, Response network) {
      Cache.this.update(cached, network);
    }

    @Override public void trackConditionalCacheHit() {
      Cache.this.trackConditionalCacheHit();
    }

    @Override public void trackResponse(CacheStrategy cacheStrategy) {
      Cache.this.trackResponse(cacheStrategy);
    }
  };
```
哦，原来这里Cache就是InternalCache的一个装饰模式，最终用InternalCache的接口方法实现了缓存功能，也就是说InternalCache是一个接口，而Cache是一个实现类。

不过这时候我还是有点懵逼，那到底谁是默认的缓存呢？
```
  // OkHttpClient内
  InternalCache internalCache() {
    return cache != null ? cache.internalCache : internalCache;
  }
```
再来看下OkHttpClient的internalCache方法，CacheInterceptor的InternalCache是这里提供的，cache是优先于internalCache的，那么cache和internalCache的默认值呢？
```
    public Builder() {
      dispatcher = new Dispatcher();
      protocols = DEFAULT_PROTOCOLS;
      connectionSpecs = DEFAULT_CONNECTION_SPECS;
      eventListenerFactory = EventListener.factory(EventListener.NONE);
      proxySelector = ProxySelector.getDefault();
      if (proxySelector == null) {
        proxySelector = new NullProxySelector();
      }
      cookieJar = CookieJar.NO_COOKIES;
      socketFactory = SocketFactory.getDefault();
      hostnameVerifier = OkHostnameVerifier.INSTANCE;
      certificatePinner = CertificatePinner.DEFAULT;
      proxyAuthenticator = Authenticator.NONE;
      authenticator = Authenticator.NONE;
      connectionPool = new ConnectionPool();
      dns = Dns.SYSTEM;
      followSslRedirects = true;
      followRedirects = true;
      retryOnConnectionFailure = true;
      callTimeout = 0;
      connectTimeout = 10_000;
      readTimeout = 10_000;
      writeTimeout = 10_000;
      pingInterval = 0;
    }
```
Sorry，OkHttpClient.Builder的构造函数并没有提供默认值，所以两者都没有默认值。

小结下，这里CacheInterceptor持有的InternalCache对象，需要自己在OkHttpClient.Builder中设置，Cache是一个实现类，我们用它就行了，如果有能力也可以实现InternalCache接口自己实现一套，通过setInternalCache方法设置。

这里花了很长篇幅研究了下CacheInterceptor持有的InternalCache对象，对于一般情况，缓存都是通过Cache类实现的，里面内容很多，也很有价值，但是现在是研究okhttp，所以这里不讲，希望读者自行查看。下面来讲拦截器的功能实现点：intercept方法。

### 第一步，获得请求的缓存，得到缓存策略(需要的网络请求、本地缓存的回复)，并进行验证
```
    // 缓存中取出的Response候选者
    Response cacheCandidate = cache != null
        ? cache.get(chain.request())
        : null;

    long now = System.currentTimeMillis();

    // 得到一个缓存策略
    CacheStrategy strategy = new CacheStrategy.Factory(now, chain.request(), cacheCandidate).get();
    Request networkRequest = strategy.networkRequest;
    Response cacheResponse = strategy.cacheResponse;

    if (cache != null) {
      // 这里不是执行，而是对本次击中缓存的情况做记录
      cache.trackResponse(strategy);
    }

    // 没有本地缓存，可以关闭缓存了
    if (cacheCandidate != null && cacheResponse == null) {
      closeQuietly(cacheCandidate.body()); // The cache candidate wasn't applicable. Close it.
    }

    // If we're forbidden from using the network and the cache is insufficient, fail.
    // 不需要网络请求，同时没有缓存，无法访问504
    if (networkRequest == null && cacheResponse == null) {
      return new Response.Builder()
          .request(chain.request())
          .protocol(Protocol.HTTP_1_1)
          .code(504)
          .message("Unsatisfiable Request (only-if-cached)")
          .body(Util.EMPTY_RESPONSE)
          .sentRequestAtMillis(-1L)
          .receivedResponseAtMillis(System.currentTimeMillis())
          .build();
    }

    // If we don't need the network, we're done.
    // 不需要网络请求，从缓存返回
    if (networkRequest == null) {
      return cacheResponse.newBuilder()
          .cacheResponse(stripBody(cacheResponse))
          .build();
    }
```
这里看得也很让我懵逼，逻辑的核心是理解CacheStrategy这个类，搞清楚它是干嘛的，先看下它自己的说明：
> Given a request and cached response, this figures out whether to use the network, the cache, or both. 
> Selecting a cache strategy may add conditions to the request (like the "If-Modified-Since" header for conditional GETs) or warnings to the cached response (if the cached data is potentially stale).

意思就是(垃圾机翻)：
1. 给定一个请求和缓存的响应，这会确定是使用网络、缓存还是同时使用两者。 
2. 选择缓存策略可能会向请求添加条件（如条件 GET 的“If-Modified-Since”标头）或对缓存响应的警告（如果缓存数据可能过时）。

搞懂CacheStrategy这个类，上面的情况就简单了。通过CacheStrategy.Factory的get方法获得strategy后，会得到两个产物：networkRequest和cacheResponse。

networkRequest是可能用到的网络请求，如果它为空，那就不发送网络请求。而cacheResponse是对cacheCandidate(原生缓存的Response)校验后的结果，如果为空，就是没缓存或者cacheCandidate已经过期了。

### 第二步，根据需要的网络请求，通过责任链下一步发起请求，获得网络回复
```
Response networkResponse = null;
    try {
      networkResponse = chain.proceed(networkRequest);
    } finally {
      // If we're crashing on I/O or otherwise, don't leak the cache body.
      // 出现异常了，关闭缓存Response的body(是一个流)
      if (networkResponse == null && cacheCandidate != null) {
        closeQuietly(cacheCandidate.body());
      }
    }

    // If we have a cache response too, then we're doing a conditional get.
    // 获取网络回复后，还有本地缓存的response，根据条件处理(未修改: 更新header、缓存)
    if (cacheResponse != null) {
      if (networkResponse.code() == HTTP_NOT_MODIFIED) {
        Response response = cacheResponse.newBuilder()
            .headers(combine(cacheResponse.headers(), networkResponse.headers()))
            .sentRequestAtMillis(networkResponse.sentRequestAtMillis())
            .receivedResponseAtMillis(networkResponse.receivedResponseAtMillis())
            .cacheResponse(stripBody(cacheResponse))
            .networkResponse(stripBody(networkResponse))
            .build();
        networkResponse.body().close();

        // Update the cache after combining headers but before stripping the
        // Content-Encoding header (as performed by initContentStream()).
        cache.trackConditionalCacheHit();
        cache.update(cacheResponse, response);
        return response;
      } else {
        closeQuietly(cacheResponse.body());
      }
    }
```
这里是处理需要网络请求的情况(networkRequest!=null)，发起了网络请求，并对同时有cacheResponse的情况做了处理。

### 第三步，通过网络回复及缓存回复创建实际的response，并缓存
```
    Response response = networkResponse.newBuilder()
        .cacheResponse(stripBody(cacheResponse))
        .networkResponse(stripBody(networkResponse))
        .build();

    // 更新缓存
    if (cache != null) {
      if (HttpHeaders.hasBody(response) && CacheStrategy.isCacheable(response, networkRequest)) {
        // Offer this request to the cache.
        CacheRequest cacheRequest = cache.put(response);
        return cacheWritingResponse(cacheRequest, response);
      }

      if (HttpMethod.invalidatesCache(networkRequest.method())) {
        try {
          cache.remove(networkRequest);
        } catch (IOException ignored) {
          // The cache cannot be written.
        }
      }
    }

    return response;
```
这里将cacheResponse和networkResponse去掉body后保存在新的response中(根据networkResponse创建，body为networkResponse的)，并对缓存进行了更新。

### 小结
上面对CacheInterceptor进行了分析，只分析主要流程，很多细节都过了，比如通过CacheStrategy和Cache的原理，有时间再瞧瞧了。

## ConnectInterceptor
ConnectInterceptor估计是这五个系统拦截器里面最简单的了，这里将intercept代码发出来就知道它的功能了。
```
  @Override public Response intercept(Chain chain) throws IOException {
    RealInterceptorChain realChain = (RealInterceptorChain) chain;
    Request request = realChain.request();
    StreamAllocation streamAllocation = realChain.streamAllocation();

    // We need the network to satisfy this request. Possibly for validating a conditional GET.
    boolean doExtensiveHealthChecks = !request.method().equals("GET");
    HttpCodec httpCodec = streamAllocation.newStream(client, chain, doExtensiveHealthChecks);
    RealConnection connection = streamAllocation.connection();

    return realChain.proceed(request, streamAllocation, httpCodec, connection);
  }
```
这里其实就是创建了HttpCodec和RealConnection，并传入下一个拦截器，虽然很简单但还是挺重要的。

## CallServerInterceptor
上面的ConnectInterceptor创建了HttpCodec和RealConnection并传递到了CallServerInterceptor，到这，剩下的三个核心类都齐了：
1. StreamAllocation
2. HttpCodec
3. RealConnection
不过这三个类我们要下篇博文详解，先来看CallServerInterceptor的intercept方法，下面也可能部分涉及这三个类。
   
### 第一步，获取httpCodec、streamAllocation、connection等，下面进行最后的请求
```
    RealInterceptorChain realChain = (RealInterceptorChain) chain;
    HttpCodec httpCodec = realChain.httpStream();
    StreamAllocation streamAllocation = realChain.streamAllocation();
    RealConnection connection = (RealConnection) realChain.connection();
    Request request = realChain.request();

    long sentRequestMillis = System.currentTimeMillis();
```

### 第二步，使用httpCodec对request进行处理，对header进行写入(UTF8)
```
    // 通过httpCodec写入请求头
    realChain.eventListener().requestHeadersStart(realChain.call());
    httpCodec.writeRequestHeaders(request);
    realChain.eventListener().requestHeadersEnd(realChain.call(), request);

    // http 100-continue用于客户端在发送POST数据给服务器前，征询服务器情况，看服务器是否处理POST的数据，
    // 如果不处理，客户端则不上传POST数据，如果处理，则POST上传数据。在现实应用中，通过在POST大数据时，
    // 才会使用100-continue协议

    // 下面其实就是处理http 100-continue的情况：需要跳过一个回复，并多发一次request
    Response.Builder responseBuilder = null;
    if (HttpMethod.permitsRequestBody(request.method()) && request.body() != null) {
      // If there's a "Expect: 100-continue" header on the request, wait for a "HTTP/1.1 100
      // Continue" response before transmitting the request body. If we don't get that, return
      // what we did get (such as a 4xx response) without ever transmitting the request body.
      if ("100-continue".equalsIgnoreCase(request.header("Expect"))) {
      
        // !!执行flushRequest，即发送了一次request
        httpCodec.flushRequest();
        realChain.eventListener().responseHeadersStart(realChain.call());
        
        // 传入true的时候会返回null
        responseBuilder = httpCodec.readResponseHeaders(true);
      }

      // 一般情况 或者 100-continue时(对于readResponseHeaders返回null)，向服务器发送 requestBody
      if (responseBuilder == null) {
        // Write the request body if the "Expect: 100-continue" expectation was met.
        realChain.eventListener().requestBodyStart(realChain.call());
        long contentLength = request.body().contentLength();
        CountingSink requestBodyOut =
            new CountingSink(httpCodec.createRequestBody(request, contentLength));
        BufferedSink bufferedRequestBody = Okio.buffer(requestBodyOut);

        request.body().writeTo(bufferedRequestBody);
        bufferedRequestBody.close();
        realChain.eventListener()
            .requestBodyEnd(realChain.call(), requestBodyOut.successfulCount);
      } else if (!connection.isMultiplexed()) {
        // If the "Expect: 100-continue" expectation wasn't met, prevent the HTTP/1 connection
        // from being reused. Otherwise we're still obligated to transmit the request body to
        // leave the connection in a consistent state.
        streamAllocation.noNewStreams();
      }
    }
    
    // 刷新输出流，发送request
    httpCodec.finishRequest();
```
读懂这里的代码，首先要知道httpCodec的作用，它是一个流的处理工具，里面管理着socket的流，当我们调用flushRequest时，就会把request发送出去。

所以上面的代码就是通过httpCodec的writeRequestHeaders方法写入了header到发送流，又获得了requestBodyOut(输出body流)写入request的body，最后调用finishRequest发送出去。

这里 100-continue 的情况会多调用一次httpCodec的flushRequest方法，线性发送一次只包含header的request。

### 第三步，对response进行处理，从httpCodec输入流读取
```
    if (responseBuilder == null) {
      realChain.eventListener().responseHeadersStart(realChain.call());
      // 读取回复得状态信息创建responseBuilder
      responseBuilder = httpCodec.readResponseHeaders(false);
    }

    Response response = responseBuilder
        .request(request)
        .handshake(streamAllocation.connection().handshake())
        .sentRequestAtMillis(sentRequestMillis)
        .receivedResponseAtMillis(System.currentTimeMillis())
        .build();

    // 对http 100-continue结果处理，再读取一次(前面多发了一次)
    int code = response.code();
    if (code == 100) {
      // server sent a 100-continue even though we did not request one.
      // try again to read the actual response
      responseBuilder = httpCodec.readResponseHeaders(false);

      // 紧接着再读取一次回复的header
      response = responseBuilder
              .request(request)
              .handshake(streamAllocation.connection().handshake())
              .sentRequestAtMillis(sentRequestMillis)
              .receivedResponseAtMillis(System.currentTimeMillis())
              .build();

      code = response.code();
    }

    realChain.eventListener()
            .responseHeadersEnd(realChain.call(), response);

    // 通过httpCodec处理response的body: openResponseBody(response)
    if (forWebSocket && code == 101) {
      // Connection is upgrading, but we need to ensure interceptors see a non-null response body.
      response = response.newBuilder()
          .body(Util.EMPTY_RESPONSE)
          .build();
    } else {
      response = response.newBuilder()
          .body(httpCodec.openResponseBody(response))
          .build();
    }

    if ("close".equalsIgnoreCase(response.request().header("Connection"))
        || "close".equalsIgnoreCase(response.header("Connection"))) {
      streamAllocation.noNewStreams();
    }

    if ((code == 204 || code == 205) && response.body().contentLength() > 0) {
      throw new ProtocolException(
          "HTTP " + code + " had non-zero Content-Length: " + response.body().contentLength());
    }

    // 实际得到的Response，读取了headers并处理且存储了，而ResponseBody封装了source，
    // 需要在OkHttpCall(Retrofit类)中parseResponse，将rawBody处理
    return response;
```
这里实际也和上面的步骤对应，先读取回复的状态信息(header)并创建了responseBuilder，构建最后的response，对http 100-continue特殊处理，最后通过httpCodec的openResponseBody方法向response中传入了socket输入流的一段(httpCodec中source的封装)。

到这里就把最终的response拿到了，并且这个response还读取好了header以及body(虽然还是流，需要进一步处理)，OkHttpClient的主要就结束了。

## 小结
把源码看到这里，不知道读者是不是和我一样一脸懵逼：就这？这就完了吗？这是如何请求、如何发送出去的？okhttp牛逼的连接池呢？

唉，这里只是拦截器的功能结束了，我们漏了几个东西：
1. StreamAllocation
2. HttpCodec
3. RealConnection
发送请求、连接、路由、代理等等功能都在里面，我们下篇博文详细分析。