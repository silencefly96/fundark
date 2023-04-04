package com.silencefly96.module_third.funnet.connection;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.util.List;
import okhttp3.Address;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.ConnectionPool;
import okhttp3.EventListener;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Route;
import okhttp3.internal.Internal;
import okhttp3.internal.Util;
import okhttp3.internal.connection.RealConnection;
import okhttp3.internal.connection.RouteDatabase;
import okhttp3.internal.connection.RouteException;
import okhttp3.internal.connection.RouteSelector;
import okhttp3.internal.connection.StreamAllocation;
import okhttp3.internal.http.HttpCodec;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.StreamResetException;

import static okhttp3.internal.Util.closeQuietly;

/**
 * 流分配器
 *
 * 主要功能：
 * This class coordinates the relationship between three entities:
 * Connections、Streams、Calls
 * 1，在RetryAndFollowUpInterceptor中创建，在ConnectInterceptor通过newStream创建httpCodec
 * 2，在newStream中获得Connection，路由选择，发起socket连接，生成httpCodec(流对象：提供的I/O操作完成网络通信)
 */
public final class StreamAllocation1 {
//    public final Address address;
//    private RouteSelector.Selection routeSelection;
//    private Route route;
//    private final ConnectionPool connectionPool;
//    public final Call call;
//    public final EventListener eventListener;
//    private final Object callStackTrace;
//
//    // State guarded by connectionPool.
//    private final RouteSelector routeSelector;
//    private int refusedStreamCount;
//    private RealConnection connection;
//    private boolean reportedAcquired;
//    private boolean released;
//    private boolean canceled;
//    private HttpCodec codec;
//
//    public StreamAllocation1(ConnectionPool connectionPool, Address address, Call call,
//                             EventListener eventListener, Object callStackTrace) {
//        this.connectionPool = connectionPool;
//        this.address = address;
//        this.call = call;
//        this.eventListener = eventListener;
//        this.routeSelector = new RouteSelector(address, routeDatabase(), call, eventListener);
//        this.callStackTrace = callStackTrace;
//    }
//
//    public HttpCodec newStream(
//            OkHttpClient client, Interceptor.Chain chain, boolean doExtensiveHealthChecks) {
//        int connectTimeout = chain.connectTimeoutMillis();
//        int readTimeout = chain.readTimeoutMillis();
//        int writeTimeout = chain.writeTimeoutMillis();
//        int pingIntervalMillis = client.pingIntervalMillis();
//        boolean connectionRetryEnabled = client.retryOnConnectionFailure();
//
//        try {
//            // 第一步，通过 findHealthyConnection() 获取可用的Connection
//            RealConnection resultConnection = findHealthyConnection(connectTimeout, readTimeout,
//                    writeTimeout, pingIntervalMillis, connectionRetryEnabled, doExtensiveHealthChecks);
//
//            // 第六步，根据获得的Connection(已连接server)创建Codec
//            HttpCodec resultCodec = resultConnection.newCodec(client, chain, this);
//
//            synchronized (connectionPool) {
//                codec = resultCodec;
//                return resultCodec;
//            }
//        } catch (IOException e) {
//            throw new RouteException(e);
//        }
//    }
//
//    /**
//     * Finds a connection and returns it if it is healthy. If it is unhealthy the process is repeated
//     * until a healthy connection is found.
//     */
//    private RealConnection findHealthyConnection(int connectTimeout, int readTimeout,
//                                                  int writeTimeout, int pingIntervalMillis, boolean connectionRetryEnabled,
//                                                  boolean doExtensiveHealthChecks) throws IOException {
//        while (true) {
//            // 第二步，不停通过findConnection拿到RealConnection，并进行检查，直到可用
//            RealConnection candidate = findConnection(connectTimeout, readTimeout, writeTimeout,
//                    pingIntervalMillis, connectionRetryEnabled);
//
//            // If this is a brand new connection, we can skip the extensive health checks.
//            synchronized (connectionPool) {
//                if (candidate.successCount == 0) {
//                    return candidate;
//                }
//            }
//
//            // Do a (potentially slow) check to confirm that the pooled connection is still good. If it
//            // isn't, take it out of the pool and start again.
//            if (!candidate.isHealthy(doExtensiveHealthChecks)) {
//                noNewStreams();
//                continue;
//            }
//
//            return candidate;
//        }
//    }
//
//    /**
//     * Returns a connection to host a new stream. This prefers the existing connection if it exists,
//     * then the pool, finally building a new connection.
//     */
//    private RealConnection findConnection(int connectTimeout, int readTimeout, int writeTimeout,
//                                           int pingIntervalMillis, boolean connectionRetryEnabled) throws IOException {
//        // 第三步，获取Connection，对旧的Connection验证，或从connectionPool里面获取新的
//        boolean foundPooledConnection = false;
//        RealConnection result = null;
//        Route selectedRoute = null;
//        Connection releasedConnection;
//        Socket toClose;
//        synchronized (connectionPool) {
//            if (released) throw new IllegalStateException("released");
//            if (codec != null) throw new IllegalStateException("codec != null");
//            if (canceled) throw new IOException("Canceled");
//
//            // Attempt to use an already-allocated connection. We need to be careful here because our
//            // already-allocated connection may have been restricted from creating new streams.
//            releasedConnection = this.connection;
//            toClose = releaseIfNoNewStreams();
//            if (this.connection != null) {
//                // We had an already-allocated connection and it's good.
//                result = this.connection;
//                releasedConnection = null;
//            }
//            if (!reportedAcquired) {
//                // If the connection was never reported acquired, don't report it as released!
//                releasedConnection = null;
//            }
//
//            if (result == null) {
//                // Attempt to get a connection from the pool.
//                Internal.instance.get(connectionPool, address, this, null);
//                if (connection != null) {
//                    foundPooledConnection = true;
//                    result = connection;
//                } else {
//                    selectedRoute = route;
//                }
//            }
//        }
//        closeQuietly(toClose);
//
//        if (releasedConnection != null) {
//            eventListener.connectionReleased(call, releasedConnection);
//        }
//        if (foundPooledConnection) {
//            eventListener.connectionAcquired(call, result);
//        }
//        if (result != null) {
//            // If we found an already-allocated or pooled connection, we're done.
//            return result;
//        }
//
//        // 第四步，result没有从上面连接池拿到，标记了selectedRout，即需要进行路由选择(先判断有没有)
//        // 如果有路由选择(指代理发送数据路径的选择: DNS返回多个IP)，找到对应的connection，没有router则创建
//        // If we need a route selection, make one. This is a blocking operation.
//        boolean newRouteSelection = false;
//        if (selectedRoute == null && (routeSelection == null || !routeSelection.hasNext())) {
//            newRouteSelection = true;
//            routeSelection = routeSelector.next();
//        }
//
//        synchronized (connectionPool) {
//            if (canceled) throw new IOException("Canceled");
//
//            // 如果需要创建路由选择，则进行创建
//            if (newRouteSelection) {
//                // Now that we have a set of IP addresses, make another attempt at getting a connection from
//                // the pool. This could match due to connection coalescing.
//                List<Route> routes = routeSelection.getAll();
//                for (int i = 0, size = routes.size(); i < size; i++) {
//                    Route route = routes.get(i);
//
//                    // Internal的实例是OkHttpClient，里面会从connectionPool取出connection
//                    // 最终调用this的acquire方法修改this的connection和route
//                    Internal.instance.get(connectionPool, address, this, route);
//                    if (connection != null) {
//                        foundPooledConnection = true;
//                        result = connection;
//                        this.route = route;
//                        break;
//                    }
//                }
//            }
//
//            if (!foundPooledConnection) {
//                if (selectedRoute == null) {
//                    selectedRoute = routeSelection.next();
//                }
//
//                // Create a connection and assign it to this allocation immediately. This makes it possible
//                // for an asynchronous cancel() to interrupt the handshake we're about to do.
//                route = selectedRoute;
//                refusedStreamCount = 0;
//                result = new RealConnection(connectionPool, selectedRoute);
//                acquire(result, false);
//            }
//        }
//
//        // If we found a pooled connection on the 2nd time around, we're done.
//        if (foundPooledConnection) {
//            eventListener.connectionAcquired(call, result);
//            return result;
//        }
//
//        // 第五步，使用connection发起socket连接，保存到connectionPool，如果连接地址相同进行复用
//        // Do TCP + TLS handshakes. This is a blocking operation.
//        result.connect(connectTimeout, readTimeout, writeTimeout, pingIntervalMillis,
//                connectionRetryEnabled, call, eventListener);
//        routeDatabase().connected(result.route());
//
//        Socket socket = null;
//        synchronized (connectionPool) {
//            reportedAcquired = true;
//
//            // Pool the connection.
//            Internal.instance.put(connectionPool, result);
//
//            // If another multiplexed connection to the same address was created concurrently, then
//            // release this connection and acquire that one.
//            if (result.isMultiplexed()) {
//                socket = Internal.instance.deduplicate(connectionPool, address, this);
//                result = connection;
//            }
//        }
//        closeQuietly(socket);
//
//        eventListener.connectionAcquired(call, result);
//        return result;
//    }
//
//    /**
//     * Releases the currently held connection and returns a socket to close if the held connection
//     * restricts new streams from being created. With HTTP/2 multiple requests share the same
//     * connection so it's possible that our connection is restricted from creating new streams during
//     * a follow-up request.
//     */
//    private Socket releaseIfNoNewStreams() {
//        assert (Thread.holdsLock(connectionPool));
//        RealConnection allocatedConnection = this.connection;
//        if (allocatedConnection != null && allocatedConnection.noNewStreams) {
//            return deallocate(false, false, true);
//        }
//        return null;
//    }
//
//    public void streamFinished(boolean noNewStreams, HttpCodec codec, long bytesRead, IOException e) {
//        eventListener.responseBodyEnd(call, bytesRead);
//
//        Socket socket;
//        Connection releasedConnection;
//        boolean callEnd;
//        synchronized (connectionPool) {
//            if (codec == null || codec != this.codec) {
//                throw new IllegalStateException("expected " + this.codec + " but was " + codec);
//            }
//            if (!noNewStreams) {
//                connection.successCount++;
//            }
//            releasedConnection = connection;
//            socket = deallocate(noNewStreams, false, true);
//            if (connection != null) releasedConnection = null;
//            callEnd = this.released;
//        }
//        closeQuietly(socket);
//        if (releasedConnection != null) {
//            eventListener.connectionReleased(call, releasedConnection);
//        }
//
//        if (e != null) {
//            e = Internal.instance.timeoutExit(call, e);
//            eventListener.callFailed(call, e);
//        } else if (callEnd) {
//            Internal.instance.timeoutExit(call, null);
//            eventListener.callEnd(call);
//        }
//    }
//
//    public HttpCodec codec() {
//        synchronized (connectionPool) {
//            return codec;
//        }
//    }
//
//    private RouteDatabase routeDatabase() {
//        return Internal.instance.routeDatabase(connectionPool);
//    }
//
//    public Route route() {
//        return route;
//    }
//
//    public synchronized RealConnection connection() {
//        return connection;
//    }
//
//    public void release() {
//        Socket socket;
//        Connection releasedConnection;
//        synchronized (connectionPool) {
//            releasedConnection = connection;
//            socket = deallocate(false, true, false);
//            if (connection != null) releasedConnection = null;
//        }
//        closeQuietly(socket);
//        if (releasedConnection != null) {
//            Internal.instance.timeoutExit(call, null);
//            eventListener.connectionReleased(call, releasedConnection);
//            eventListener.callEnd(call);
//        }
//    }
//
//    /** Forbid new streams from being created on the connection that hosts this allocation. */
//    public void noNewStreams() {
//        Socket socket;
//        Connection releasedConnection;
//        synchronized (connectionPool) {
//            releasedConnection = connection;
//            socket = deallocate(true, false, false);
//            if (connection != null) releasedConnection = null;
//        }
//        closeQuietly(socket);
//        if (releasedConnection != null) {
//            eventListener.connectionReleased(call, releasedConnection);
//        }
//    }
//
//    /**
//     * Releases resources held by this allocation. If sufficient resources are allocated, the
//     * connection will be detached or closed. Callers must be synchronized on the connection pool.
//     *
//     * <p>Returns a closeable that the caller should pass to {@link Util#closeQuietly} upon completion
//     * of the synchronized block. (We don't do I/O while synchronized on the connection pool.)
//     */
//    private Socket deallocate(boolean noNewStreams, boolean released, boolean streamFinished) {
//        assert (Thread.holdsLock(connectionPool));
//
//        if (streamFinished) {
//            this.codec = null;
//        }
//        if (released) {
//            this.released = true;
//        }
//        Socket socket = null;
//        if (connection != null) {
//            if (noNewStreams) {
//                connection.noNewStreams = true;
//            }
//            if (this.codec == null && (this.released || connection.noNewStreams)) {
//                release(connection);
//                if (connection.allocations.isEmpty()) {
//                    connection.idleAtNanos = System.nanoTime();
//                    if (Internal.instance.connectionBecameIdle(connectionPool, connection)) {
//                        socket = connection.socket();
//                    }
//                }
//                connection = null;
//            }
//        }
//        return socket;
//    }
//
//    public void cancel() {
//        HttpCodec codecToCancel;
//        RealConnection connectionToCancel;
//        synchronized (connectionPool) {
//            canceled = true;
//            codecToCancel = codec;
//            connectionToCancel = connection;
//        }
//        if (codecToCancel != null) {
//            codecToCancel.cancel();
//        } else if (connectionToCancel != null) {
//            connectionToCancel.cancel();
//        }
//    }
//
//    public void streamFailed(IOException e) {
//        Socket socket;
//        Connection releasedConnection;
//        boolean noNewStreams = false;
//
//        synchronized (connectionPool) {
//            if (e instanceof StreamResetException) {
//                ErrorCode errorCode = ((StreamResetException) e).errorCode;
//                if (errorCode == ErrorCode.REFUSED_STREAM) {
//                    // Retry REFUSED_STREAM errors once on the same connection.
//                    refusedStreamCount++;
//                    if (refusedStreamCount > 1) {
//                        noNewStreams = true;
//                        route = null;
//                    }
//                } else if (errorCode != ErrorCode.CANCEL) {
//                    // Keep the connection for CANCEL errors. Everything else wants a fresh connection.
//                    noNewStreams = true;
//                    route = null;
//                }
//            } else if (connection != null
//                    && (!connection.isMultiplexed() || e instanceof ConnectionShutdownException)) {
//                noNewStreams = true;
//
//                // If this route hasn't completed a call, avoid it for new connections.
//                if (connection.successCount == 0) {
//                    if (route != null && e != null) {
//                        routeSelector.connectFailed(route, e);
//                    }
//                    route = null;
//                }
//            }
//            releasedConnection = connection;
//            socket = deallocate(noNewStreams, false, true);
//            if (connection != null || !reportedAcquired) releasedConnection = null;
//        }
//
//        closeQuietly(socket);
//        if (releasedConnection != null) {
//            eventListener.connectionReleased(call, releasedConnection);
//        }
//    }
//
//    /**
//     * Use this allocation to hold {@code connection}. Each call to this must be paired with a call to
//     * {@link #release} on the same connection.
//     */
//    public void acquire(RealConnection connection, boolean reportedAcquired) {
//        assert (Thread.holdsLock(connectionPool));
//        if (this.connection != null) throw new IllegalStateException();
//
//        this.connection = connection;
//        this.reportedAcquired = reportedAcquired;
//        connection.allocations.add(new StreamAllocation.StreamAllocationReference(this, callStackTrace));
//    }
//
//    /** Remove this allocation from the connection's list of allocations. */
//    private void release(RealConnection connection) {
//        for (int i = 0, size = connection.allocations.size(); i < size; i++) {
//            Reference<StreamAllocation> reference = connection.allocations.get(i);
//            if (reference.get() == this) {
//                connection.allocations.remove(i);
//                return;
//            }
//        }
//        throw new IllegalStateException();
//    }
//
//    /**
//     * Release the connection held by this connection and acquire {@code newConnection} instead. It is
//     * only safe to call this if the held connection is newly connected but duplicated by {@code
//     * newConnection}. Typically this occurs when concurrently connecting to an HTTP/2 webserver.
//     *
//     * <p>Returns a closeable that the caller should pass to {@link Util#closeQuietly} upon completion
//     * of the synchronized block. (We don't do I/O while synchronized on the connection pool.)
//     */
//    public Socket releaseAndAcquire(RealConnection newConnection) {
//        assert (Thread.holdsLock(connectionPool));
//        if (codec != null || connection.allocations.size() != 1) throw new IllegalStateException();
//
//        // Release the old connection.
//        Reference<StreamAllocation> onlyAllocation = connection.allocations.get(0);
//        Socket socket = deallocate(true, false, false);
//
//        // Acquire the new connection.
//        this.connection = newConnection;
//        newConnection.allocations.add(onlyAllocation);
//
//        return socket;
//    }
//
//    public boolean hasMoreRoutes() {
//        return route != null
//                || (routeSelection != null && routeSelection.hasNext())
//                || routeSelector.hasNext();
//    }
//
//    @Override public String toString() {
//        RealConnection connection = connection();
//        return connection != null ? connection.toString() : address.toString();
//    }
//
//    public static final class StreamAllocationReference extends WeakReference<StreamAllocation1> {
//        /**
//         * Captures the stack trace at the time the Call is executed or enqueued. This is helpful for
//         * identifying the origin of connection leaks.
//         */
//        public final Object callStackTrace;
//
//        StreamAllocationReference(StreamAllocation1 referent, Object callStackTrace) {
//            super(referent);
//            this.callStackTrace = callStackTrace;
//        }
//    }
}