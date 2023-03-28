package com.silencefly96.module_third.funnet.interceptor

import com.silencefly96.module_third.funnet.Cache
import com.silencefly96.module_third.funnet.Response

/**
 * 缓存拦截器
 *
 * 主要功能：
 * Serves requests from the cache and writes responses to the cache
 * 1，根据缓存策略取缓存、发起请求、保存缓存
 */
class CacheInterceptor(val cache: Cache?): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // 第一步，获得请求的缓存，得到缓存策略(需要的网络请求、本地缓存的回复)，并进行验证
//        val cacheCandidate: Response = cache?.get(chain.request())
//
//        val now = System.currentTimeMillis()
//
//        val strategy = CacheStrategy.Factory(now, chain.request(), cacheCandidate).get()
//        val networkRequest = strategy.networkRequest
//        val cacheResponse = strategy.cacheResponse
//
//        cache?.trackResponse(strategy)
//
        // 没有本地缓存，可以关闭缓存了
//        if (cacheCandidate != null && cacheResponse == null) {
//            Util.closeQuietly(cacheCandidate.body()) // The cache candidate wasn't applicable. Close it.
//        }

        // 没有网络请求，同时没有缓存，无法访问504
        // If we're forbidden from using the network and the cache is insufficient, fail.
//        if (networkRequest == null && cacheResponse == null) {
//            return Response.Builder()
//                .request(chain.request())
//                .protocol(Protocol.HTTP_1_1)
//                .code(504)
//                .message("Unsatisfiable Request (only-if-cached)")
//                .body(Util.EMPTY_RESPONSE)
//                .sentRequestAtMillis(-1L)
//                .receivedResponseAtMillis(System.currentTimeMillis())
//                .build()
//        }

        // 不需要网络请求，从缓存返回
        // If we don't need the network, we're done.
//        if (networkRequest == null) {
//            return cacheResponse!!.newBuilder()
//                .cacheResponse(CacheInterceptor.stripBody(cacheResponse))
//                .build()
//        }


        // 第二步，根据需要的网络请求，通过责任链下一步发起请求，获得网络回复
//        var networkResponse: Response? = null
//        try {
//            networkResponse = chain.proceed(networkRequest)
//        } finally {
//            // If we're crashing on I/O or otherwise, don't leak the cache body.
//            if (networkResponse == null && cacheCandidate != null) {
//                Util.closeQuietly(cacheCandidate.body())
//            }
//        }

        // 获取网络回复后，还有本地缓存的回复，根据条件处理(未修改: 更新header、缓存)
        // If we have a cache response too, then we're doing a conditional get.
//        if (cacheResponse != null) {
//            if (networkResponse.code() == HttpURLConnection.HTTP_NOT_MODIFIED) {
//                val response: Response = cacheResponse.newBuilder()
//                    .headers(
//                        CacheInterceptor.combine(
//                            cacheResponse.headers(),
//                            networkResponse.headers()
//                        )
//                    )
//                    .sentRequestAtMillis(networkResponse.sentRequestAtMillis())
//                    .receivedResponseAtMillis(networkResponse.receivedResponseAtMillis())
//                    .cacheResponse(CacheInterceptor.stripBody(cacheResponse))
//                    .networkResponse(CacheInterceptor.stripBody(networkResponse))
//                    .build()
//                networkResponse.body().close()
//
//                // Update the cache after combining headers but before stripping the
//                // Content-Encoding header (as performed by initContentStream()).
//                cache.trackConditionalCacheHit()
//                cache.update(cacheResponse, response)
//                return response
//            } else {
//                Util.closeQuietly(cacheResponse.body())
//            }
//        }


        // 第三步，通过网络回复及缓存回复创建实际的response(上面只针对NOT_MODIFIED情况)，并缓存
//        val response: Response = networkResponse.newBuilder()
//            .cacheResponse(CacheInterceptor.stripBody(cacheResponse))
//            .networkResponse(CacheInterceptor.stripBody(networkResponse))
//            .build()
//
//        if (cache != null) {
//            if (HttpHeaders.hasBody(response) && CacheStrategy.isCacheable(
//                    response,
//                    networkRequest
//                )
//            ) {
//                // Offer this request to the cache.
//                val cacheRequest: CacheRequest = cache.put(response)
//                return cacheWritingResponse(cacheRequest, response)
//            }
//            if (HttpMethod.invalidatesCache(networkRequest.method())) {
//                try {
//                    cache.remove(networkRequest)
//                } catch (ignored: IOException) {
//                    // The cache cannot be written.
//                }
//            }
//        }

        return Response()
    }
}