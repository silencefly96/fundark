# volley源码解析
## 前言
最近看完okhttp3的源码，想起之前看过《Android开发进阶：从小工到专家》这本书，里面根据volley写入一个简单的网络请求框架，当时对于泛型都不会用的我来说，真的挺震撼的。又开发了几年，感觉可以利用这个机会学习下这个库，还能趁okhttp3的知识还没忘记，正好对比下，看看都有哪些优缺点。

生活总要给自己找一些事做，不然浑浑噩噩，什么都不想干，恍恍惚惚一天又一天的，白白就过去了。

## 整体结构
首先贴上volley官网的说明，英文的，不过也能看了: 
> [官网说明链接](https://google.github.io/volley/)

再一个看一个库的源码，最好了解下源码的整体结构，网上虽然有很多解析volley的文章，大多都是用的官网的这张图:
![官网架构图](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/807bb5872b0d4d37af2294e493dfba6f~tplv-k3u1fbpfcp-watermark.image?)

但是我觉得这图还不如《Android开发进阶：从小工到专家》里面的结构清晰，于是我拿书里的图改了改，希望能让读者理解起来轻松点:

![jiagou.png](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/78089ae4d5214856b1453b89d112b7ad~tplv-k3u1fbpfcp-watermark.image?)

图改的不怎么样，大致就是这样一个流程吧。

## 基本使用
和okhttp一样，这里先看下简单使用，再一步一步最终实现原理.
```
        // 1，创建RequestQueue，默认使用BasicNetwork、HurlStack
        val requestQueue = Volley.newRequestQueue(context)
        val url = "https://www.baidu.com"

        // 2，构造一个 request（请求）
        val request = StringRequest(Request.Method.GET, url, {
            Log.d("TAG", "useVolley result: $it")
        }, {
            Log.d("TAG", "useVolley error: ${it.message}")
        })

        // 3，把request添加进请求队列RequestQueue里面
        requestQueue.add(request)
```
好了，记住这个使用，接下来我们就进入源码分析环节。

## 源码分析
### 创建RequestQueue
首先来看第一步，这里通过Volley类创建一个RequestQueue对象，点开Volley类，可以发现三个创建RequestQueue的方法，一个废弃了，我们看下剩下的两个方法:
```
public static RequestQueue newRequestQueue(Context context)
public static RequestQueue newRequestQueue(Context context, BaseHttpStack stack)
```
忽略SDK_INT < 9的情况，代码可以简化到下面形式:
```
    public static RequestQueue newRequestQueue(Context context, BaseHttpStack stack) {
        BasicNetwork network;
        if (stack == null) {
            // 默认情况
            network = new BasicNetwork(new HurlStack());
        } else {
            network = new BasicNetwork(stack);
        }
        return newRequestQueue(context, network);
    }
    
    private static RequestQueue newRequestQueue(Context context, Network network) {
        final Context appContext = context.getApplicationContext();
        // Use a lazy supplier for the cache directory so that newRequestQueue() can be called on
        // main thread without causing strict mode violation.
        DiskBasedCache.FileSupplier cacheSupplier =
                new DiskBasedCache.FileSupplier() {
                    private File cacheDir = null;

                    @Override
                    public File get() {
                        // 使用到时才创建文件
                        if (cacheDir == null) {
                            cacheDir = new File(appContext.getCacheDir(), DEFAULT_CACHE_DIR);
                        }
                        return cacheDir;
                    }
                };
        // 创建queue，并启动
        RequestQueue queue = new RequestQueue(new DiskBasedCache(cacheSupplier), network);
        queue.start();
        return queue;
    }
```
代码很清晰，就是创建HttpStack、Network以及RequestQueue，HttpStack和Network默认实现是HurlStack及BasicNetwork。

下面我们先简单看下HttpStack、Network、RequestQueue三个类。

#### HttpStack
HttpStack是volley中实际发起请求的接口，输出HttpResponse(body还在流中)，具体实现类是HurlStack，它基于HttpURLConnection来网络交互。

由于HttpClient被安卓废弃了，而原有接口HttpStack用到了，所以volley后面使用BaseHttpStack代替了它，并用executeRequest代替了原有的performRequest方法。
```
public abstract class BaseHttpStack implements HttpStack {
    // 实际使用方法
    public abstract HttpResponse executeRequest(
            Request<?> request, Map<String, String> additionalHeaders)
            throws IOException, AuthFailureError;
            
    @Deprecated
    @Override
    public final org.apache.http.HttpResponse performRequest(
            Request<?> request, Map<String, String> additionalHeaders)
            throws IOException, AuthFailureError {...}
}
```

BaseHttpStack有两个实现，AsyncHttpStack和HurlStack，忽略AdaptedHttpStack(适配HttpClient的)，这个后面使用到时再详解。

#### Network
Network是volley中的网络层，具体实现类是BasicNetwork，输出NetworkResponse，与BaseHttpStack不同的是会读取body流到byte数组，并会对header进行些处理。核心代码如下:
```
    @Override
    public NetworkResponse performRequest(Request<?> request) throws VolleyError {
        long requestStart = SystemClock.elapsedRealtime();
        // 注意这个循环，重试、重定向可能用到
        while (true) {
            HttpResponse httpResponse = null;
            byte[] responseContents = null;
            List<Header> responseHeaders = Collections.emptyList();
            try {
                // 额外的header信息
                Map<String, String> additionalRequestHeaders = ...
                httpResponse = mBaseHttpStack.executeRequest(request, additionalRequestHeaders);
                
                // 状态码及返回的header
                int statusCode = httpResponse.getStatusCode();
                responseHeaders = httpResponse.getHeaders();
                
                // 更新缓存header
                if (statusCode == HttpURLConnection.HTTP_NOT_MODIFIED) {...}

                // 处理body
                InputStream inputStream = httpResponse.getContent();
                if (inputStream != null) {...} else {...}

                // 异常状态码
                if (statusCode < 200 || statusCode > 299) {throw new IOException();}
                
                return new NetworkResponse(...);
            } catch (IOException e) {
                // 重试，下一个循环
                RetryInfo retryInfo = ...
                NetworkUtility.attemptRetryOnException(request, retryInfo);
            }
        }
    }
```
Network还有另一个继承类AsyncNetwork，而BasicAsyncNetwork又继承了AsyncNetwork，这个我们后面也会讲解，先了解下。

#### RequestQueue
RequestQueue与其说它是一个请求队列，倒不如说它是Request的一个manager，它负责管理request，并整合了CacheDispatcher和NetworkDispatcher。
```
public void start()
public void stop()
public void cancelAll(RequestFilter filter)
public void cancelAll(final Object tag)
public <T> Request<T> add(Request<T> request)
```
RequestQueue并不是一个queue，但是它里面有两个优先级阻塞队列: mCacheQueue和mNetworkQueue，RequestQueue中start方法启动后，CacheDispatcher和NetworkDispatcher就会从这两个阻塞队列中取出request去执行。
```
    // 在Volley的newRequestQueue方法中调用
    public void start() {
        stop(); // Make sure any currently running dispatchers are stopped.
        
        // Create the cache dispatcher and start it.
        mCacheDispatcher = new CacheDispatcher(mCacheQueue, mNetworkQueue, mCache, mDelivery);
        mCacheDispatcher.start();

        // Create network dispatchers (and corresponding threads) up to the pool size.
        for (int i = 0; i < mDispatchers.length; i++) {
            NetworkDispatcher networkDispatcher =
                    new NetworkDispatcher(mNetworkQueue, mNetwork, mCache, mDelivery);
            mDispatchers[i] = networkDispatcher;
            networkDispatcher.start();
        }
    }
```
关于CacheDispatcher和NetworkDispatcher的内容后面详解，这里知道他们是处理缓存和网络的线程就行，就是要注意下CacheDispatcher只有一个，而NetworkDispatcher有默认四个，并且并未用到线程池。
```
    public <T> Request<T> add(Request<T> request) {
        // ...
        synchronized (mCurrentRequests) {
            // 便于统一操作request
            mCurrentRequests.add(request);
        }

        // ...
        beginRequest(request);
        return request;
    }
    
    <T> void beginRequest(Request<T> request) {
        // If the request is uncacheable, skip the cache queue and go straight to the network.
        if (!request.shouldCache()) {
            sendRequestOverNetwork(request);
        } else {
            mCacheQueue.add(request);
        }
    }
```
调用RequestQueue的add方法传入request，就会将request放入mCacheQueue或者mNetworkQueue，由是request的mShouldCache决定。

## 创建Request
RequestQueue的使用需要一个request对象，Request是一个抽象类，需要继承它并重写parseNetworkResponse和deliverResponse方法:
```
public class StringRequest extends Request<String> {
    // 将response对象以何种方法传递给用户代码
    @Override
    protected void deliverResponse(String response) {
        Response.Listener<String> listener;
        synchronized (mLock) {
            listener = mListener;
        }
        if (listener != null) {
            listener.onResponse(response);
        }
    }

    // 将数据解析成具体的数据类
    @Override
    protected Response<String> parseNetworkResponse(NetworkResponse response) {
        String parsed;
        try {
            parsed = new String(response.data, HttpHeaderParser.parseCharset(response.headers));
        } catch (UnsupportedEncodingException e) {
            parsed = new String(response.data);
        }
        return Response.success(parsed, HttpHeaderParser.parseCacheHeaders(response));
    }
}
```
Request类并不只是一个数据类，它还持有了很多引用，如RequestQueue、Cache.Entry、NetworkRequestCompleteListener等，还听提供了一些对request处理的方法:
```
void finish(final String tag) 
public void cancel()
```
经过parseNetworkResponse和deliverResponse方法后，用户代码应该就能拿到response对象了，或者收到一个VolleyError。

## 添加Request任务
其实前面都是开胃菜，为什么RequestQueue中添加(add) request后，我们就能能拿到response了呢？RequestQueue的add方法我们前面已经铁过代码了，下面我们再贴一遍:
```
    public <T> Request<T> add(Request<T> request) {
        // ...
        synchronized (mCurrentRequests) {
            // 便于统一操作request
            mCurrentRequests.add(request);
        }

        // ...
        beginRequest(request);
        return request;
    }
    
    <T> void beginRequest(Request<T> request) {
        // If the request is uncacheable, skip the cache queue and go straight to the network.
        if (!request.shouldCache()) {
            sendRequestOverNetwork(request);
        } else {
            mCacheQueue.add(request);
        }
    }
    
    <T> void sendRequestOverNetwork(Request<T> request) {
        mNetworkQueue.add(request);
    }
```
其实只是将request加到了mCacheQueue和mNetworkQueue中，那为什么request会被执行并拿到response呢？实际原理是在RequestQueue的start方法上
```
    // 在Volley的newRequestQueue方法中调用
    public void start() {
        stop(); // Make sure any currently running dispatchers are stopped.
        
        // Create the cache dispatcher and start it.
        mCacheDispatcher = new CacheDispatcher(mCacheQueue, mNetworkQueue, mCache, mDelivery);
        mCacheDispatcher.start();

        // Create network dispatchers (and corresponding threads) up to the pool size.
        for (int i = 0; i < mDispatchers.length; i++) {
            NetworkDispatcher networkDispatcher =
                    new NetworkDispatcher(mNetworkQueue, mNetwork, mCache, mDelivery);
            mDispatchers[i] = networkDispatcher;
            networkDispatcher.start();
        }
    }
```
Volley的newRequestQueue方法启动了RequestQueue，还记得说CacheDispatcher和NetworkDispatcher是线程吗？是的，他们启动后就在死循环，等request添加到mCacheQueue和mNetworkQueue中，然后供它们take。
```
    private static RequestQueue newRequestQueue(Context context, Network network) {
        // ...
        RequestQueue queue = new RequestQueue(new DiskBasedCache(cacheSupplier), network);
        queue.start();
        return queue;
    }
```

## 网络流程
下面就根据NetworkDispatcher来讲解下网络请求的流程。

### 概述
NetworkDispatcher比较简单，它会从mNetworkQueue中取出request，再通过mNetwork拿到networkResponse，随后通过request.parseNetworkResponse拿到具体的response，然后由mDelivery传递出去。
```
    private final BlockingQueue<Request<?>> mQueue;
    
    // 构造方法
    public NetworkDispatcher(
            BlockingQueue<Request<?>> queue,
            Network network,
            Cache cache,
            ResponseDelivery delivery) {
        mQueue = queue;
        mNetwork = network;
        mCache = cache;
        mDelivery = delivery;
    }
```

### 线程run
```
    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        while (true) {
            try {
                processRequest();
            } catch (InterruptedException e) {
                // We may have been interrupted because it was time to quit.
                if (mQuit) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
```
虽然NetworkDispatcher比较简单，但是主要学东西嘛，这里有个线程的interrupt有意思，可以看下。这个mQuit设置来源于quit方法:
```
    public void quit() {
        mQuit = true;
        interrupt();
    }
    
    // RequestQueue
    public void stop() {
        if (mCacheDispatcher != null) {
            mCacheDispatcher.quit();
        }
        for (final NetworkDispatcher mDispatcher : mDispatchers) {
            if (mDispatcher != null) {
                mDispatcher.quit();
            }
        }
    }
```
RequestQueue调用stop方法，NetworkDispatcher被interrupt，线程会停止，但是request被取消并不会影响线程，也不会interrupt:
```
// NetworkDispatcher的processRequest方法中
if (request.isCanceled()) {
    request.finish("network-discard-cancelled");
    request.notifyListenerResponseNotUsable();
    return;
}
```

### 核心方法processRequest分析
```
    private void processRequest() throws InterruptedException {
        // Take a request from the queue. 四个NetworkDispatcher轮流拿取request
        Request<?> request = mQueue.take();
        processRequest(request);
    }
    
    void processRequest(Request<?> request) {
        long startTimeMs = SystemClock.elapsedRealtime();
        request.sendEvent(RequestQueue.RequestEvent.REQUEST_NETWORK_DISPATCH_STARTED);
        try {
            request.addMarker("network-queue-take");

            // If the request was cancelled already, do not perform the
            // network request.
            if (request.isCanceled()) {
                request.finish("network-discard-cancelled");
                request.notifyListenerResponseNotUsable();
                return;
            }

            addTrafficStatsTag(request);

            // Perform the network request. 实际发起请求
            NetworkResponse networkResponse = mNetwork.performRequest(request);
            request.addMarker("network-http-complete");

            // If the server returned 304 AND we delivered a response already,
            // we're done -- don't deliver a second identical response.
            if (networkResponse.notModified && request.hasHadResponseDelivered()) {
                request.finish("not-modified");
                request.notifyListenerResponseNotUsable();
                return;
            }

            // Parse the response here on the worker thread. 将networkResponse转换成具体的Response
            Response<?> response = request.parseNetworkResponse(networkResponse);
            request.addMarker("network-parse-complete");

            // Write to cache if applicable. 更新缓存
            if (request.shouldCache() && response.cacheEntry != null) {
                mCache.put(request.getCacheKey(), response.cacheEntry);
                request.addMarker("network-cache-written");
            }

            // Post the response back.
            request.markDelivered();
            
            // 由mDelivery从线程中转换到主线程，投递response
            mDelivery.postResponse(request, response);
            
            // 网络完成的成功回调
            request.notifyListenerResponseReceived(response);
        } catch (VolleyError volleyError) {
            volleyError.setNetworkTimeMs(SystemClock.elapsedRealtime() - startTimeMs);
            
            // 里面会parseNetworkError，并由mDelivery投递error
            parseAndDeliverNetworkError(request, volleyError);
            // 网络完成的失败回调
            request.notifyListenerResponseNotUsable();
        } catch (Exception e) {
            VolleyLog.e(e, "Unhandled exception %s", e.toString());
            VolleyError volleyError = new VolleyError(e);
            volleyError.setNetworkTimeMs(SystemClock.elapsedRealtime() - startTimeMs);
            
            mDelivery.postError(request, volleyError);
            request.notifyListenerResponseNotUsable();
        } finally {
            request.sendEvent(RequestQueue.RequestEvent.REQUEST_NETWORK_DISPATCH_FINISHED);
        }
    }
```
这里需要注意的是，只要使用了NetworkDispatcher那就是异步请求，而且volley内是四个NetworkDispatcher同时进行的，轮流从mQueue(BlockingQueue<Request<?>>)中拿取(take)request。

一大堆代码下来，其实就两个重点，一是通过mNetwork去获得networkResponse，二是通过mDelivery投递response或者error。

### mNetwork去获得networkResponse
上面讲到了NetworkDispatcher通过mNetwork去获得NetworkResponse，mNetwork的具体实现是BasicNetwork，而BasicNetwork通过mBaseHttpStack去获取httpResponse，会传入request和一些额外的header信息
```
    public NetworkResponse performRequest(Request<?> request) throws VolleyError {
        long requestStart = SystemClock.elapsedRealtime();
        // 注意这个循环，会重试、重定向
        while (true) {
            HttpResponse httpResponse = null;
            byte[] responseContents = null;
            List<Header> responseHeaders = Collections.emptyList();
            try {
                // Gather headers. 额外的header信息: "If-None-Match"、"If-Modified-Since"
                Map<String, String> additionalRequestHeaders =
                        HttpHeaderParser.getCacheHeaders(request.getCacheEntry());
                        
                // mBaseHttpStack通过HttpURLConnection去网络请求
                httpResponse = mBaseHttpStack.executeRequest(request, additionalRequestHeaders);
                int statusCode = httpResponse.getStatusCode();

                // 更新缓存header
                responseHeaders = httpResponse.getHeaders();
                // Handle cache validation.
                if (statusCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    long requestDuration = SystemClock.elapsedRealtime() - requestStart;
                    return NetworkUtility.getNotModifiedNetworkResponse(
                            request, requestDuration, responseHeaders);
                }

                // 读取body到byte数组，直接进内存！！！==> 大文件请求别这么搞。。。
                InputStream inputStream = httpResponse.getContent();
                if (inputStream != null) {
                    responseContents =
                            NetworkUtility.inputStreamToBytes(
                                    inputStream, httpResponse.getContentLength(), mPool);
                } else {
                    // Add 0 byte response as a way of honestly representing a
                    // no-content request.
                    responseContents = new byte[0];
                }

                // if the request is slow, log it.
                long requestLifetime = SystemClock.elapsedRealtime() - requestStart;
                NetworkUtility.logSlowRequests(
                        requestLifetime, request, responseContents, statusCode);

                // 错误状态码
                if (statusCode < 200 || statusCode > 299) {
                    throw new IOException();
                }
                
                return new NetworkResponse(
                        statusCode,
                        responseContents,
                        /* notModified= */ false,
                        SystemClock.elapsedRealtime() - requestStart,
                        responseHeaders);
            } catch (IOException e) {
                // This will either throw an exception, breaking us from the loop, or will loop
                // again and retry the request.
                RetryInfo retryInfo = NetworkUtility.shouldRetryException(
                                request, e, requestStart, httpResponse, responseContents);
                
                // 记录信息，并重试
                // We should already be on a background thread, so we can invoke the retry inline.
                NetworkUtility.attemptRetryOnException(request, retryInfo);
            }
        }
    }
```
这个Network的performRequest上面简单讲过，这里代码完整一些。

这里注意下BasicNetwork拿到httpResponse后，会读取出其中的body(inputStream)，并保存到byte[]数组中，并转成NetworkResponse，body的inputStream被封装为UrlConnectionInputStream，读取完成后会关闭UrlConnection。

### mBaseHttpStack去获得httpResponse
接下来BaseHttpStack通过executeRequest方法获得httpResponse，实现类是HurlStack，代码如下:
```
    @Override
    public HttpResponse executeRequest(Request<?> request, Map<String, String> additionalHeaders)
            throws IOException, AuthFailureError {
        // 请求链接
        String url = request.getUrl();
        
        // 处理全部header
        HashMap<String, String> map = new HashMap<>();
        map.putAll(additionalHeaders);
        // Request.getHeaders() takes precedence over the given additional (cache) headers).
        map.putAll(request.getHeaders());
        
        // mUrlRewriter要自行设置，可忽略
        if (mUrlRewriter != null) {
            String rewritten = mUrlRewriter.rewriteUrl(url);
            if (rewritten == null) {
                throw new IOException("URL blocked by rewriter: " + url);
            }
            url = rewritten;
        }
        
        // 使用HttpURLConnection请求
        URL parsedUrl = new URL(url);
        HttpURLConnection connection = openConnection(parsedUrl, request);
        boolean keepConnectionOpen = false;
        try {
            for (String headerName : map.keySet()) {
                connection.setRequestProperty(headerName, map.get(headerName));
            }
            
            // 设置HttpURLConnection的请求方法，GET、POST等
            setConnectionParametersForRequest(connection, request);
            
            // Initialize HttpResponse with data from the HttpURLConnection.
            int responseCode = connection.getResponseCode();
            if (responseCode == -1) {
                // -1 is returned by getResponseCode() if the response code could not be retrieved.
                // Signal to the caller that something was wrong with the connection.
                throw new IOException("Could not retrieve response code from HttpUrlConnection.");
            }
            
            // 请求没有body
            if (!hasResponseBody(request.getMethod(), responseCode)) {
                return new HttpResponse(responseCode, convertHeaders(connection.getHeaderFields()));
            }

            // Need to keep the connection open until the stream is consumed by the caller. Wrap the
            // stream such that close() will disconnect the connection.
            keepConnectionOpen = true;
            return new HttpResponse(
                    responseCode,
                    
                    // 处理Headers
                    convertHeaders(connection.getHeaderFields()),
                    connection.getContentLength(),
                    
                    // 把body封装成UrlConnectionInputStream
                    createInputStream(request, connection));
        } finally {
            if (!keepConnectionOpen) {
                connection.disconnect();
            }
        }
    }
```
这里代码也不复杂，就是要看下body的封装:
```
    static class UrlConnectionInputStream extends FilterInputStream {
        private final HttpURLConnection mConnection;

        UrlConnectionInputStream(HttpURLConnection connection) {
            super(inputStreamFromConnection(connection));
            mConnection = connection;
        }

        @Override
        public void close() throws IOException {
            super.close();
            mConnection.disconnect();
        }
    }
```
body被封装成了UrlConnectionInputStream，这里当body的InputStream被close的时候，mConnection也会被关闭连接。

### mDelivery投递response
NetworkDispatcher最终通过mDelivery传递response，mDelivery是一个ExecutorDelivery对象，内部有一个Executor来执行Runnable，默认在主线程handler执行:
```
    // RequestQueue构造函数中创建
    public RequestQueue(Cache cache, Network network, int threadPoolSize) {
        this(
                cache,
                network,
                threadPoolSize,
                 // 传入主线程Looper
                new ExecutorDelivery(new Handler(Looper.getMainLooper())));
    }
    
    // 构造方法
    public ExecutorDelivery(final Handler handler) {
        // Make an Executor that just wraps the handler.
        mResponsePoster =
                new Executor() {
                    @Override
                    public void execute(Runnable command) {
                        handler.post(command);
                    }
                };
    }
```
ExecutorDelivery中实际就是对mRequest做一些标记，最后通过Request的deliverResponse方法，将Response.result传递过去了，实际就是交给了Request里面的listener，通过回调将response交出去
```
    @Override
    public void postResponse(Request<?> request, Response<?> response, Runnable runnable) {
        request.markDelivered();
        request.addMarker("post-response");
        mResponsePoster.execute(new ResponseDeliveryRunnable(request, response, runnable));
    }
```
下面是ResponseDeliveryRunnable的run方法:
```
        @Override
        public void run() {
            // If this request has canceled, finish it and don't deliver.
            if (mRequest.isCanceled()) {
                mRequest.finish("canceled-at-delivery");
                return;
            }

            // Deliver a normal response or error, depending.
            if (mResponse.isSuccess()) {
                mRequest.deliverResponse(mResponse.result);
            } else {
                mRequest.deliverError(mResponse.error);
            }

            // If this is an intermediate response, add a marker, otherwise we're done
            // and the request can be finished.
            if (mResponse.intermediate) {
                mRequest.addMarker("intermediate-response");
            } else {
                mRequest.finish("done");
            }
            
            // 传入的额外的runnable
            // If we have been provided a post-delivery runnable, run it.
            if (mRunnable != null) {
                mRunnable.run();
            }
        }
```
至于如何到达用户代码，前面Request中我们已经有说到了，关键就在Request的两个抽象方法: parseNetworkResponse和deliverResponse。

## 缓存流程
### CacheDispatcher取缓存
CacheDispatcher和NetworkDispatcher类似，构造方法内多个WaitingRequestManager: 
```
    public CacheDispatcher(
            BlockingQueue<Request<?>> cacheQueue,
            BlockingQueue<Request<?>> networkQueue,
            Cache cache,
            ResponseDelivery delivery) {
        mCacheQueue = cacheQueue;
        mNetworkQueue = networkQueue;
        mCache = cache;
        mDelivery = delivery;
        mWaitingRequestManager = new WaitingRequestManager(this, networkQueue, delivery);
    }
```
CacheDispatcher也是一个线程，它的run方法中会对mCache进行初始化，剩下的和CacheDispatcher类似:
```
@Override
    public void run() {
        // ...
        // Make a blocking call to initialize the cache.
        mCache.initialize();
        while (true) {
            try {
                processRequest();
            } catch (InterruptedException e) {
                // ...
            }
        }
    }
```
它从mCacheQueue取出request，再从mCache中通过request.getCacheKey取缓存，没缓存还要通过WaitingRequestManager判断下:
```
    private void processRequest() throws InterruptedException {
        final Request<?> request = mCacheQueue.take();
        processRequest(request);
    }
    
    void processRequest(final Request<?> request) throws InterruptedException {
        // ...
        try {
            // If the request has been canceled, don't bother dispatching it.
            if (request.isCanceled()) {
                request.finish("cache-discard-canceled");
                return;
            }

            // Attempt to retrieve this item from cache.
            Cache.Entry entry = mCache.get(request.getCacheKey());
            if (entry == null) {
                request.addMarker("cache-miss");
                
                // 未获取到缓存，则交给网络请求阻塞队列，并结束此processRequest
                if (!mWaitingRequestManager.maybeAddToWaitingRequests(request)) {
                    mNetworkQueue.put(request);
                }
                return;
            }

            // 缓存超时也重新网络请求
            long currentTimeMillis = System.currentTimeMillis();
            if (entry.isExpired(currentTimeMillis)) {
                request.addMarker("cache-hit-expired");
                request.setCacheEntry(entry);
                if (!mWaitingRequestManager.maybeAddToWaitingRequests(request)) {
                    mNetworkQueue.put(request);
                }
                return;
            }

            // 击中缓存
            request.addMarker("cache-hit");
            Response<?> response = request.parseNetworkResponse(
                            new NetworkResponse(entry.data, entry.responseHeaders));
            request.addMarker("cache-hit-parsed");

            // 拿到缓存但是处理失败了
            if (!response.isSuccess()) {
                request.addMarker("cache-parsing-failed");
                mCache.invalidate(request.getCacheKey(), true);
                request.setCacheEntry(null);
                
                // 如果有和这个request相同任务的request正在请求，可以等一等，因为一会儿可以获得最新数据
                if (!mWaitingRequestManager.maybeAddToWaitingRequests(request)) {
                    mNetworkQueue.put(request);
                }
                return;
            }
            
            if (!entry.refreshNeeded(currentTimeMillis)) {
                // 不需要更新，立即返回
                mDelivery.postResponse(request, response);
            } else {
                // 即使击中了缓存，也去请求下，获得最新书
                // Soft-expired cache hit. We can deliver the cached response,
                // but we need to also send the request to the network for
                // refreshing.
                request.addMarker("cache-hit-refresh-needed");
                request.setCacheEntry(entry);
                
                // Mark the response as intermediate. 立即生效
                response.intermediate = true;

                if (!mWaitingRequestManager.maybeAddToWaitingRequests(request)) {
                    // 不在等待队列中，投递response数据后，加到网络请求阻塞队列去请求
                    mDelivery.postResponse(
                            request,
                            response,
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        mNetworkQueue.put(request);
                                    } catch (InterruptedException e) {
                                        // Restore the interrupted status
                                        Thread.currentThread().interrupt();
                                    }
                                }
                            });
                } else {
                    // 等待队列中有相同目的的request
                    mDelivery.postResponse(request, response);
                }
            }
        } finally {
            request.sendEvent(RequestQueue.RequestEvent.REQUEST_CACHE_LOOKUP_FINISHED);
        }
    }
```
注释写的清楚了，这里要特别讲下WaitingRequestManager，它里面有一个mWaitingRequests储存着等待有相同请求任务的request，这些request会等待第一个request(网络请求)的结果，并更新它们所有。不太好理解，着重看下下面代码:
```
if (!mWaitingRequestManager.maybeAddToWaitingRequests(request)) {
    mDelivery.postResponse(request,response,new Runnable() {
        @Override
        public void run() {
            try {
                mNetworkQueue.put(request);
            } catch (InterruptedException e) {
                // Restore the interrupted status
                Thread.currentThread().interrupt();
            }
        }
    });
    // ...    
}
```
这是缓存击中后，去更新缓存的动作，maybeAddToWaitingRequests中返回false的那里，会把这个request加到mWaitingRequests中(第一个):
```
        } else {
            mWaitingRequests.put(cacheKey, null);
            request.setNetworkRequestCompleteListener(this);
            return false;
        }
```
再来看在mWaitingRequests有相同目的的request时，maybeAddToWaitingRequests也会把它加到mWaitingRequests中，但是它就(很有可能)不是第一个了，而是要第一个request更新的东东。
```
// maybeAddToWaitingRequests中
if (mWaitingRequests.containsKey(cacheKey)) {
    // There is already a request in flight. Queue up.
    List<Request<?>> stagedRequests = mWaitingRequests.get(cacheKey);
    if (stagedRequests == null) {
        stagedRequests = new ArrayList<>();
    }
    request.addMarker("waiting-for-response");
    stagedRequests.add(request);
    
    // 加到等待队列
    mWaitingRequests.put(cacheKey, stagedRequests);
    return true;
}
        
if (!mWaitingRequestManager.maybeAddToWaitingRequests(request)) {
    // ...
} else {
    mDelivery.postResponse(request, response);
}
```
至于mWaitingRequests如何被通知的，我们看下下面代码:
```
    // WaitingRequestManager中
    @Override
    public void onResponseReceived(Request<?> request, Response<?> response) {
        // 。。。for循环更新，忽略
    }
    
    // Request中被调用
    void notifyListenerResponseReceived(Response<?> response) {
        NetworkRequestCompleteListener listener;
        synchronized (mLock) {
            listener = mRequestCompleteListener;
        }
        if (listener != null) {
            listener.onResponseReceived(this, response);
        }
    }
```
实际就是Request中的notifyListenerResponseReceived会更新mWaitingRequests中这些request，而Request中的notifyListenerResponseReceived前面有说到，是在NetworkDispatcher的processRequest方法中调用的。

好了，这些代码有点绕，但是看懂它的先后顺序，那就明了了。

### 缓存Cache
DiskBasedCache是负责Volley缓存的实现类，NoCache和ThrowingCache也继承了Cache接口，但是一个是没实现，一个是抛出异常。Cache接口方法如下，里面还有个Entry类就忽略了:
```
public interface Cache {
    @Nullable
    Entry get(String key);
    void put(String key, Entry entry);
    void initialize();
    void invalidate(String key, boolean fullExpire);
    void remove(String key);
    void clear();
}
```
DiskBasedCache缓存通过mRootDirectorySupplier目录下的文件实现: 
```
    // Volley中创建
    @NonNull
    private static RequestQueue newRequestQueue(Context context, Network network) {
        final Context appContext = context.getApplicationContext();
        DiskBasedCache.FileSupplier cacheSupplier =
                new DiskBasedCache.FileSupplier() {
                    private File cacheDir = null;
                    @Override
                    public File get() {
                        if (cacheDir == null) {
                            cacheDir = new File(appContext.getCacheDir(), DEFAULT_CACHE_DIR);
                        }
                        return cacheDir;
                    }
                };
        RequestQueue queue = new RequestQueue(new DiskBasedCache(cacheSupplier), network);
        queue.start();
        return queue;
    }
```
从initialize方法中可以看出，每个文件包含CacheHeader类部分信息、ResponseHeaders以及body数据。
```
    @Override
    public synchronized void initialize() {
        File rootDirectory = mRootDirectorySupplier.get();
        if (!rootDirectory.exists()) {
            if (!rootDirectory.mkdirs()) {
                VolleyLog.e("Unable to create cache dir %s", rootDirectory.getAbsolutePath());
            }
            return;
        }
        File[] files = rootDirectory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                long entrySize = file.length();
                CountingInputStream cis =
                        new CountingInputStream(
                                new BufferedInputStream(createInputStream(file)), entrySize);
                try {
                    // 只读取header信息，CountingInputStream会记录剩余大小，及body的大小
                    CacheHeader entry = CacheHeader.readHeader(cis);
                    entry.size = entrySize;
                    putEntry(entry.key, entry);
                } finally {
                    // Any IOException thrown here is handled by the below catch block by design.
                    //noinspection ThrowFromFinallyBlock
                    cis.close();
                }
            } catch (IOException e) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }
```
DiskBasedCache内通过mEntries(Map<String, CacheHeader>)维持缓存的一些信息，但不包含body数据，但是body会保存在Entry对象里面(data属性)。CacheHeader可以通过toCacheEntry得到Entry对象，需要传入body数据(byte[] data)
```
        // DiskBasedCache属性
        private final Map<String, CacheHeader> mEntries = new LinkedHashMap<>(16, .75f, true);
        
        // Entry的方法
        Entry toCacheEntry(byte[] data) {
            Entry e = new Entry();
            e.data = data;
            e.etag = etag;
            e.serverDate = serverDate;
            e.lastModified = lastModified;
            e.ttl = ttl;
            e.softTtl = softTtl;
            e.responseHeaders = HttpHeaderParser.toHeaderMap(allResponseHeaders);
            e.allResponseHeaders = Collections.unmodifiableList(allResponseHeaders);
            return e;
        }
```
CacheDispatcher中通过CacheKey拿到Cache.Entry，由entry的data(body数据)和responseHeaders即可构建NetworkResponse对象，继而转成Response，拿到缓存
```
    @Override
    public synchronized Entry get(String key) {
        CacheHeader entry = mEntries.get(key);
        // if the entry does not exist, return.
        if (entry == null) {
            return null;
        }
        
        File file = getFileForKey(key);
        try {
            CountingInputStream cis =
                    new CountingInputStream(
                            new BufferedInputStream(createInputStream(file)), file.length());
            try {
                // 读出header数据
                CacheHeader entryOnDisk = CacheHeader.readHeader(cis);
                
                // 文件异常了
                if (!TextUtils.equals(key, entryOnDisk.key)) {
                    // File was shared by two keys and now holds data for a different entry!
                    removeEntry(key);
                    return null;
                }
                
                // 读出body数据
                byte[] data = streamToBytes(cis, cis.bytesRemaining());
                return entry.toCacheEntry(data);
            } finally {
                // 无论任何异常，这里都会关闭
                cis.close();
            }
        } catch (IOException e) {
            remove(key);
            return null;
        }
    }
```
DiskBasedCache缓存时，会根据key创建一个文件，然后按顺序写入CacheHeader的一些信息、header信息，最后写入entry的data数组，即body信息
```
@Override
    public synchronized void put(String key, Entry entry) {
        // 满了
        if (mTotalSize + entry.data.length > mMaxCacheSizeInBytes
                && entry.data.length > mMaxCacheSizeInBytes * HYSTERESIS_FACTOR) {
            return;
        }
        
        File file = getFileForKey(key);
        try {
            BufferedOutputStream fos = new BufferedOutputStream(createOutputStream(file));
            CacheHeader e = new CacheHeader(key, entry);
            
            // 先写入CacheHeader的一些信息、header
            boolean success = e.writeHeader(fos);
            
            if (!success) {
                fos.close();
                throw new IOException();
            }
            
            // 再写入body
            fos.write(entry.data);
            fos.close();
            
            // 加到mEntries中，记录起来
            e.size = file.length();
            putEntry(key, e);
            
            // 判断是否要裁切
            pruneIfNeeded();
        } catch (IOException e) {
            boolean deleted = file.delete();
            initializeIfRootDirectoryDeleted();
        }
    }
```

## AsyncRequestQueue相关
> ps. 感觉AsyncRequestQueue这一套完全不一样，如果只是了解volley原理，看上面内容就行了。
> 不过在我看来，下面的代码等于对上面volley的一个重构，采用线程池和ALL TASK的思想实现，可以学习下，下面写得比较糙，过一下

Volley里面还有个AsyncRequestQueue，用于请求，它不是通过Volley类直接创建，而是要通过Builder创建，需要一个AsyncNetwork，其他自行配置，不设置在build的时候会创建默认的
```
    val asyncNetwork = BasicAsyncNetwork.Builder(object : AsyncHttpStack() {
        override fun executeRequest(
            request: Request<*>?,
            additionalHeaders: MutableMap<String, String>?,
            callback: OnRequestComplete?
        ) {

        }
    }).build()
    val asyncRequestQueue = AsyncRequestQueue.Builder(asyncNetwork).build()
    asyncRequestQueue.add(request)
    asyncRequestQueue.start()
```
AsyncRequestQueue中调用start之后就会开始执行线程任务，与RequestQueue不同的是，AsyncRequestQueue是通过mBlockingExecutor和mNonBlockingExecutor两个线程池去执行任务的
```
@Override
    public void start() {
        stop(); // Make sure any currently running threads are stopped

        // 由mExecutorFactory创建线程池
        mNonBlockingExecutor = mExecutorFactory.createNonBlockingExecutor(getBlockingQueue());
        mBlockingExecutor = mExecutorFactory.createBlockingExecutor(getBlockingQueue());
        mNonBlockingScheduledExecutor = mExecutorFactory.createNonBlockingScheduledExecutor();
        
        // 这两个是执行任务的线程池
        mNetwork.setBlockingExecutor(mBlockingExecutor);
        mNetwork.setNonBlockingExecutor(mNonBlockingExecutor);
        // 这个没用到好像
        mNetwork.setNonBlockingScheduledExecutor(mNonBlockingScheduledExecutor);

        // Kick off cache initialization, which must complete before any requests can be processed.
        if (mAsyncCache != null) {
            mNonBlockingExecutor.execute( () -> {
                mAsyncCache.initialize( 
                    new AsyncCache.OnWriteCompleteCallback() {
                        @Override
                        public void onWriteComplete() {
                            // 这么长就这个方法有用。。。
                            onCacheInitializationComplete();
                        }
                });
            } 
        } else {
            // 没缓存，通过阻塞去执行，mAsyncCache和cache的区别
            mBlockingExecutor.execute( () -> {
                getCache().initialize();
                mNonBlockingExecutor.execute( () -> {
                    onCacheInitializationComplete();
                }
            }
        }
    }
    
    // 等缓存初始化完成后，再启动前面的request
    private void onCacheInitializationComplete() {
        List<Request<?>> requestsToDispatch;
        synchronized (mCacheInitializationLock) {
            requestsToDispatch = new ArrayList<>(mRequestsAwaitingCacheInitialization);
            mRequestsAwaitingCacheInitialization.clear();
            mIsCacheInitialized = true;
        }

        // Kick off any requests that were queued while waiting for cache initialization.
        for (Request<?> request : requestsToDispatch) {
            beginRequest(request);
        }
    }
```
AsyncRequestQueue内两个线程池执行的是CacheTask和NetworkTask，AsyncRequestQueue内定义了很多继承于RequestTask<T>的类，实际就是一个可比较的Runnable，请求的一系列操作都通过各种RequestTask实现，比如缓存、处理缓存、处理response、处理error等
```
private class CacheParseTask<T> extends RequestTask<T>
private class CachePutTask<T> extends RequestTask<T>
private class CacheTask<T> extends RequestTask<T>

private class InvokeRetryPolicyTask<T> extends RequestTask<T>

private class NetworkParseTask<T> extends RequestTask<T>
private class NetworkTask<T> extends RequestTask<T>

private class ParseErrorTask<T> extends RequestTask<T>
private class ResponseParsingTask<T> extends RequestTask<T>
```
AsyncRequestQueue继承了RequestQueue，调用add方法也会触发beginRequest，继而执行任务，如果缓存还没有初始化，还得加到等待列表，需要等start方法启动，start方法启动后等待列表的请求会实际运行
```
    @Override
    <T> void beginRequest(Request<T> request) {
        // If the cache hasn't been initialized yet, add the request to a temporary queue to be
        // flushed once initialization completes.
        if (!mIsCacheInitialized) {
            synchronized (mCacheInitializationLock) {
                if (!mIsCacheInitialized) {
                    // 先保存起来，等待缓存初始化
                    mRequestsAwaitingCacheInitialization.add(request);
                    return;
                }
            }
        }

        // If the request is uncacheable, send it over the network.
        if (request.shouldCache()) {
            if (mAsyncCache != null) {
                mNonBlockingExecutor.execute(new CacheTask<>(request));
            } else {
                // 没缓存，通过阻塞去执行，mAsyncCache和cache的区别
                mBlockingExecutor.execute(new CacheTask<>(request));
            }
        } else {
            // 就是添加了个NetworkTask
            sendRequestOverNetwork(request);
        }
    }
    
    @Override
    <T> void sendRequestOverNetwork(Request<T> request) {
        mNonBlockingExecutor.execute(new NetworkTask<>(request));
    }
```
AsyncRequestQueue中的缓存由CacheTask完成，可以设置一个mAsyncCache来异步处理缓存的增删改查，如果没在builder中提供，则必须提供一个cache，操作就和RequestQueue一样了
```
    private class CacheTask<T> extends RequestTask<T> {
        // ...
        @Override
        public void run() {
            // If the request has been canceled, don't bother dispatching it.
            if (mRequest.isCanceled()) {
                mRequest.finish("cache-discard-canceled");
                return;
            }

            mRequest.addMarker("cache-queue-take");

            // Attempt to retrieve this item from cache.
            if (mAsyncCache != null) {
                mAsyncCache.get(
                        mRequest.getCacheKey(),
                        new OnGetCompleteCallback() {
                            @Override
                            public void onGetComplete(Entry entry) {
                                handleEntry(entry, mRequest);
                            }
                        });
            } else {
                Entry entry = getCache().get(mRequest.getCacheKey());
                handleEntry(entry, mRequest);
            }
        }
    }
    
    // 和上面处理缓存的功能差不多
    private void handleEntry(final Entry entry, final Request<?> mRequest) {
        if (entry == null) {
            mRequest.addMarker("cache-miss");
            // 取缓存失败，直接网络请求
            if (!mWaitingRequestManager.maybeAddToWaitingRequests(mRequest)) {
                sendRequestOverNetwork(mRequest);
            }
            return;
        }

        // 超时也使用网络请求
        long currentTimeMillis = System.currentTimeMillis();
        if (entry.isExpired(currentTimeMillis)) {
            mRequest.addMarker("cache-hit-expired");
            mRequest.setCacheEntry(entry);
            if (!mWaitingRequestManager.maybeAddToWaitingRequests(mRequest)) {
                sendRequestOverNetwork(mRequest);
            }
            return;
        }

        // We have a cache hit; parse its data for delivery back to the request.
        mBlockingExecutor.execute(new CacheParseTask<>(mRequest, entry, currentTimeMillis));
    }
```
AsyncRequestQueue中的网络请求由NetworkTask完成，NetworkTask中由AsyncNetwork的performRequest方法执行
```
    private class NetworkTask<T> extends RequestTask<T> {
        // ...
        @Override
        public void run() {
            // If the request was cancelled already, do not perform the network request.
            if (mRequest.isCanceled()) { // ... }

            final long startTimeMs = SystemClock.elapsedRealtime();
            mRequest.addMarker("network-queue-take");
            
            // Perform the network request.
            mNetwork.performRequest(
                    mRequest,
                    new OnRequestComplete() {
                        @Override
                        public void onSuccess(final NetworkResponse networkResponse) {
                            // ...
                            // 拿到的networkResponse通过mBlockingExecutor执行NetworkParseTask得到，再投递到主线程
                            mBlockingExecutor.execute(
                                    new NetworkParseTask<>(mRequest, networkResponse));
                        }

                        @Override
                        public void onError(final VolleyError volleyError) {
                            // 异常也要通过ParseErrorTask再处理
                            volleyError.setNetworkTimeMs(
                                    SystemClock.elapsedRealtime() - startTimeMs);
                            mBlockingExecutor.execute(new ParseErrorTask<>(mRequest, volleyError));
                        }
                    });
        }
    }
```
AsyncNetwork的实现类是BasicAsyncNetwork，但是AsyncRequestQueue中并未默认提供，需要在其Builder构造方法中传入
```
    // AsyncNetwork需要自己构建并传入
    val asyncRequestQueue = AsyncRequestQueue.Builder(asyncNetwork).build()
```
BasicAsyncNetwork内部持有一个mAsyncStack，网络请求实际是通过AsyncHttpStack完成的，AsyncHttpStack是个抽象类，需要在BasicAsyncNetwork的Builder构造函数传入，结合上面AsyncNetwork也是要用户创建，真就呵呵了，AsyncHttpStack的executeRequest方法要用户自己写
```
    // BasicAsyncNetwork.Builder中要传入一个AsyncHttpStack
    val asyncNetwork = BasicAsyncNetwork.Builder(object : AsyncHttpStack() {
        override fun executeRequest(
            request: Request<*>?,
            additionalHeaders: MutableMap<String, String>?,
            callback: OnRequestComplete?
        ) {
            // 需要自己写如何处理request
            
        }
    }).build()
```
总而言之，AsyncRequestQueue这个没人写教程，网上也没人提，看完源码才知道是干嘛的，不过思路还是可以借鉴借鉴的。

## 总结
和okhttp3相比，volley还是简单了很多，一个是没有okio那样的IO优化，网络部分交给了HttpURLConnection去处理(最复杂的部分)，缓存也就一条请求一个文件，甚至缓存的body直接读到内存中，在异步线程上，volley还只是四个线程，并没有用到线程池进行管理。

当然，虽然volley没有okhttp那么强大，但是如果只是普通请求，还是能cover的，增加了泛型及数据的parse，能够自动线程转换，用起来还是很方便的。

