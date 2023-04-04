# okhttp3源码解析(2)-拦截器 I
## 前言
上一篇博文讲到了无论发起同步请求还是异步请求，okhttp最后都通过getResponseWithInterceptorChain方法得到response，里面是一系列的拦截器通过责任链形式实现。接下来这篇文章就来详细讲讲拦截器。

[okhttp3源码解析(1)-整体流程](https://blog.csdn.net/lfq88/article/details/129932098)

## 拦截器概述
这里我们再让看下getResponseWithInterceptorChain方法：
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
这里除了自定义的interceptors和networkInterceptors外，共有五个系统拦截器，分别是：
1. RetryAndFollowUpInterceptor
2. BridgeInterceptor
3. CacheInterceptor
4. ConnectInterceptor
5. CallServerInterceptor
这些拦截器实现了从重试、header处理、缓存、连接、请求等，下面我们会好好介绍下着五个拦截器，不过首先我们先看下RealInterceptorChain，看下它是怎么实现责任链串行执行的。

## RealInterceptorChain
RealInterceptorChain其实很简单，我们只要注意它的构造函数和proceed方法就行，其他的方法都是返回参数或修改参数。

### RealInterceptorChain构造
```
  public RealInterceptorChain(List<Interceptor> interceptors, StreamAllocation streamAllocation,
      HttpCodec httpCodec, RealConnection connection, int index, Request request, Call call,
      EventListener eventListener, int connectTimeout, int readTimeout, int writeTimeout) {
    this.interceptors = interceptors;
    this.connection = connection;
    this.streamAllocation = streamAllocation;
    this.httpCodec = httpCodec;
    this.index = index;
    this.request = request;
    this.call = call;
    this.eventListener = eventListener;
    this.connectTimeout = connectTimeout;
    this.readTimeout = readTimeout;
    this.writeTimeout = writeTimeout;
  }
```
这里的构造函数携带了很多参数，都比较好理解，这里我们需要着重看下StreamAllocation、HttpCodec以及RealConnection，这个后面详细讲讲。

### proceed方法
```
  public Response proceed(Request request, StreamAllocation streamAllocation, HttpCodec httpCodec,
      RealConnection connection) throws IOException {
    if (index >= interceptors.size()) throw new AssertionError();

    calls++;

    // If we already have a stream, confirm that the incoming request will use it.
    if (this.httpCodec != null && !this.connection.supportsUrl(request.url())) {
      throw new IllegalStateException("network interceptor " + interceptors.get(index - 1)
          + " must retain the same host and port");
    }

    // If we already have a stream, confirm that this is the only call to chain.proceed().
    if (this.httpCodec != null && calls > 1) {
      throw new IllegalStateException("network interceptor " + interceptors.get(index - 1)
          + " must call proceed() exactly once");
    }

    // Call the next interceptor in the chain.
    RealInterceptorChain next = new RealInterceptorChain(interceptors, streamAllocation, httpCodec,
        connection, index + 1, request, call, eventListener, connectTimeout, readTimeout,
        writeTimeout);
    Interceptor interceptor = interceptors.get(index);
    Response response = interceptor.intercept(next);

    // Confirm that the next interceptor made its required call to chain.proceed().
    if (httpCodec != null && index + 1 < interceptors.size() && next.calls != 1) {
      throw new IllegalStateException("network interceptor " + interceptor
          + " must call proceed() exactly once");
    }

    // Confirm that the intercepted response isn't null.
    if (response == null) {
      throw new NullPointerException("interceptor " + interceptor + " returned null");
    }

    if (response.body() == null) {
      throw new IllegalStateException(
          "interceptor " + interceptor + " returned a response with no body");
    }

    return response;
  }
```
这里异常情况稍微看下，基本都是IllegalStateException加一个NullPointerException，注意这里抛出了IOException，并会在getResponseWithInterceptorChain继续往上抛，到达AsyncCall或RealCall的execute方法处理。

着重看下里面的几行有效代码：
```
    // Call the next interceptor in the chain.
    RealInterceptorChain next = new RealInterceptorChain(interceptors, streamAllocation, httpCodec,
        connection, index + 1, request, call, eventListener, connectTimeout, readTimeout,
        writeTimeout);
    Interceptor interceptor = interceptors.get(index);
    Response response = interceptor.intercept(next);
```
实际就是根据自身参数又创建了一个RealInterceptorChain实列，修改interceptor的index，这里通过index(RealCall传入了0)拿到了第一个拦截器去执行intercept，并得到response，我开始看得是有点懵懂，接下来看下拦截器就明白了。

## Interceptor接口
```
/**
 * Observes, modifies, and potentially short-circuits requests going out and the corresponding
 * responses coming back in. Typically interceptors add, remove, or transform headers on the request
 * or response.
 */
public interface Interceptor {
  Response intercept(Chain chain) throws IOException;

  interface Chain {
    Request request();

    Response proceed(Request request) throws IOException;
    // 省略部分代码
  }
}
```
首先我们看下Interceptor接口，实际上它就一个方法intercept，该方法返回Response，向上抛出IOException(到达Chain的proceed方法)，这里有个Chain接口，它唯一的实现就是RealInterceptorChain，上面已经讲了。

## RetryAndFollowUpInterceptor
如果我们没有设置OkHttpClient的interceptors，第一个执行的拦截器就是RetryAndFollowUpInterceptor，回想下上篇博文，RetryAndFollowUpInterceptor是在RealCall的构造函数中创建的，下面着重看下intercept，其他方法基本都是从中延展出来的。

### 第一步，创建streamAllocation对象
```
    // 取了些数据后面使用
    Request request = chain.request();
    RealInterceptorChain realChain = (RealInterceptorChain) chain;
    Call call = realChain.call();
    EventListener eventListener = realChain.eventListener();

    StreamAllocation streamAllocation = new StreamAllocation(client.connectionPool(),
        createAddress(request.url()), call, eventListener, callStackTrace);
    this.streamAllocation = streamAllocation;
```
看StreamAllocation简介，其是一个连接下面三者的工具(Streams为HttpCodec)，这里创建的streamAllocation会随着责任链往下传递得去，现在暂时未使用到。
> This class coordinates the relationship between three entities:Connections、Streams、Calls

### 第二步，在循环中执行，可多次发送请求，直至获得结果
```
    int followUpCount = 0;
    Response priorResponse = null;
    while (true) {
      if (canceled) {
        streamAllocation.release();
        throw new IOException("Canceled");
      }
      // 省略好多代码
      
    }
```
这里注意下本个拦截器的名字RetryAndFollowUpInterceptor，重试和继续拦截器，所以这个循环的目的就是实现重试和继续。

### 第三步，获取结果，失败时进行重试
```
      Response response;
      boolean releaseConnection = true;
      try {
        response = realChain.proceed(request, streamAllocation, null, null);
        releaseConnection = false;
      } catch (RouteException e) {
        // The attempt to connect via a route failed. The request will not have been sent.
        // 连接时的失败情况，请求还未发送，选择是否掩盖异常进行重试，不掩盖则为false，跳出循环
        if (!recover(e.getLastConnectException(), streamAllocation, false, request)) {
          throw e.getFirstConnectException();
        }
        releaseConnection = false;
        continue;
      } catch (IOException e) {
        // An attempt to communicate with a server failed. The request may have been sent.
        // 和服务器通信失败，请求可能已发送
        boolean requestSendStarted = !(e instanceof ConnectionShutdownException);
        if (!recover(e, streamAllocation, requestSendStarted, request)) throw e;
        releaseConnection = false;
        continue;
      } finally {
        // We're throwing an unchecked exception. Release any resources.
        if (releaseConnection) {
          streamAllocation.streamFailed(null);
          streamAllocation.release();
        }
      }
```
这里篇幅有点长了，实际就做了两件事，一是调用realChain的proceed方法拿到response，二是对各个异常进行recover：
```
  /**
   * Report and attempt to recover from a failure to communicate with a server. Returns true if
   * {@code e} is recoverable, or false if the failure is permanent. Requests with a body can only
   * be recovered if the body is buffered or if the failure occurred before the request has been
   * sent.
   */
  private boolean recover(IOException e, StreamAllocation streamAllocation,
      boolean requestSendStarted, Request userRequest) {
    streamAllocation.streamFailed(e);

    // The application layer has forbidden retries.
    if (!client.retryOnConnectionFailure()) return false;

    // We can't send the request body again.
    if (requestSendStarted && userRequest.body() instanceof UnrepeatableRequestBody) return false;

    // This exception is fatal.
    if (!isRecoverable(e, requestSendStarted)) return false;

    // No more routes to attempt.
    if (!streamAllocation.hasMoreRoutes()) return false;

    // For failure recovery, use the same route selector with a new connection.
    return true;
  }
```
比较有意思的就是，如果对异常进行recover，那么就不会抛出异常，而是由continue进入下一次循环，也即是*重试*了。在finally里面则是通过releaseConnection判断是否需要释放连接，抛出异常的时候releaseConnection没有赋值，就会释放连接。

### 第四步，根据返回码判断是否需要再发起请求(验证、重定向、重试等)，不需要重新请求则返回response
```
      // Attach the prior response if it exists. Such responses never have a body.
      if (priorResponse != null) {
        // 上一个循环已经获得了回复，根据上一个回复信息创建response，储存上一个回复
        response = response.newBuilder()
            .priorResponse(priorResponse.newBuilder()
                    .body(null)
                    .build())
            .build();
      }
      
      Request followUp;
      try {
        followUp = followUpRequest(response, streamAllocation.route());
      } catch (IOException e) {
        streamAllocation.release();
        throw e;
      }

      if (followUp == null) {
        streamAllocation.release();
        return response;
      }
        
      // 关闭流
      closeQuietly(response.body());
        
      // 超出followUp次数
      if (++followUpCount > MAX_FOLLOW_UPS) {
        streamAllocation.release();
        throw new ProtocolException("Too many follow-up requests: " + followUpCount);
      }
      
      // 不可抵达
      if (followUp.body() instanceof UnrepeatableRequestBody) {
        streamAllocation.release();
        throw new HttpRetryException("Cannot retry streamed HTTP body", response.code());
      }
```
上面一步体现了重试的思想，那么这一步就是followUp了。大致意思就是通过followUpRequest函数判断下是否要发起下一个请求，如果需要会得到一个followUp的Request，并在下一次循环执行请求。
```
  /**
   * Figures out the HTTP request to make in response to receiving {@code userResponse}. This will
   * either add authentication headers, follow redirects or handle a client request timeout. If a
   * follow-up is either unnecessary or not applicable, this returns null.
   */
  private Request followUpRequest(Response userResponse, Route route) throws IOException {
    if (userResponse == null) throw new IllegalStateException();
    int responseCode = userResponse.code();

    final String method = userResponse.request().method();
    switch (responseCode) {
      case HTTP_PROXY_AUTH:
      case HTTP_UNAUTHORIZED:
      case HTTP_PERM_REDIRECT:
      case HTTP_TEMP_REDIRECT:
      // ...省略部分代码
    }
  }
```
followUpRequest这个函数比较长，大致意思就是根据返回码做出一些处理，并构建下一次的Request，大致就是验证、重定向、重试等，读者有兴趣可以详细看下。

### 第五步，判断是否是相同链接(重定向了)，不同链接则重新创建streamAllocation
```
      if (!sameConnection(response, followUp.url())) {
        streamAllocation.release();
        streamAllocation = new StreamAllocation(client.connectionPool(),
            createAddress(followUp.url()), call, eventListener, callStackTrace);
        this.streamAllocation = streamAllocation;
      } else if (streamAllocation.codec() != null) {
        throw new IllegalStateException("Closing the body of " + response
            + " didn't close its backing stream. Bad interceptor?");
      }

      // 下一个循环
      request = followUp;
      priorResponse = response;
```
这里就是判断下followUp的url是否变换了，如果变换了streamAllocation也要进行更新，因为它事处理连接、流、请求的工具。

到这就进入下一次循环了，同时RetryAndFollowUpInterceptor的大致功能也了解的差不多了，最终是在下面这行代码进入到了下一个拦截器：
```
response = realChain.proceed(request, streamAllocation, null, null);
```
注意streamAllocation在这里传递下去了，会到达RealInterceptorChain的proceed方法，里面会使用它创建个新的RealInterceptorChain，并进入到下一个拦截器，后续拦截器都能使用到这个实列。

## BridgeInterceptor
上面讲解了下RetryAndFollowUpInterceptor，比较复杂，一开始也看得我头疼，不过万事开头难，你不去尝试，没有经过锻炼，那永远都只是一个搬砖工，只有去试才能创造可能，才能有机会，才能有成长。

话又说多了，BridgeInterceptor这个拦截器相对RetryAndFollowUpInterceptor来说是简单多了，接下来我也分步骤讲解下intercept内的内容。

### 第一步，添加各种请求头
```
    Request userRequest = chain.request();
    Request.Builder requestBuilder = userRequest.newBuilder();

    // 根据已有请求头添加header(来自RetryAndFollowUpInterceptor的followUp)
    RequestBody body = userRequest.body();
    if (body != null) {
      MediaType contentType = body.contentType();
      if (contentType != null) {
        requestBuilder.header("Content-Type", contentType.toString());
      }

      long contentLength = body.contentLength();
      if (contentLength != -1) {
        requestBuilder.header("Content-Length", Long.toString(contentLength));
        requestBuilder.removeHeader("Transfer-Encoding");
      } else {
        requestBuilder.header("Transfer-Encoding", "chunked");
        requestBuilder.removeHeader("Content-Length");
      }
    }

    // 添加其他header
    if (userRequest.header("Host") == null) {
      requestBuilder.header("Host", hostHeader(userRequest.url(), false));
    }

    if (userRequest.header("Connection") == null) {
      requestBuilder.header("Connection", "Keep-Alive");
    }

    // If we add an "Accept-Encoding: gzip" header field we're responsible for also decompressing
    // the transfer stream.
    boolean transparentGzip = false;
    if (userRequest.header("Accept-Encoding") == null && userRequest.header("Range") == null) {
      transparentGzip = true;
      requestBuilder.header("Accept-Encoding", "gzip");
    }

    // 添加cookies
    List<Cookie> cookies = cookieJar.loadForRequest(userRequest.url());
    if (!cookies.isEmpty()) {
      requestBuilder.header("Cookie", cookieHeader(cookies));
    }

    if (userRequest.header("User-Agent") == null) {
      requestBuilder.header("User-Agent", Version.userAgent());
    }
```
这里就是向requestBuilder里面写入header，唯一需要注意的就是cookieJar，它是从OkHttpClient.Builder里面传过来的(如果没主动设置)：
```
// 默认值
cookieJar = CookieJar.NO_COOKIES;
```
```
  CookieJar NO_COOKIES = new CookieJar() {
    @Override public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
    }

    @Override public List<Cookie> loadForRequest(HttpUrl url) {
      return Collections.emptyList();
    }
  };
```
这里cookieJar的默认值就是NO_COOKIES，它在保存和读取cookies时都是不操作的。

### 第二步，通过责任链发起请求，进入下一个责任链，request的header填充完毕
```
Response networkResponse = chain.proceed(requestBuilder.build());
```
这里和RetryAndFollowUpInterceptor类似，就是调用RealInterceptorChain的proceed方法进入下一个责任链获得结果。

### 第三步，保存cookies
```
    HttpHeaders.receiveHeaders(cookieJar, userRequest.url(), networkResponse.headers());
```
这里是保存cookies，如果没对OkHttpClient.Builder传入cookieJar，就是默认的NO_COOKIES，不会对cookies操作。

### 第四步，根据回复进行处理(解压)，得到最终的response
```
    // 向networkResponse里写入userRequest并创建新的Response
    Response.Builder responseBuilder = networkResponse.newBuilder()
        .request(userRequest);

    // 对应上面transparentGzip=true的解压操作
    if (transparentGzip
        && "gzip".equalsIgnoreCase(networkResponse.header("Content-Encoding"))
        && HttpHeaders.hasBody(networkResponse)) {
      GzipSource responseBody = new GzipSource(networkResponse.body().source());
      Headers strippedHeaders = networkResponse.headers().newBuilder()
          .removeAll("Content-Encoding")
          .removeAll("Content-Length")
          .build();
      responseBuilder.headers(strippedHeaders);
      String contentType = networkResponse.header("Content-Type");
      responseBuilder.body(new RealResponseBody(contentType, -1L, Okio.buffer(responseBody)));
    }

    return responseBuilder.build();
```
以第二步前后做对比，就会发起其实这些操作是对称的，header操作、cookie的操作以及Gzip操作。

## 小结
写到这里篇幅也比较长了，但是拦截器的大致功能已经十分清楚了，剩下三个系统的拦截器，我们在下篇博文再讲解。