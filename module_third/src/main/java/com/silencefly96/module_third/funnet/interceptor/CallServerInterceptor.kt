package com.silencefly96.module_third.funnet.interceptor

import com.silencefly96.module_third.funnet.Response

/**
 * 服务器请求拦截器
 *
 * 主要功能：
 * This is the last interceptor in the chain. It makes a network call to the server
 * 1,使用httpCodec对request和response处理，写入header，处理body
 * 2，通过streamAllocation发起请求
 */
class CallServerInterceptor: Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // 第一步，获取httpCodec、streamAllocation、connection等，下面进行最后的请求
//        val realChain = chain as RealInterceptorChain
//        val httpCodec = realChain.httpStream()
//        val streamAllocation = realChain.streamAllocation()
//        val connection = realChain.connection() as RealConnection?
//        val request = realChain.request()

//        val sentRequestMillis = System.currentTimeMillis()


        // 第二步，使用httpCodec对request进行处理，对header进行写入(UTF8)
        // 通过httpCodec写入请求头
//        realChain.eventListener().requestHeadersStart(realChain.call())
//        httpCodec.writeRequestHeaders(request)
//        realChain.eventListener().requestHeadersEnd(realChain.call(), request)

        // http 100-continue用于客户端在发送POST数据给服务器前，征询服务器情况，看服务器是否处理POST的数据，
        // 如果不处理，客户端则不上传POST数据，如果处理，则POST上传数据。在现实应用中，通过在POST大数据时，
        // 才会使用100-continue协议

        // 当前request需要请求体，即post请求等方式, 如果有，则进行封装
//        var responseBuilder: Response.Builder? = null
//        if (HttpMethod.permitsRequestBody(request.method()) && request.body() != null) {
//            // If there's a "Expect: 100-continue" header on the request, wait for a "HTTP/1.1 100
//            // Continue" response before transmitting the request body. If we don't get that, return
//            // what we did get (such as a 4xx response) without ever transmitting the request body.
//            if ("100-continue".equals(request.header("Expect"), ignoreCase = true)) {
//                httpCodec.flushRequest()
//                realChain.eventListener().responseHeadersStart(realChain.call())
//                responseBuilder = httpCodec.readResponseHeaders(true)
//            }

              // 100-continue时，向服务器发送 requestBody
//            if (responseBuilder == null) {
//                // Write the request body if the "Expect: 100-continue" expectation was met.
//                realChain.eventListener().requestBodyStart(realChain.call())
//                val contentLength = request.body()!!.contentLength()
//                val requestBodyOut = CallServerInterceptor.CountingSink(
//                    httpCodec.createRequestBody(
//                        request,
//                        contentLength
//                    )
//                )
//                val bufferedRequestBody = Okio.buffer(requestBodyOut)
//                request.body()!!.writeTo(bufferedRequestBody)
//                bufferedRequestBody.close()
//                realChain.eventListener()
//                    .requestBodyEnd(realChain.call(), requestBodyOut.successfulCount)
//            } else if (!connection!!.isMultiplexed) {
//                // If the "Expect: 100-continue" expectation wasn't met, prevent the HTTP/1 connection
//                // from being reused. Otherwise we're still obligated to transmit the request body to
//                // leave the connection in a consistent state.
//                streamAllocation.noNewStreams()
//            }
//        }
//
//        httpCodec.finishRequest()


        // 第二步，发起实际请求，通过streamAllocation实现
//        if (responseBuilder == null) {
//            realChain.eventListener().responseHeadersStart(realChain.call())
//            responseBuilder = httpCodec.readResponseHeaders(false)
//        }
//
//        var response: Response = responseBuilder
//            .request(request)
//            .handshake(streamAllocation.connection().handshake())
//            .sentRequestAtMillis(sentRequestMillis)
//            .receivedResponseAtMillis(System.currentTimeMillis())
//            .build()

        // 第三步，对response进行处理
        // 对http 100-continue结果处理，再读取一次
//        var code = response.code()
//        if (code == 100) {
//            // server sent a 100-continue even though we did not request one.
//            // try again to read the actual response
//            responseBuilder = httpCodec.readResponseHeaders(false)
//            response = responseBuilder
//                .request(request)
//                .handshake(streamAllocation.connection().handshake())
//                .sentRequestAtMillis(sentRequestMillis)
//                .receivedResponseAtMillis(System.currentTimeMillis())
//                .build()
//            code = response.code()
//        }
//
//        realChain.eventListener()
//            .responseHeadersEnd(realChain.call(), response)
//

        // 通过httpCodec处理response的body
//        response = if (forWebSocket && code == 101) {
//            // Connection is upgrading, but we need to ensure interceptors see a non-null response body.
//            response.newBuilder()
//                .body(Util.EMPTY_RESPONSE)
//                .build()
//        } else {
//            response.newBuilder()
//                .body(httpCodec.openResponseBody(response))
//                .build()
//        }
//
//        if ("close".equals(response.request().header("Connection"), ignoreCase = true)
//            || "close".equals(response.header("Connection"), ignoreCase = true)
//        ) {
//            streamAllocation.noNewStreams()
//        }
//
//        if ((code == 204 || code == 205) && response.body()!!.contentLength() > 0) {
//            throw ProtocolException(
//                "HTTP " + code + " had non-zero Content-Length: " + response.body()!!
//                    .contentLength()
//            )
//        }


        return Response()
    }
}