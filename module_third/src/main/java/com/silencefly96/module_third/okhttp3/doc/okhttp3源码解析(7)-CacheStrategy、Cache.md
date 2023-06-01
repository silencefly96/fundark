# okhttp3源码解析(7)-CacheStrategy、Cache
## 前言
okhttp3源码解析的系列又断更了很久，发生了很多事，但是生活和学习还是得继续，最近还是打起劲来了，慢慢去做事吧，写下来的文章就是自己最宝贵的财富。

在我前面的文章: [okhttp3源码解析(3)-拦截器 II](https://juejin.cn/post/7221694062682816571) 里面已经把CacheInterceptor进行了分析，但是里面的CacheStrategy和Cache只是简单说了下作用，这篇文章我想再详细写写。

## CacheStrategy
这里直接摘录我之前写的内容，给CacheStrategy一个简单介绍：

- 通过CacheStrategy.Factory的get方法获得strategy后，会得到两个产物：networkRequest和cacheResponse。
  
- networkRequest是可能用到的网络请求，如果它为空，那就不发送网络请求。而cacheResponse是对cacheCandidate(原生缓存的Response)校验后的结果，如果为空，就是没缓存或者cacheCandidate已经过期了。

![image.png](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/6b9060d9507144be8b7187dbc0e1ff9c~tplv-k3u1fbpfcp-watermark.image?)
下面再来解读源码，其实CacheStrategy对外公开的就两内容: Factory和isCacheable方法，先来看下isCacheable方法。

### isCacheable方法
```
 public static boolean isCacheable(Response response, Request request) {
    // Always go to network for uncacheable response codes (RFC 7231 section 6.1),
    // This implementation doesn't support caching partial content.
    switch (response.code()) {
      case HTTP_OK:
      case HTTP_NOT_AUTHORITATIVE:
      case HTTP_NO_CONTENT:
      case HTTP_MULT_CHOICE:
      case HTTP_MOVED_PERM:
      case HTTP_NOT_FOUND:
      case HTTP_BAD_METHOD:
      case HTTP_GONE:
      case HTTP_REQ_TOO_LONG:
      case HTTP_NOT_IMPLEMENTED:
      case StatusLine.HTTP_PERM_REDIRECT:
        // These codes can be cached unless headers forbid it.
        break;

      case HTTP_MOVED_TEMP:
      case StatusLine.HTTP_TEMP_REDIRECT:
        // These codes can only be cached with the right response headers.
        // http://tools.ietf.org/html/rfc7234#section-3
        // s-maxage is not checked because OkHttp is a private cache that should ignore s-maxage.
        if (response.header("Expires") != null
            || response.cacheControl().maxAgeSeconds() != -1
            || response.cacheControl().isPublic()
            || response.cacheControl().isPrivate()) {
          break;
        }
        // Fall-through.

      default:
        // All other codes cannot be cached.
        return false;
    }

    // A 'no-store' directive on request or response prevents the response from being cached.
    return !response.cacheControl().noStore() && !request.cacheControl().noStore();
  }
```
这个没什么好说的，一看就是判断Response是否能缓存的意思，不多说了，下面看Factory才是重点。

### Factory
在上面的图上面，也是看到Factory主要就一个构造函数和一个get方法。
```
    public Factory(long nowMillis, Request request, Response cacheResponse) {
      // 对三个参数赋值
      this.nowMillis = nowMillis;
      this.request = request;
      this.cacheResponse = cacheResponse;

      if (cacheResponse != null) {
        // 收集各种数据，保存到数据域
        this.sentRequestMillis = cacheResponse.sentRequestAtMillis();
        this.receivedResponseMillis = cacheResponse.receivedResponseAtMillis();
        Headers headers = cacheResponse.headers();
        for (int i = 0, size = headers.size(); i < size; i++) {
          String fieldName = headers.name(i);
          String value = headers.value(i);
          if ("Date".equalsIgnoreCase(fieldName)) {
            servedDate = HttpDate.parse(value);
            servedDateString = value;
          } else if ("Expires".equalsIgnoreCase(fieldName)) {
            expires = HttpDate.parse(value);
          } else if ("Last-Modified".equalsIgnoreCase(fieldName)) {
            lastModified = HttpDate.parse(value);
            lastModifiedString = value;
          } else if ("ETag".equalsIgnoreCase(fieldName)) {
            etag = value;
          } else if ("Age".equalsIgnoreCase(fieldName)) {
            ageSeconds = HttpHeaders.parseSeconds(value, -1);
          }
        }
      }
    }
```
这里大致意思就是通过构造函数得到了数据域的一些数据，例如三个传参、各种时间记录等。
```
    public CacheStrategy get() {
      CacheStrategy candidate = getCandidate();

      if (candidate.networkRequest != null && request.cacheControl().onlyIfCached()) {
        // We're forbidden from using the network and the cache is insufficient.
        return new CacheStrategy(null, null);
      }

      return candidate;
    }
```
再来看get方法，其实就是用getCandidate去获取合适的策略对象，candidate内保存了可能的request和可能的缓存response。
```
    private CacheStrategy getCandidate() {
      // No cached response. 没有旧的缓存
      if (cacheResponse == null) {
        return new CacheStrategy(request, null);
      }

      // Drop the cached response if it's missing a required handshake. HTTPS过期
      if (request.isHttps() && cacheResponse.handshake() == null) {
        return new CacheStrategy(request, null);
      }

      // If this response shouldn't have been stored, it should never be used
      // as a response source. This check should be redundant as long as the
      // persistence store is well-behaved and the rules are constant. 不该缓存(根据response得到的结果)
      if (!isCacheable(cacheResponse, request)) {
        return new CacheStrategy(request, null);
      }

      // 根据request得到是否使用缓存
      CacheControl requestCaching = request.cacheControl();
      if (requestCaching.noCache() || hasConditions(request)) {
        return new CacheStrategy(request, null);
      }

      CacheControl responseCaching = cacheResponse.cacheControl();

      // 当前响应的age，从服务器响应计算到now的millis
      long ageMillis = cacheResponseAge();
      // 新鲜度，分三种情况:不过期、到期时间-回复时间、(回复时间-上次修改时间)/10(RFC规范)
      long freshMillis = computeFreshnessLifetime();

      // 响应的服务日期之后无需验证即可服务的持续时间
      if (requestCaching.maxAgeSeconds() != -1) {
        freshMillis = Math.min(freshMillis, SECONDS.toMillis(requestCaching.maxAgeSeconds()));
      }

      // 最小的新鲜度
      long minFreshMillis = 0;
      if (requestCaching.minFreshSeconds() != -1) {
        minFreshMillis = SECONDS.toMillis(requestCaching.minFreshSeconds());
      }

      // max-stale指令表示客户端可以接受的过期响应的最大时间，超出的时间
      long maxStaleMillis = 0;
      if (!responseCaching.mustRevalidate() && requestCaching.maxStaleSeconds() != -1) {
        maxStaleMillis = SECONDS.toMillis(requestCaching.maxStaleSeconds());
      }

      // 新鲜度还能使用，即缓存被击中了
      if (!responseCaching.noCache() && ageMillis + minFreshMillis < freshMillis + maxStaleMillis) {
        Response.Builder builder = cacheResponse.newBuilder();
        // 虽然新鲜度符合，但是是默许了maxStaleMillis内的情况
        if (ageMillis + minFreshMillis >= freshMillis) {
          builder.addHeader("Warning", "110 HttpURLConnection \"Response is stale\"");
        }
        // 启发式？computeFreshnessLifetime计算新鲜度的第三种情况：(回复时间-上次修改时间)/10(RFC规范)
        long oneDayMillis = 24 * 60 * 60 * 1000L;
        if (ageMillis > oneDayMillis && isFreshnessLifetimeHeuristic()) {
          builder.addHeader("Warning", "113 HttpURLConnection \"Heuristic expiration\"");
        }
        return new CacheStrategy(null, builder.build());
      }

      // Find a condition to add to the request. If the condition is satisfied, the response body
      // will not be transmitted.
      String conditionName;
      String conditionValue;
      
      // Etag 帮助客户端判断缓存响应是否仍然有效,服务器发送响应时,带上资源的 Etag,客户端下次请求时,带上之前获取的 Etag
      // 如果 Etag 相同,返回 304,表示缓存有效,如果 Etag 不同,返回新的 Etag 和资源,表示缓存已过期
      if (etag != null) {
        conditionName = "If-None-Match";
        conditionValue = etag;
      } else if (lastModified != null) {
        conditionName = "If-Modified-Since";
        conditionValue = lastModifiedString;
      } else if (servedDate != null) {
        conditionName = "If-Modified-Since";
        conditionValue = servedDateString;
      } else {
        return new CacheStrategy(request, null); // No condition! Make a regular request.
      }

      // 添加上面condition的header
      Headers.Builder conditionalRequestHeaders = request.headers().newBuilder();
      Internal.instance.addLenient(conditionalRequestHeaders, conditionName, conditionValue);

      Request conditionalRequest = request.newBuilder()
          .headers(conditionalRequestHeaders.build())
          .build();
      return new CacheStrategy(conditionalRequest, cacheResponse);
    }
```
CacheStrategy最复杂的就是在这里了吧，注释写得很清楚了，主要就是HTTP协议的一些内容，注意下新鲜度的计算和判断。

## Cache
说了这么多，缓存的主角还没登场，前面已经讲到了Cache类才是InternalCache的实现类，先来看下有那些重要方法: 
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
      // 只是hitCount++
      Cache.this.trackConditionalCacheHit();
    }

    @Override public void trackResponse(CacheStrategy cacheStrategy) {
      // requestCount++，然后根据情况networkCount++或者hitCount++
      Cache.this.trackResponse(cacheStrategy);
    }
  };
```
除了trackConditionalCacheHit和trackResponse只是做一个记录作用，剩下的不就是增删查改了，下面就好好研究下。

### put方法
其实Cache类也不只是只有增删查改四个方法，但是我们一个一个看的去，慢慢地再来研究、解释遇到地内容，下面先看put方法：
```
  @Nullable CacheRequest put(Response response) {
    String requestMethod = response.request().method();

    // POST、PATCH、PUT、DELETE、MOVE的请求结果不进行缓存，还要移除对于请求的缓存
    if (HttpMethod.invalidatesCache(response.request().method())) {
      try {
        remove(response.request());
      } catch (IOException ignored) {
        // The cache cannot be written.
      }
      return null;
    }
    // 非GET请求不缓存
    if (!requestMethod.equals("GET")) {
      // Don't cache non-GET responses. We're technically allowed to cache
      // HEAD requests and some POST requests, but the complexity of doing
      // so is high and the benefit is low.
      return null;
    }

    // header中的Vary字段是否包含all(即“*”)，如果服务器对所有header字段验证地话，就不缓存了
    // Vary字段告诉缓存服务器在响应下一个请求时，要检查哪些请求头是否与之前的请求相同
    if (HttpHeaders.hasVaryAll(response)) {
      return null;
    }

    // 这里出现了一个Entry，是Cache的一个内部类，保持了response的一些信息
    Entry entry = new Entry(response);
    // DiskLruCaches是硬盘缓存，Cache的cache成员变量是DiskLruCaches类型的
    DiskLruCache.Editor editor = null;
    try {
      // 这个key方法值得注意下
      editor = cache.edit(key(response.request().url()));
      if (editor == null) {
        return null;
      }
      // 通过DiskLruCache.Editor获得输出流，将entry写入到该key对应的输出流
      entry.writeTo(editor);
      // CacheRequestImpl又是一个内部类，里面保存了editor，创建了cacheOut和body两个流可供外面使用
      return new CacheRequestImpl(editor);
    } catch (IOException e) {
      abortQuietly(editor);
      return null;
    }
  }
```
看完上面代码又懵逼了，前面还好理解，后面就摸不清头脑了，下面再细究。

#### Entry
Entry特别长，这里就不直接贴代码了，看下调用的构造方法：
```
    Entry(Response response) {
      this.url = response.request().url().toString();
      this.varyHeaders = HttpHeaders.varyHeaders(response);
      this.requestMethod = response.request().method();
      this.protocol = response.protocol();
      this.code = response.code();
      this.message = response.message();
      this.responseHeaders = response.headers();
      this.handshake = response.handshake();
      this.sentRequestMillis = response.sentRequestAtMillis();
      this.receivedResponseMillis = response.receivedResponseAtMillis();
    }
```
什么啊，就是response保存的一些信息嘛，这里还有另一个用Source创建的构造方法，很长，不过也是读取这些信息罢了。

下面还有几个方法，大致看下功能
1. writeTo 这个方法put里面也用到了，大致就是将entry的信息写入到editor提供的流里面。
2. readCertificateList和writeCertList 就是对证书的读取和写入了，前面HTTPS部分文章讲了很多。
3. matches 对缓存验证，url、method以及Vary里面要求的内容。
4. response 根据entry内的信息生成一个response，但是需要一个DiskLruCache.Snapshot。

其他都好理解，但是DiskLruCache.Snapshot又是什么东西？其实我从看到entry的属性事，我就有个问题，response的body保存在哪里？难道是这里吗？这里先这么认为，下面看到了再讲。

#### key
这里先忽略DiskLruCache，一千多行，估计得再写一篇文章来分析了，先看增删查改流程，现在是在put方法内，这里有个key方法要注意下：
```
  public static String key(HttpUrl url) {
    return ByteString.encodeUtf8(url.toString()).md5().hex();
  }
```
看代码表面意思估计就能理解了，这里保存的key是先是用utf-8编码，再取MD5，最后取十六进制数，因为MD5的碰撞的概率几乎为0，所以可以认为是唯一的。

#### CacheRequestImpl
上面注释我是这样写得：CacheRequestImpl又是一个内部类，里面保存了editor，创建了cacheOut和body两个流可供外面使用。确实是这样，它的构造函数已经包含很多信息了：
```
    CacheRequestImpl(final DiskLruCache.Editor editor) {
      this.editor = editor;
      this.cacheOut = editor.newSink(ENTRY_BODY);
      this.body = new ForwardingSink(cacheOut) {
        @Override public void close() throws IOException {
          synchronized (Cache.this) {
            if (done) {
              return;
            }
            done = true;
            // Cache的变量，这个是内部类，持有外部类引用
            writeSuccessCount++;
          }
          super.close();
          editor.commit();
        }
      };
    }
```
四个成员变量，editor是传递进来的，cacheOut由editor创建的空输出流(sink)，body则是对cacheOut的封装，重写了close函数，用作记录和通过editor刷新流。

done变量和abort没什么好说的，但是CacheRequestImpl类最重要的就是通过body()，向外公开body变量，我们来看下哪里使用了，是做什么的：
```
  private Response cacheWritingResponse(final CacheRequest cacheRequest, Response response)
      throws IOException {
    // Some apps return a null body; for compatibility we treat that like a null cache request.
    if (cacheRequest == null) return response;
    Sink cacheBodyUnbuffered = cacheRequest.body();
    if (cacheBodyUnbuffered == null) return response;

    final BufferedSource source = response.body().source();
    final BufferedSink cacheBody = Okio.buffer(cacheBodyUnbuffered);
```
body()只有一个地方使用，就是在CacheInterceptor的cacheWritingResponse方法中执行的，cacheWritingResponse使用的地方如下：
```
    // intercept方法中
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
```
这就是对CacheInterceptor缓存的地方啊！put方法的唯一使用地方也在这，这也帮我们解决了Entry只保存了header等信息，并没有保存body的问题，原来body是通过cacheRequest来保存的，那如何保存的呢？我们继续往下看：
```
private Response cacheWritingResponse(final CacheRequest cacheRequest, Response response)
      throws IOException {
    // 异常处理  
    // Some apps return a null body; for compatibility we treat that like a null cache request.
    if (cacheRequest == null) return response;
    Sink cacheBodyUnbuffered = cacheRequest.body();
    if (cacheBodyUnbuffered == null) return response;

    // 这个response是要缓存的response，所以是数据源
    final BufferedSource source = response.body().source();
    // 这个是我们cacheRequest body的输出流，也就是缓存的body！！！这俄格很重要
    final BufferedSink cacheBody = Okio.buffer(cacheBodyUnbuffered);

    Source cacheWritingSource = new Source() {
      boolean cacheRequestClosed;

      @Override public long read(Buffer sink, long byteCount) throws IOException {
        long bytesRead;
        try {
          // 从response.body读出数据，写入到sink中
          bytesRead = source.read(sink, byteCount);
        } catch (IOException e) {
          if (!cacheRequestClosed) {
            cacheRequestClosed = true;
            cacheRequest.abort(); // Failed to write a complete cache response.
          }
          throw e;
        }

        // 读取完成了
        if (bytesRead == -1) {
          if (!cacheRequestClosed) {
            cacheRequestClosed = true;
            cacheBody.close(); // The cache response is complete!
          }
          return -1;
        }

        // 好家伙，破案了，原来藏在这里！这里复制了一份到cacheBody
        sink.copyTo(cacheBody.buffer(), sink.size() - bytesRead, bytesRead);
        cacheBody.emitCompleteSegments();
        return bytesRead;
      }

      @Override public Timeout timeout() {
        return source.timeout();
      }

      @Override public void close() throws IOException {
        if (!cacheRequestClosed
            && !discard(this, HttpCodec.DISCARD_STREAM_TIMEOUT_MILLIS, MILLISECONDS)) {
          cacheRequestClosed = true;
          cacheRequest.abort();
        }
        source.close();
      }
    };

    String contentType = response.header("Content-Type");
    long contentLength = response.body().contentLength();
    
    // 这里根据response创建了一个新的response，在复制body的时候会同时复制一份到缓存中
    return response.newBuilder()
        .body(new RealResponseBody(contentType, contentLength, Okio.buffer(cacheWritingSource)))
        .build();
  }
```
整理下这个方法，意思就是将response重新生成一个response，创建一个新的source(body)，同时复制一份到缓存，这个方法有注释但是我没看懂想表达什么:
> Returns a new source that writes bytes to cacheRequest as they are read by the source consumer. This is careful to discard bytes left over when the stream is closed; otherwise we may never exhaust the source stream and therefore not complete the cached response

这里几个有意思的东西要提一下：
- 一个是为什么put方法返回的是cacheRequest，而不叫cacheResponse呢？没有注释，我也不清楚。
- 二是为什么cacheWritingResponse要创建个新的源呢？根据我前面对okhttp3不靠谱的理解，好像是body的源一直还是socket的输入流，只是做了一层封装，前面讲Body的时候好像漏掉了这里，看来这个独立的流才是真正的response的body流！
- 三就是CacheRequest凭什么可以缓存body，如何关联的key呢？这个就要注意下editor，editor是cache通过edit key生成的，所以就关联了啊，并且CacheRequest的cacheOut是editor生成的，而CacheRequest的body是cacheOut生成的，那不就body缓存和key有关了。

Any way，通过Entry和这里的CacheRequest，算是对response的put缓存完成了。

### get方法
看完了put方法，我们继续看get方法，看下缓存到底是怎么取的。
```
@Nullable Response get(Request request) {
    String key = key(request.url());
    // 上面提过的snapshot，快照
    DiskLruCache.Snapshot snapshot;
    Entry entry;
    try {
      // 这里有另一个get方法用来获得快照
      snapshot = cache.get(key);
      if (snapshot == null) {
        return null;
      }
    } catch (IOException e) {
      // Give up because the cache cannot be read.
      return null;
    }

    try {
      // 通过快照获得entry，是Entry的一个方法
      entry = new Entry(snapshot.getSource(ENTRY_METADATA));
    } catch (IOException e) {
      Util.closeQuietly(snapshot);
      return null;
    }

    // 通过Entry构建一个response，也是Entry的方法
    Response response = entry.response(snapshot);

    if (!entry.matches(request, response)) {
      Util.closeQuietly(response.body());
      return null;
    }

    return response;
  }
```
代码看起来很熟悉，毕竟除了get(key)，其他的都在put方法的介绍里面露头了，虽然都没有深入讲解，下面就来一一看下这些内容。先看Snapshot。

#### DiskLruCache.Snapshot
> A snapshot of the values for an entry.

看这个类自己的介绍，对entry的一个快照，这个类代码不多，大致看下: 
```
public final class Snapshot implements Closeable {
    // ...
    // 构造方法就是对这些值赋值到私有变量，忽略这些代码
    Snapshot(String key, long sequenceNumber, Source[] sources, long[] lengths) { ... }
    public String key() { return key; }

    // 垃圾机翻: 返回此快照条目的编辑器，如果自创建此快照以来条目已更改或正在进行其他编辑，则返回 null。 
    public @Nullable Editor edit() throws IOException {
      return DiskLruCache.this.edit(key, sequenceNumber);
    }

    // 取index的值
    public Source getSource(int index) { return sources[index]; }
    public long getLength(int index) { return lengths[index]; }

    public void close() {
      for (Source in : sources) {
        Util.closeQuietly(in);
      }
    }
  }
```
基本就是一个数据类，唯一重要的是这个editor方法，会根据key和sequenceNumber得到一个editor，点进这个方法发现其实和上面put方法获得editor是一样的，这里暂时不讲，继续看。

#### get(key)方法
上面讲到了Snapshot基本就类似一个数据类，那看下这类的实例是怎么来的:
```
  public synchronized Snapshot get(String key) throws IOException {
    initialize();

    checkNotClosed();
    // 验证key，不符合抛出IllegalArgumentException
    validateKey(key);
    // 注意这个Entry是DiskLruCache的Entry，不是Cache.Entry
    // 原来在DiskLruCache里面还有一个lruEntries集合用来保存信息，现在是从这集合里面取出
    Entry entry = lruEntries.get(key);
    if (entry == null || !entry.readable) return null;

    // snapshot保存在DiskLruCache.Entry内部
    Snapshot snapshot = entry.snapshot();
    if (snapshot == null) return null;

    // 没看懂，忽略
    redundantOpCount++;
    journalWriter.writeUtf8(READ).writeByte(' ').writeUtf8(key).writeByte('\n');
    if (journalRebuildRequired()) {
      executor.execute(cleanupRunnable);
    }

    return snapshot;
  }
```
原来这里的snapshot保存在DiskLruCache的Entry内部，而且DiskLruCache还维持了一个lruEntries集合:
```
  final LinkedHashMap<String, Entry> lruEntries = new LinkedHashMap<>(0, 0.75f, true);
```
LinkedHashMap就不用多说了吧！不过这里还只是知道了snapshot从哪里取得的，但不知道源头，大概还是需要后面花时间研究下DiskLruCache才知道。

#### Entry(Source in)
在上面讲put方法时忽略了Entry通过Source创建的构造方法，代码比较长，大致功能就是读取并解析header部分，格式如下:
```
    /**
     *   http://google.com/foo
     *   GET
     *   2
     *   Accept-Language: fr-CA
     *   Accept-Charset: UTF-8
     *   HTTP/1.1 200 OK
     *   3
     *   Content-Type: image/png
     *   Content-Length: 100
     *   Cache-Control: max-age=600
     */
    /** 
     *   https://google.com/foo
     *   GET
     *   2
     *   Accept-Language: fr-CA
     *   Accept-Charset: UTF-8
     *   HTTP/1.1 200 OK
     *   3
     *   Content-Type: image/png
     *   Content-Length: 100
     *   Cache-Control: max-age=600
     *
     *   AES_256_WITH_MD5
     *   2
     *   base64-encoded peerCertificate[0]
     *   base64-encoded peerCertificate[1]
     *   -1
     *   TLSv1.2
     */
```

#### entry.response方法
Entry(Source in)是从snapshot中读取了header信息，那entry.response方法就是用这些信息以及snapshot，重新构建一个response出来。
```
public Response response(DiskLruCache.Snapshot snapshot) {
      String contentType = responseHeaders.get("Content-Type");
      String contentLength = responseHeaders.get("Content-Length");
      Request cacheRequest = new Request.Builder()
          .url(url)
          .method(requestMethod, null)
          .headers(varyHeaders)
          .build();
      return new Response.Builder()
          .request(cacheRequest)
          .protocol(protocol)
          .code(code)
          .message(message)
          .headers(responseHeaders)
          .body(new CacheResponseBody(snapshot, contentType, contentLength))
          .handshake(handshake)
          .sentRequestAtMillis(sentRequestMillis)
          .receivedResponseAtMillis(receivedResponseMillis)
          .build();
    }
```
可以看到除了body，其他信息都是entry的成员变量，而body是通过snapshot创建的，下面来看CacheResponseBody。

#### CacheResponseBody
CacheResponseBody和CacheRequestImpl以及Entry一样，同为Cache的内部类，类不是很长，放出来分析下:
```
private static class CacheResponseBody extends ResponseBody {
    final DiskLruCache.Snapshot snapshot;
    private final BufferedSource bodySource;
    private final @Nullable String contentType;
    private final @Nullable String contentLength;

    // 构造方法
    CacheResponseBody(final DiskLruCache.Snapshot snapshot,
        String contentType, String contentLength) {
      this.snapshot = snapshot;
      this.contentType = contentType;
      this.contentLength = contentLength;

      // 通过snapshot得到一个body的source，这个就是实际缓存的body!
      Source source = snapshot.getSource(ENTRY_BODY);
      bodySource = Okio.buffer(new ForwardingSource(source) {
        @Override public void close() throws IOException {
          // 关掉body流的时候把snapshot也关了
          snapshot.close();
          super.close();
        }
      });
    }

    @Override public MediaType contentType() {
      return contentType != null ? MediaType.parse(contentType) : null;
    }

    @Override public long contentLength() {
      try {
        return contentLength != null ? Long.parseLong(contentLength) : -1;
      } catch (NumberFormatException e) {
        return -1;
      }
    }

    // 这个方法和上面两都是ResponseBody的抽象方法，这里实际就把缓存body的输入流提供出去了
    @Override public BufferedSource source() {
      return bodySource;
    }
  }
```
大致看一下，好像没什么东西，就是继承了ResponseBody，加了四个成员变量，重写了三个方法，在构造方法里通过snapshot拿到了body缓存，并在source方法里向外公开。

至此，get方法的response就算拿到了，entry.matches(...)方法get里面有讲到，东西不多，所以get方法也就讲的差不多了，虽然我也还有很多不理解的地方，毕竟还有个DiskLruCache没讲到嘛，let's go on!

### remove方法
这里的remove方法就比较简单了，实际就是调用DiskLruCache去移除对应key的内容。
```
  void remove(Request request) throws IOException {
    cache.remove(key(request.url()));
  }
```

### update方法
get和put方法都讲了很多，这里的update方法也好理解了，都是上面讲过的东西，逻辑也好理解，可以看下注释:
```
  void update(Response cached, Response network) {
    // 新Response(network)的Entry
    Entry entry = new Entry(network);
    
    // 从旧的CacheResponseBody里面拿到旧的snapshot
    DiskLruCache.Snapshot snapshot = ((CacheResponseBody) cached.body()).snapshot;
    DiskLruCache.Editor editor = null;
    
    try {
      // 这个是snapshot的edit方法，内部会调用DiskLruCache的edit(key, sequenceNumber)方法
      editor = snapshot.edit(); // Returns null if snapshot is not current.
      if (editor != null) {
      
        // 写入新的entry到editor
        entry.writeTo(editor);
        editor.commit();
      }
    } catch (IOException e) {
      abortQuietly(editor);
    }
  }
```

## 小结
又写得好长了，这篇文章拖了好久好久，断断续续写了好几次，总的来说前面有点啰嗦，中间有点敷衍，后面才算有点状态，之前的文章都是先学完再写的，这个算是边看边记录，可能会没这么流畅，但是记录知识点、记录思维的连续性还是可以的。

总的来说，算是把我想要写的知识写错来了，后面加油，继续把DiskLruCache解析了，顺便把Cache的其他方法讲清，最后连贯起来，理解就会简单了。