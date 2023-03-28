package com.silencefly96.module_third.funnet.connection

import okhttp3.Connection
import okhttp3.internal.Util
import java.io.IOException
import java.net.Socket

/**
 * 流分配器
 *
 * 主要功能：
 * This class coordinates the relationship between three entities:
 * Connections、Streams、Calls
 * 1，在RetryAndFollowUpInterceptor中创建，在ConnectInterceptor通过newStream创建httpCodec
 * 2，在newStream中获得Connection，路由选择，发起socket连接，生成httpCodec
 */
class StreamAllocation {

    /**
     * 通过有效的Connection创建Codec
     */
    fun newStream(): HttpCodec {
//        val connectTimeout: Int = chain.connectTimeoutMillis()
//        val readTimeout: Int = chain.readTimeoutMillis()
//        val writeTimeout: Int = chain.writeTimeoutMillis()
//        val pingIntervalMillis: Int = client.pingIntervalMillis()
//        val connectionRetryEnabled: Boolean = client.retryOnConnectionFailure()
//
//        try {
        // 第一步，通过 findHealthyConnection() 获取可用的Connection

//            val resultConnection: RealConnection =
//                findHealthyConnection(connectTimeout, readTimeout, writeTimeout, pingIntervalMillis,
//                    connectionRetryEnabled, doExtensiveHealthChecks)
        // 第六步，根据获得的Connection(已连接server)创建Codec
//            val resultCodec = resultConnection.newCodec(client, chain, this)
//            synchronized(connectionPool) {
//                codec = resultCodec
//                return resultCodec
//            }
//        } catch (e: IOException) {
//            throw RouteException(e)
//        }
        return HttpCodec()
    }

    private fun findHealthyConnection() {
        // 第二步，不停通过findConnection拿到RealConnection，并进行检查，直到可用
//        while (true) {
//            val candidate: RealConnection = findConnection(
//                connectTimeout, readTimeout, writeTimeout,
//                pingIntervalMillis, connectionRetryEnabled
//            )
//
//            // If this is a brand new connection, we can skip the extensive health checks.
//            synchronized(connectionPool) {
//                if (candidate.successCount == 0) {
//                    return candidate
//                }
//            }
//
//            // Do a (potentially slow) check to confirm that the pooled connection is still good. If it
//            // isn't, take it out of the pool and start again.
//            if (!candidate.isHealthy(doExtensiveHealthChecks)) {
//                noNewStreams()
//                continue
//            }
    }

    fun findConnection(): RealConnection {
        // 第三步，获取Connection，对旧的Connection验证，或从connectionPool里面获取新的
//        var foundPooledConnection = false
//        var result: okhttp3.internal.connection.RealConnection? = null
//        var selectedRoute: okhttp3.Route? = null
//        var releasedConnection: Connection?
//        var toClose: Socket?
//        synchronized(connectionPool) {
//            check(!released) { "released" }
//            check(codec == null) { "codec != null" }
//            if (canceled) throw IOException("Canceled")
//
//            // Attempt to use an already-allocated connection. We need to be careful here because our
//            // already-allocated connection may have been restricted from creating new streams.
//            releasedConnection = this.connection
//            toClose = releaseIfNoNewStreams()
//            if (this.connection != null) {
//                // We had an already-allocated connection and it's good.
//                result = this.connection
//                releasedConnection = null
//            }
//            if (!reportedAcquired) {
//                // If the connection was never reported acquired, don't report it as released!
//                releasedConnection = null
//            }
//            if (result == null) {
//                // Attempt to get a connection from the pool.
//                Internal.instance.get(connectionPool, address, this, null)
//                if (connection != null) {
//                    foundPooledConnection = true
//                    result = connection
//                } else {
//                    selectedRoute = route
//                }
//            }
//        }
//        Util.closeQuietly(toClose)
//
//        if (releasedConnection != null) {
//            eventListener.connectionReleased(call, releasedConnection)
//        }
//        if (foundPooledConnection) {
//            eventListener.connectionAcquired(call, result)
//        }
//        if (result != null) {
//            // If we found an already-allocated or pooled connection, we're done.
//            return result
//        }


        // 第四步，如果有路由选择(指代理发送数据路径的选择: DNS返回多个IP)，找到对应的connection，没有router则创建
//        // If we need a route selection, make one. This is a blocking operation.
//        var newRouteSelection = false
//        if (selectedRoute == null && (routeSelection == null || !routeSelection.hasNext())) {
//            newRouteSelection = true
//            routeSelection = routeSelector.next()
//        }
//
//        synchronized(connectionPool) {
//            if (canceled) throw IOException("Canceled")
//            if (newRouteSelection) {
//                // Now that we have a set of IP addresses, make another attempt at getting a connection from
//                // the pool. This could match due to connection coalescing.
//                val routes: List<okhttp3.Route> = routeSelection.getAll()
//                var i = 0
//                val size = routes.size
//                while (i < size) {
//                    val route = routes[i]
//                    Internal.instance.get(connectionPool, address, this, route)
//                    if (connection != null) {
//                        foundPooledConnection = true
//                        result = connection
//                        route = route
//                        break
//                    }
//                    i++
//                }
//            }
//            if (!foundPooledConnection) {
//                if (selectedRoute == null) {
//                    selectedRoute = routeSelection.next()
//                }
//
//                // Create a connection and assign it to this allocation immediately. This makes it possible
//                // for an asynchronous cancel() to interrupt the handshake we're about to do.
//                route = selectedRoute
//                refusedStreamCount = 0
//                result = okhttp3.internal.connection.RealConnection(connectionPool, selectedRoute)
//                acquire(result, false)
//            }
//        }
//
//        // If we found a pooled connection on the 2nd time around, we're done.
//        if (foundPooledConnection) {
//            eventListener.connectionAcquired(call, result)
//            return result
//        }

        // 第五步，使用connection发起socket连接，保存到connectionPool，如果连接地址相同进行复用
//        // Do TCP + TLS handshakes. This is a blocking operation.
//        result!!.connect(
//            connectTimeout, readTimeout, writeTimeout, pingIntervalMillis,
//            connectionRetryEnabled, call, eventListener
//        )
//        routeDatabase().connected(result!!.route())
//
//        var socket: Socket? = null
//        synchronized(connectionPool) {
//            reportedAcquired = true
//
//            // Pool the connection.
//            Internal.instance.put(connectionPool, result)
//
//            // If another multiplexed connection to the same address was created concurrently, then
//            // release this connection and acquire that one.
//            if (result!!.isMultiplexed) {
//                socket = Internal.instance.deduplicate(connectionPool, address, this)
//                result = connection
//            }
//        }
//        Util.closeQuietly(socket)
//
//        eventListener.connectionAcquired(call, result)
        return RealConnection()
    }

    /**
     * 禁止再创建Codec:
     * prevents the connection from being used for new streams in the future.
     * Use this after a Connection: close header, or when the connection may be inconsistent.
     */
    fun noNewStreams() {
//        var socket: Socket?
//        var releasedConnection: Connection?
//        synchronized(connectionPool) {
//            releasedConnection = connection
//            socket = deallocate(true, false, false)
//            if (connection != null) releasedConnection = null
//        }
//        Util.closeQuietly(socket)
//        if (releasedConnection != null) {
//            eventListener.connectionReleased(call, releasedConnection)
//        }
    }

    fun release() {}

    fun streamFailed(e: IOException?) {}

    fun route(): Route {
        return Route()
    }

    fun codec(): HttpCodec? {
        return HttpCodec()
    }
}

class Route() {}