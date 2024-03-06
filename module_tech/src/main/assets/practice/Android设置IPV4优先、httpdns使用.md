# Android设置IPV4优先、httpdns使用
## 前言
最近接了个比较奇怪的BUG，就是服务器开了IPV6之后，部分安卓手机会访问不了，或者访问时间特别久，查了下是DNS会返回多个IP，但是IPV6地址会放在前面，比如:
```
[ms.bdstatic.com/240e:95d:801:2::6fb1:624, ms.bdstatic.com/119.96.52.36]
```
然后取域名的时候默认会取第一个IP，然后就蛋疼了，有的机型、系统、运行商、路由器都可能不支持IPV6，然后访问不了。 由于iOS是没问题的，剩下来的肯定是Android的问题了。

于是我花了些时间看了看，做了个IPV4优先方案(还没用到生产环境)，测试了下可行性，顺便又学了下httpdns的使用，这里记录下。

## 核心思路
网上找了资料，解决办法都是通过okhttp的自定义DNS去处理的(可以用Interceptor，不推荐)，这个也是解决办法的核心:
```
class MyDns : Dns {
    @Throws(UnknownHostException::class)
    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            val inetAddressList: MutableList<InetAddress> = ArrayList()
            val inetAddresses = InetAddress.getAllByName(hostname)
            Log.d("TAG", "lookup before: ${Arrays.toString(inetAddresses)}")
            for (inetAddress in inetAddresses) {
                
                // 将IPV4地址放到最前面
                if (inetAddress is Inet4Address) {
                    inetAddressList.add(0, inetAddress)
                } else {
                    inetAddressList.add(inetAddress)
                }
            }
            Log.d("TAG", "lookup after: $inetAddressList")
            inetAddressList
        } catch (var4: NullPointerException) {
            val unknownHostException = UnknownHostException("Broken system behavior")
            unknownHostException.initCause(var4)
            throw unknownHostException
        }
    }
}
```
上面自定义了一个DNS，里面的lookup就是okhttp查找DNS的逻辑，前面我okhttp源码的文章也有说到，默认会取第一个inetAddress，下面看下如何使用:
```
val client = OkHttpClient.Builder()
    .dns(DnsInterceptor.MyDns())
    .build()
    
// 异步请求下百度    
client.newCall(Request.Builder().url(originalUrl).build()).enqueue(
    object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.d("TAG", "onFailure: ")
        }

        override fun onResponse(call: Call, response: Response) {
            Log.d("TAG", "onResponse: $response")
        }
    }
)
```
看下log，第一个是我WiFi访问的，不支持IPV6，第二个是我用iPhone开热点访问的，支持IPV6:
![dd.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/880f644a8daa47649918a27781ba8b60~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1920&h=388&s=66986&e=png&b=2b2b2b)

![cc.png](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/d34b9cb54ecf4bb986e38a0998ea8d10~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1920&h=427&s=74726&e=png&b=2c2c2c)

ps. Android手机可以设置使用IPV6:
> 华为手机: 设置->移动网络->移动数据->接入点名称(APN)->新建一个APN,配置中的APN协议及APN漫游协议设置为仅ipv4或ipv6.

## WebView内使用
okhttp好办，可是我们APP是套壳webView的，Android请求不多，大部分还是HttpURLConnection的，HttpURLConnection找了资料也不太好改，还不如改逻辑换成okhttp，但是webView就没得办法了。

好在API-21后，WebViewClient提供了新的shouldInterceptRequest方法，可以让我们代理它的请求操作，不过有很多限制操作。

### shouldInterceptRequest方法
先来看下shouldInterceptRequest方法，它要求API大于等于21:
```
binding.webView.webViewClient = object : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest
    ): WebResourceResponse? {
        // ...
    }
}
```
方法会提供一个request携带一些请求信息，要求我们返回一个WebResourceResponse，将代理的请求结果封装进去。鸡肋的就是这两个类东西都不多，会限制我们的代理功能:
![dd.png](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/35f45f9b051547b495cfdf633762b224~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=659&h=247&s=20343&e=png&b=3c3f41)

![image.png](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/1d23dfb5acb8426cab42591abf546673~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1132&h=465&s=61061&e=png&b=2b2b2b)

### 功能封装
这里我把代理功能封装了一下，可能还有问题，请谨慎参考:
```
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import okhttp3.Dns
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.Arrays

object DnsInterceptor {

    /**
     * 设置okhttpClient
     */
    lateinit var client: OkHttpClient

    /**
     * 拦截webView请求
     */
    fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest
    ): WebResourceResponse? {
        // WebResourceRequest Android6.0以上才支持header，不支持body所以只能拦截GET方法
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            && request.method.lowercase() == "get"
            && (request.url.scheme?.lowercase() == "http"
                    || request.url.scheme?.lowercase() == "https")) {

            //  获取头部
            val headersBuilder = Headers.Builder()
            request.requestHeaders.entries.forEach {
                headersBuilder.add(it.key, it.value)
            }
            val headers = headersBuilder.build()

            // 生成okhttp请求
            val newRequest =  Request.Builder()
                .url(request.url.toString())
                .headers(headers)
                .build()

            // 同步请求
            val response = client.newCall(newRequest).execute()

            // 对于无mime类型的请求不拦截
            val contentType = response.body()?.contentType()
            if (TextUtils.isEmpty(contentType.toString())) {
                return null
            }

            // 获取响应头
            val responseHeaders: MutableMap<String, String> = HashMap()
            val length = response.headers().size()
            for (i in 0 until length) {
                val name = response.headers().name(i)
                val value =  response.headers().get(name)
                if (null != value) {
                    responseHeaders[name] = value
                }
            }

            // 创建新的response
            return WebResourceResponse(
                "${contentType!!.type()}/${contentType.subtype()}",
                contentType.charset(Charset.defaultCharset())?.name(),
                response.code(),
                "OK",
                responseHeaders,
                response.body()?.byteStream()
            )
        } else {
            return null
        }
    }

    /**
     * 优先使用ipv4
     */
    class MyDns : Dns {
        @Throws(UnknownHostException::class)
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                val inetAddressList: MutableList<InetAddress> = ArrayList()
                val inetAddresses = InetAddress.getAllByName(hostname)
                Log.d("TAG", "lookup before: ${Arrays.toString(inetAddresses)}")
                for (inetAddress in inetAddresses) {
                    if (inetAddress is Inet4Address) {
                        inetAddressList.add(0, inetAddress)
                    } else {
                        inetAddressList.add(inetAddress)
                    }
                }
                Log.d("TAG", "lookup after: $inetAddressList")
                inetAddressList
            } catch (var4: NullPointerException) {
                val unknownHostException = UnknownHostException("Broken system behavior")
                unknownHostException.initCause(var4)
                throw unknownHostException
            }
        }
    }
}
```
把大部分操作封装到一个单例类去了，然后在webView使用的时候就可以这样写:
```
// 创建okhttp
val client = OkHttpClient.Builder().dns(DnsInterceptor.MyDns()).build()
DnsInterceptor.client = client

// 配置webView
val webSettings = binding.webView.settings
webSettings.javaScriptEnabled = true //启用js,不然空白
webSettings.domStorageEnabled = true //getItem报错解决
binding.webView.webViewClient = object : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest
    ): WebResourceResponse? {
        try {
            // 通过okhttp拦截请求
            val response = DnsInterceptor.shouldInterceptRequest(view, request)
            if (response != null) {
                return response
            }
        }catch (e: Exception) {
            // 可能有异常，发生异常就不拦截: UnknownHostException(MyDns)
            e.printStackTrace()
        }
        return super.shouldInterceptRequest(view, request)
    }
}

binding.button.setOnClickListener {
    binding.webView.loadUrl(binding.ip.text.toString())
}
```
试了下，访问百度没啥问题

### 存在问题
上面方法虽然代理webView去发请求了，不过这里有好多限制:
1. 需要API21以上，大部分机型应该满足
2. 只能让GET请求优先使用IPV4，其他请求方法改不了
3. 不支持MIME类型为空的响应
4. 不支持contentType中，无法获取到编码的非二进制文件请求
5. 不支持重定向

网上文章比较少，有几篇我看还都差不多，最后一对比，竟然是阿里云httpdns里面的说明，这里我也不太详叙了，看下文章吧:

[Android端HTTPDNS+Webview最佳实践](https://help.aliyun.com/document_detail/435263.html)

## HTTPDNS使用
上面修改DNS顺序的操作，实际和HTTPDNS的思路是一样的，看到相关内容后，触发了我知识的盲区，觉得还是有必要去学一学的。

HTTPDNS的作用就是代替本地的DNS解析，通过http请求访问httpdns的服务商，先拿到IP，再发起请求，可以防劫持，并且更快，当然这都是我简单的理解，可以看下阿里对它产品的介绍:
> https://help.aliyun.com/document_detail/435219.html

### 阿里HTTPDNS
这里我是选的阿里的httpdns服务，开通方式还是看他们自己的说明吧，不是很复杂: [服务开通](https://help.aliyun.com/document_detail/435229.html)

下面就来看如何使用，首先是添加依赖:
```
// setting.gradle.kts中
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven{ url = uri("./catalog_repo") }
        maven {
            url = uri("http://maven.aliyun.com/nexus/content/repositories/releases/")
            name = "aliyun"
            //一定要添加这个配置
            isAllowInsecureProtocol = true
        }
    }
}

// 要使用的module中
implementation 'com.aliyun.ams:alicloud-android-httpdns:2.3.2'
```
这里是kts的依赖，groovy语法类似，gradle7.0以下甚至加个url就行。

再来看下具体使用，我在阿里云的后台配置了百度的域名(”www.baidu.com“)，这里就来请求百度的IP:
```
val httpdns = HttpDns.getService(getContext(), "xxx")
// 预加载
httpdns.setPreResolveHosts(ArrayList(listOf("www.baidu.com")))

val originalUrl = "http://www.baidu.com"
val url = URL(originalUrl)
val ip = httpdns.getIpByHostAsync(url.host)
Log.d("TAG", "httpdns get init: ip = $ip")
```
这样使用我这直接就失败了，拿到的ip为null，所以初始化的操作应该要提前一点做:
```
// 点击事件
binding.button.setOnClickListener {
    val ipClick = httpdns.getIpByHostAsync(url.host)
    val ipv6 = httpdns.getIPv6sByHostAsync(url.host).let {
        if (it.isNotEmpty()) return@let it[0]
        else return@let "not get"
    }
    Log.d("TAG", "httpdns get: ip = $ipClick, ipv6 = $ipv6")
}
```
后面我把获取操作放到点击事件里面，就没问题了，也能拿到IPV6地址:
![dd.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/83a88ef7d8774254961dd7a4db3e2195~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1204&h=343&s=68682&e=png&b=2d2d2d)

这里要注意下，如果切换网络，IPV6的地址会有缓存，谨慎使用吧(网络可能不支持了):
![dd.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/afbd382ed62e4c379f4dc91036d092cf~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1920&h=608&s=121133&e=png&b=2b2b2b)

httpdns的使用应该算网络优化了吧，看别人文章说dns查找域名有的要几百毫秒，用httpdns可能只要一百毫秒，有机会来研究研究源码^_^

## 小结
稍微总结下吧，这篇文章分析了一下IPV6在Android上出错的原因，实践了下IPV4优先的思路，并且对webView做了支持，还研究了下httpdns的使用。