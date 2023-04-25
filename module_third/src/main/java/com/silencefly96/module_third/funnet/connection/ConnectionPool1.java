package com.silencefly96.module_third.funnet.connection;

import static okhttp3.internal.Util.closeQuietly;

import androidx.annotation.Nullable;

import java.lang.ref.Reference;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import okhttp3.Address;
import okhttp3.Connection;
import okhttp3.Route;
import okhttp3.internal.Util;
import okhttp3.internal.connection.RealConnection;
import okhttp3.internal.connection.RouteDatabase;
import okhttp3.internal.connection.StreamAllocation;
import okhttp3.internal.platform.Platform;

/**
 * Manages reuse of HTTP and HTTP/2 connections for reduced network latency. HTTP requests that
 * share the same {@link Address} may share a {@link Connection}. This class implements the policy
 * of which connections to keep open for future use.
 */
public final class ConnectionPool1 {
    // 配合下面的cleanupRunnable用来清楚超时的Connection，SynchronousQueue所以保证了只有一个线程嘛？
    /**
     * Background threads are used to cleanup expired connections. There will be at most a single
     * thread running per connection pool. The thread pool executor permits the pool itself to be
     * garbage collected.
     */
    private static final Executor executor = new ThreadPoolExecutor(0 /* corePoolSize */,
            Integer.MAX_VALUE /* maximumPoolSize */, 60L /* keepAliveTime */, TimeUnit.SECONDS,
            new SynchronousQueue<Runnable>(), Util.threadFactory("OkHttp ConnectionPool", true));

    /** The maximum number of idle connections for each address. */
    private final int maxIdleConnections;
    private final long keepAliveDurationNs;
    // 用来清理connectionPool内的connection，清理一个只会会在延迟时间后清理下一个
    private final Runnable cleanupRunnable = new Runnable() {
        @Override public void run() {
            while (true) {
                // 得到延迟执行的时间
                long waitNanos = cleanup(System.nanoTime());
                // 对应没有connection的情况，会停止这个runnable，在put方法内会重新开始
                if (waitNanos == -1) return;
                if (waitNanos > 0) {
                    // 分成秒和纳秒两部分，Java的wait方法需要，接收纳秒范围0-999999
                    long waitMillis = waitNanos / 1000000L;
                    waitNanos -= (waitMillis * 1000000L);
                    // 连接池是全局单例？使用累锁进行卡线程
                    synchronized (ConnectionPool1.this) {
                        try {
                            ConnectionPool1.this.wait(waitMillis, (int) waitNanos);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }
        }
    };

    // 内部保存的RealConnection，遍历比较多所以用ArrayDeque不用LinkedList
    private final Deque<RealConnection> connections = new ArrayDeque<>();
    // RouteDatabase是对失败Route的一个记录，会在Internal中向外公开
    // 在StreamAllocation中参与routeSelector的创建，在findConnection中记录成功，在routeSelector中会有连接失败
    final RouteDatabase routeDatabase = new RouteDatabase();
    boolean cleanupRunning;

    // 默认情况，连接保留五分钟，在OkHttpClient.Builder中创建
    /**
     * Create a new connection pool with tuning parameters appropriate for a single-user application.
     * The tuning parameters in this pool are subject to change in future OkHttp releases. Currently
     * this pool holds up to 5 idle connections which will be evicted after 5 minutes of inactivity.
     */
    public ConnectionPool1() {
        this(5, 5, TimeUnit.MINUTES);
    }

    public ConnectionPool1(int maxIdleConnections, long keepAliveDuration, TimeUnit timeUnit) {
        this.maxIdleConnections = maxIdleConnections;
        this.keepAliveDurationNs = timeUnit.toNanos(keepAliveDuration);

        // Put a floor on the keep alive duration, otherwise cleanup will spin loop.
        if (keepAliveDuration <= 0) {
            throw new IllegalArgumentException("keepAliveDuration <= 0: " + keepAliveDuration);
        }
    }

    /** Returns the number of idle connections in the pool. */
    public synchronized int idleConnectionCount() {
        int total = 0;
        for (RealConnection connection : connections) {
            if (connection.allocations.isEmpty()) total++;
        }
        return total;
    }

    /**
     * Returns total number of connections in the pool. Note that prior to OkHttp 2.7 this included
     * only idle connections and HTTP/2 connections. Since OkHttp 2.7 this includes all connections,
     * both active and inactive. Use {@link #idleConnectionCount()} to count connections not currently
     * in use.
     */
    public synchronized int connectionCount() {
        return connections.size();
    }

    /**
     * Returns a recycled connection to {@code address}, or null if no such connection exists. The
     * route is null if the address has not yet been routed.
     */
    @Nullable
    RealConnection get(Address address, StreamAllocation streamAllocation, Route route) {
        assert (Thread.holdsLock(this));
        for (RealConnection connection : connections) {
            // connection对address、route可用(Eligible)，connection能创建流、不在黑名单、特殊情况
            if (connection.isEligible(address, route)) {
                // 让当前的streamAllocation去持有这个connection，并且让connection中记录这个流的分配
                streamAllocation.acquire(connection, true);
                return connection;
            }
        }
        return null;
    }

    // 如果可能，将 streamAllocation 持有的连接替换为共享连接。当同时创建多个多路复用连接时，这会恢复
    // deduplicate在Internal.instance调用，最终用在StreamAllocation的findConnection中，防止异步创建两个相同地址的connection
    /**
     * Replaces the connection held by {@code streamAllocation} with a shared connection if possible.
     * This recovers when multiple multiplexed connections are created concurrently.
     */
    @Nullable
    Socket deduplicate(Address address, StreamAllocation streamAllocation) {
        assert (Thread.holdsLock(this));
        for (RealConnection connection : connections) {
            // isMultiplexed返回http2Connection!=null，只有HTTP2才复用连接
            if (connection.isEligible(address, null)
                    && connection.isMultiplexed()
                    && connection != streamAllocation.connection()) {
                return streamAllocation.releaseAndAcquire(connection);
            }
        }
        return null;
    }

    // 通过Internal在StreamAllocation的findConnection中使用
    void put(RealConnection connection) {
        assert (Thread.holdsLock(this));
        // 启动清理connection的线程，正常情况按keepAliveDurationNs周期执行
        if (!cleanupRunning) {
            cleanupRunning = true;
            executor.execute(cleanupRunnable);
        }
        connections.add(connection);
    }

    // 通过Internal在StreamAllocation的deallocate(解除分配)方法中调用
    // 通知此连接已变为空闲。如果连接已从池中删除并应关闭，则返回 true。
    /**
     * Notify this pool that {@code connection} has become idle. Returns true if the connection has
     * been removed from the pool and should be closed.
     */
    boolean connectionBecameIdle(RealConnection connection) {
        assert (Thread.holdsLock(this));
        if (connection.noNewStreams || maxIdleConnections == 0) {
            connections.remove(connection);
            return true;
        } else {
            // 唤醒cleanup线程，我们可能已经超过空闲连接限制
            // 在cleanup方法中synchronized (this)了，其他地方对connection加了对象锁的话，会卡住cleanupRunnable的cleanup方法
            notifyAll(); // Awake the cleanup thread: we may have exceeded the idle connection limit.
            return false;
        }
    }

    // 向外提供的方法，通过client.connectionPool().evictAll()调用
    /** Close and remove all idle connections in the pool. */
    public void evictAll() {
        List<RealConnection> evictedConnections = new ArrayList<>();
        synchronized (this) {
            for (Iterator<RealConnection> i = connections.iterator(); i.hasNext(); ) {
                RealConnection connection = i.next();
                if (connection.allocations.isEmpty()) {
                    connection.noNewStreams = true;
                    evictedConnections.add(connection);
                    i.remove();
                }
            }
        }

        for (RealConnection connection : evictedConnections) {
            closeQuietly(connection.socket());
        }
    }

    /**
     * Performs maintenance on this pool, evicting the connection that has been idle the longest if
     * either it has exceeded the keep alive limit or the idle connections limit.
     *
     * <p>Returns the duration in nanos to sleep until the next scheduled call to this method. Returns
     * -1 if no further cleanups are required.
     */
    long cleanup(long now) {
        int inUseConnectionCount = 0;
        int idleConnectionCount = 0;
        RealConnection longestIdleConnection = null;
        long longestIdleDurationNs = Long.MIN_VALUE;

        // 找到要去除的connection，或者下一个要调用驱除的时间
        // Find either a connection to evict, or the time that the next eviction is due.
        synchronized (this) {
            for (Iterator<RealConnection> i = connections.iterator(); i.hasNext(); ) {
                RealConnection connection = i.next();

                // 修剪任何泄漏的分配(整理connection内的StreamAllocation)，返回正在使用的流的数量，并对泄露情况处理
                // If the connection is in use, keep searching.
                if (pruneAndGetAllocationCount(connection, now) > 0) {
                    inUseConnectionCount++;
                    continue;
                }

                // connection没有分配流，即该connection不在使用了
                idleConnectionCount++;

                // 拿到空闲最久的connection，只拿一个？
                // If the connection is ready to be evicted, we're done.
                long idleDurationNs = now - connection.idleAtNanos;
                if (idleDurationNs > longestIdleDurationNs) {
                    longestIdleDurationNs = idleDurationNs;
                    longestIdleConnection = connection;
                }
            }

            // 对当前最长空闲的connection处理，超时、超多时移除该connection
            // 这里会return 0 => 直接进入cleanupRunnable下一个cleanup
            if (longestIdleDurationNs >= this.keepAliveDurationNs
                    || idleConnectionCount > this.maxIdleConnections) {
                // We've found a connection to evict. Remove it from the list, then close it below (outside
                // of the synchronized block).
                connections.remove(longestIdleConnection);

                // 最长空闲的connection还没超时，计算下下次触发cleanup的等待时间
            } else if (idleConnectionCount > 0) {
                // A connection will be ready to evict soon.
                return keepAliveDurationNs - longestIdleDurationNs;

                // connection还在使用，进入下一个keepAliveDurationNs周期的cleanup
            } else if (inUseConnectionCount > 0) {
                // All connections are in use. It'll be at least the keep alive duration 'til we run again.
                return keepAliveDurationNs;

                // 当前connectionPool里面没有connection，return -1 => cleanupRunnable周期循环结束
            } else {
                // 为什么写在这，不写到cleanupRunnable里面去。。。
                // No connections, idle or in use.
                cleanupRunning = false;
                return -1;
            }
        }

        // 对应上面if第一个情况，所以最长等待的connection需要关闭socket
        closeQuietly(longestIdleConnection.socket());

        // Cleanup again immediately.
        return 0;
    }

    // 修剪任何泄漏的分配，然后返回连接上剩余的活动分配数。如果连接正在跟踪分配但应用程序代码已放弃它们，则分配会泄漏。
    // 泄漏检测是不精确的并且依赖于垃圾收集。
    /**
     * Prunes any leaked allocations and then returns the number of remaining live allocations on
     * {@code connection}. Allocations are leaked if the connection is tracking them but the
     * application code has abandoned them. Leak detection is imprecise and relies on garbage
     * collection.
     */
    private int pruneAndGetAllocationCount(RealConnection connection, long now) {
        // RealConnection中储存的流的情况，在ConnectionPool的get方法中创建的弱引用
        List<Reference<StreamAllocation>> references = connection.allocations;
        for (int i = 0; i < references.size(); ) {
            Reference<StreamAllocation> reference = references.get(i);

            // 流还在使用
            if (reference.get() != null) {
                i++;
                continue;
            }

            // 流已经被GC了，弱引用还存在，内存泄露了，可能是没有关闭response body的流
            // We've discovered a leaked allocation. This is an application bug.
            StreamAllocation.StreamAllocationReference streamAllocRef =
                    (StreamAllocation.StreamAllocationReference) reference;
            String message = "A connection to " + connection.route().address().url()
                    + " was leaked. Did you forget to close a response body?";
            Platform.get().logCloseableLeak(message, streamAllocRef.callStackTrace);

            // 没关闭response body的流所以不能创建新流了？等着已经有的流关闭？
            references.remove(i);
            connection.noNewStreams = true;

            // If this was the last allocation, the connection is eligible for immediate eviction.
            if (references.isEmpty()) {
                // 用当前的time减去连接保持的时间，修改开始的time，在cleanup里面就会认为这个连接正好超时了，会被清理
                connection.idleAtNanos = now - keepAliveDurationNs;
                return 0;
            }
        }

        return references.size();
    }
}