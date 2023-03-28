package com.silencefly96.module_third.funnet.interceptor

import com.silencefly96.module_third.funnet.FunNetClient
import com.silencefly96.module_third.funnet.Request
import com.silencefly96.module_third.funnet.Response
import com.silencefly96.module_third.funnet.connection.ConnectionShutdownException
import com.silencefly96.module_third.funnet.connection.Route
import com.silencefly96.module_third.funnet.connection.RouteException
import com.silencefly96.module_third.funnet.connection.StreamAllocation
import java.io.IOException

/**
 * 重试拦截器
 *
 * 主要功能：
 * 1，创建StreamAllocation
 * 2，循环发送请求，重试、重定向等，直到获得期待 结果或者达到放弃条件
 */
class RetryAndFollowUpInterceptor(
    val client: FunNetClient
): Interceptor {

    @Volatile
    var isCanceled = false

    var streamAllocation: StreamAllocation? = null

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        // 第一步，创建streamAllocation对象
        val realChain = chain as RealInterceptorChain
        var request: Request = chain.request
        // 为了学习，假装下这里创建了
        var streamAllocation = StreamAllocation()
        this.streamAllocation = streamAllocation

        // 第二步，在循环中执行，可多次发送请求，直至获得结果
        // var followUpCount = 0
        var priorResponse: Response? = null     //
        while (true) {
            if (isCanceled) {
                streamAllocation.release()
                throw IOException("Canceled")
            }

            // 第三步，获取结果，失败时进行重试
            var response: Response
            var releaseConnection = true
            try {
                response = realChain.proceed(request,
                    streamAllocation, null, null)
                releaseConnection = false
            } catch (e: RouteException) {
                // The attempt to connect via a route failed. The request will not have been sent.
                // 连接时的失败情况，请求还未发送，选择是否掩盖异常进行重试，不掩盖则为false，跳出循环
                if (!recover(e.lastException!!, streamAllocation, false, request)) {
                    throw e.firstException!!
                }
                releaseConnection = false
                continue
            } catch (e: IOException) {
                // An attempt to communicate with a server failed. The request may have been sent.
                // 和服务器通信失败，请求可能已发送
                val requestSendStarted = e !is ConnectionShutdownException
                if (!recover(e, streamAllocation, requestSendStarted, request)) throw e
                releaseConnection = false
                continue
            } finally {
                // We're throwing an unchecked exception. Release any resources.
                if (releaseConnection) {
                    streamAllocation.streamFailed(null)
                    streamAllocation.release()
                }
            }

            // Attach the prior response if it exists. Such responses never have a body.
            // 上一个循环已经获得了回复，根据上一个回复信息创建response，储存上一个回复
            if (priorResponse != null) {
                response = Response().apply { this.priorResponse = priorResponse }
            }

            // 第四步，根据返回码判断是否需要再发起请求(验证、重定向、重试等)，不需要重新请求则返回response
            var followUp: Request?
            try {
                followUp = followUpRequest(response, streamAllocation.route())
            } catch (e: IOException) {
                streamAllocation.release()
                throw e
            }

            if (followUp == null) {
                streamAllocation.release()
                return response
            }

            // 关闭流
//            closeQuietly(response.body())

            // 超出重试次数
//            if (++followUpCount > RetryAndFollowUpInterceptor.MAX_FOLLOW_UPS) {
//                streamAllocation.release()
//                throw ProtocolException("Too many follow-up requests: $followUpCount")
//            }

            // 不可抵达
//            if (followUp.body() is UnrepeatableRequestBody) {
//                streamAllocation.release()
//                throw HttpRetryException("Cannot retry streamed HTTP body", response.code())
//            }

            // 第五步，判断是否是相同链接(重定向了)，不同链接则重新创建streamAllocation
            if (!sameConnection(response, followUp)) {
                streamAllocation.release()
                streamAllocation = StreamAllocation()
                this.streamAllocation = streamAllocation
            } else check(streamAllocation.codec() == null) {
                ("Closing the body of " + response
                        + " didn't close its backing stream. Bad interceptor?")
            }

            // 下一个循环
            request = followUp
            priorResponse = response
        }
    }

    fun cancel() {

    }

    fun recover(e: IOException, streamAllocation: StreamAllocation, requestSendStarted: Boolean,
                userRequest: Request): Boolean {
        streamAllocation.streamFailed(e)

        // 根据client的设置，返回是否重试，不详写
        // 。。。

        return true
    }

    @Throws(IOException::class)
    fun followUpRequest(userResponse: Response, route: Route?): Request? {
        return null
    }

    fun sameConnection(response: Response, request: Request): Boolean {
        return false
    }
}