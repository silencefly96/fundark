package com.silencefly96.module_third.funnet.connection;


//import java.io.Closeable;
//import java.io.IOException;
//import java.io.InterruptedIOException;
//import java.net.InetSocketAddress;
//import java.net.Socket;
//import java.util.LinkedHashMap;
//import java.util.LinkedHashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.LinkedBlockingQueue;
//import java.util.concurrent.RejectedExecutionException;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.ScheduledThreadPoolExecutor;
//import java.util.concurrent.SynchronousQueue;
//import java.util.concurrent.ThreadPoolExecutor;
//import java.util.concurrent.TimeUnit;
//import okhttp3.Headers;
//import okhttp3.Protocol;
//import okhttp3.internal.NamedRunnable;
//import okhttp3.internal.Util;
//import okhttp3.internal.http2.ConnectionShutdownException;
//import okhttp3.internal.http2.ErrorCode;
//import okhttp3.internal.http2.Header;
//import okhttp3.internal.http2.Http2;
//import okhttp3.internal.http2.Http2Reader;
//import okhttp3.internal.http2.Http2Stream;
//import okhttp3.internal.http2.Http2Writer;
//import okhttp3.internal.http2.PushObserver;
//import okhttp3.internal.http2.Settings;
//import okhttp3.internal.platform.Platform;
//import okio.Buffer;
//import okio.BufferedSink;
//import okio.BufferedSource;
//import okio.ByteString;
//import okio.Okio;
//
//import static java.util.concurrent.TimeUnit.MILLISECONDS;
//import static okhttp3.internal.http2.ErrorCode.REFUSED_STREAM;
//import static okhttp3.internal.http2.Settings.DEFAULT_INITIAL_WINDOW_SIZE;
//import static okhttp3.internal.platform.Platform.INFO;

import java.io.Closeable;
import java.io.IOException;

/**
 * A socket connection to a remote peer. A connection hosts streams which can send and receive
 * data.
 *
 * <p>Many methods in this API are <strong>synchronous:</strong> the call is completed before the
 * method returns. This is typical for Java but atypical for HTTP/2. This is motivated by exception
 * transparency: an IOException that was triggered by a certain caller can be caught and handled by
 * that caller.
 */
public final class Http2Connection1 implements Closeable {
    @Override
    public void close() throws IOException {

    }
//
//    // Internal state of this connection is guarded by 'this'. No blocking
//    // operations may be performed while holding this lock!
//    //
//    // Socket writes are guarded by frameWriter.
//    //
//    // Socket reads are unguarded but are only made by the reader thread.
//    //
//    // Certain operations (like SYN_STREAM) need to synchronize on both the
//    // frameWriter (to do blocking I/O) and this (to create streams). Such
//    // operations must synchronize on 'this' last. This ensures that we never
//    // wait for a blocking operation while holding 'this'.
//
//    static final int OKHTTP_CLIENT_WINDOW_SIZE = 16 * 1024 * 1024;
//
//    /**
//     * Shared executor to send notifications of incoming streams. This executor requires multiple
//     * threads because listeners are not required to return promptly.
//     */
//    private static final ExecutorService listenerExecutor = new ThreadPoolExecutor(0,
//            Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
//            Util.threadFactory("OkHttp Http2Connection", true));
//
//    /** True if this peer initiated the connection. */
//    final boolean client;
//
//    /**
//     * User code to run in response to incoming streams or settings. Calls to this are always invoked
//     * on {@link #listenerExecutor}.
//     */
//    final okhttp3.internal.http2.Http2Connection.Listener listener;
//    final Map<Integer, Http2Stream> streams = new LinkedHashMap<>();
//    final String hostname;
//    int lastGoodStreamId;
//    int nextStreamId;
//    boolean shutdown;
//
//    /** Asynchronously writes frames to the outgoing socket. */
//    private final ScheduledExecutorService writerExecutor;
//
//    /** Ensures push promise callbacks events are sent in order per stream. */
//    private final ExecutorService pushExecutor;
//
//    /** User code to run in response to push promise events. */
//    final PushObserver pushObserver;
//
//    /** True if we have sent a ping that is still awaiting a reply. */
//    private boolean awaitingPong;
//
//    /**
//     * The total number of bytes consumed by the application, but not yet acknowledged by sending a
//     * {@code WINDOW_UPDATE} frame on this connection.
//     */
//    // Visible for testing
//    long unacknowledgedBytesRead = 0;
//
//    /**
//     * Count of bytes that can be written on the connection before receiving a window update.
//     */
//    // Visible for testing
//    long bytesLeftInWriteWindow;
//
//    /** Settings we communicate to the peer. */
//    Settings okHttpSettings = new Settings();
//
//    /** Settings we receive from the peer. */
//    // TODO: MWS will need to guard on this setting before attempting to push.
//    final Settings peerSettings = new Settings();
//
//    boolean receivedInitialPeerSettings = false;
//    final Socket socket;
//    final Http2Writer writer;
//
//    // Visible for testing
//    final okhttp3.internal.http2.Http2Connection.ReaderRunnable readerRunnable;
//
//    Http2Connection(Builder builder) {
//        pushObserver = builder.pushObserver;
//        client = builder.client;
//        listener = builder.listener;
//        // http://tools.ietf.org/html/draft-ietf-httpbis-http2-17#section-5.1.1
//
//        // 这里做了说明，client用单数StreamId，server用偶数StreamId，并且1作为Upgrade帧？
//        nextStreamId = builder.client ? 1 : 2;
//        if (builder.client) {
//            nextStreamId += 2; // In HTTP/2, 1 on client is reserved for Upgrade.
//        }
//
//        // Flow control was designed more for servers, or proxies than edge clients.
//        // If we are a client, set the flow control window to 16MiB.  This avoids
//        // thrashing window updates every 64KiB, yet small enough to avoid blowing
//        // up the heap.
//        if (builder.client) {
//            okHttpSettings.set(Settings.INITIAL_WINDOW_SIZE, OKHTTP_CLIENT_WINDOW_SIZE);
//        }
//
//        hostname = builder.hostname;
//
//        writerExecutor = new ScheduledThreadPoolExecutor(1,
//                Util.threadFactory(Util.format("OkHttp %s Writer", hostname), false));
//        if (builder.pingIntervalMillis != 0) {
//            writerExecutor.scheduleAtFixedRate(new Http2Connection1.PingRunnable(false, 0, 0),
//                    builder.pingIntervalMillis, builder.pingIntervalMillis, MILLISECONDS);
//        }
//
//        // Like newSingleThreadExecutor, except lazy creates the thread.
//        pushExecutor = new ThreadPoolExecutor(0, 1, 60, TimeUnit.SECONDS,
//                new LinkedBlockingQueue<Runnable>(),
//                Util.threadFactory(Util.format("OkHttp %s Push Observer", hostname), true));
//        peerSettings.set(Settings.INITIAL_WINDOW_SIZE, DEFAULT_INITIAL_WINDOW_SIZE);
//        peerSettings.set(Settings.MAX_FRAME_SIZE, Http2.INITIAL_MAX_FRAME_SIZE);
//        bytesLeftInWriteWindow = peerSettings.getInitialWindowSize();
//        socket = builder.socket;
//        writer = new Http2Writer(builder.sink, client);
//
//        // 这里，Http2Writer和Http2Reader传入了socket的输入输出流
//        readerRunnable = new Http2Connection1.ReaderRunnable(new Http2Reader(builder.source, client));
//    }
//
//    /** The protocol as selected using ALPN. */
//    public Protocol getProtocol() {
//        return Protocol.HTTP_2;
//    }
//
//    /**
//     * Returns the number of {@link Http2Stream#isOpen() open streams} on this connection.
//     */
//    public synchronized int openStreamCount() {
//        return streams.size();
//    }
//
//    synchronized Http2Stream getStream(int id) {
//        return streams.get(id);
//    }
//
//    synchronized Http2Stream removeStream(int streamId) {
//        Http2Stream stream = streams.remove(streamId);
//        notifyAll(); // The removed stream may be blocked on a connection-wide window update.
//        return stream;
//    }
//
//    public synchronized int maxConcurrentStreams() {
//        return peerSettings.getMaxConcurrentStreams(Integer.MAX_VALUE);
//    }
//
//    synchronized void updateConnectionFlowControl(long read) {
//        unacknowledgedBytesRead += read;
//        if (unacknowledgedBytesRead >= okHttpSettings.getInitialWindowSize() / 2) {
//            writeWindowUpdateLater(0, unacknowledgedBytesRead);
//            unacknowledgedBytesRead = 0;
//        }
//    }
//
//    /**
//     * Returns a new server-initiated stream.
//     *
//     * @param associatedStreamId the stream that triggered the sender to create this stream.
//     * @param out true to create an output stream that we can use to send data to the remote peer.
//     * Corresponds to {@code FLAG_FIN}.
//     */
//    public Http2Stream pushStream(int associatedStreamId, List<Header> requestHeaders, boolean out)
//            throws IOException {
//        if (client) throw new IllegalStateException("Client cannot push requests.");
//        return newStream(associatedStreamId, requestHeaders, out);
//    }
//
//    /**
//     * Returns a new locally-initiated stream.
//     * @param out true to create an output stream that we can use to send data to the remote peer.
//     * Corresponds to {@code FLAG_FIN}.
//     */
//    public Http2Stream newStream(List<Header> requestHeaders, boolean out) throws IOException {
//        // Http2Codec的writeRequestHeaders会调用这里，创建一个Http2Stream，输入输出都是在一个流上
//        return newStream(0, requestHeaders, out);
//    }
//
//    private Http2Stream newStream(
//            int associatedStreamId, List<Header> requestHeaders, boolean out) throws IOException {
//        boolean outFinished = !out;
//        boolean inFinished = false;
//        boolean flushHeaders;
//        Http2Stream stream;
//        int streamId;
//
//        synchronized (writer) {
//            synchronized (this) {
//                if (nextStreamId > Integer.MAX_VALUE / 2) {
//                    shutdown(REFUSED_STREAM);
//                }
//                if (shutdown) {
//                    throw new ConnectionShutdownException();
//                }
//                // 输入输出流，所以加2？associatedStreamId?
//                streamId = nextStreamId;
//                nextStreamId += 2;
//                // 创建的Http2Stream会用来接收Http2Reader里面的帧数据，写入的时候会通过Http2Reader写入
//                stream = new Http2Stream(streamId, this, outFinished, inFinished, null);
//                flushHeaders = !out || bytesLeftInWriteWindow == 0L || stream.bytesLeftInWriteWindow == 0L;
//                if (stream.isOpen()) {
//                    // 保存起来，一个连接可以有多个HTTPS请求，即多个Http2Stream
//                    streams.put(streamId, stream);
//                }
//            }
//            if (associatedStreamId == 0) {
//                // 发送请求头，associatedStreamId==0就是刚开始吧
//                writer.synStream(outFinished, streamId, associatedStreamId, requestHeaders);
//            } else if (client) {
//                throw new IllegalArgumentException("client streams shouldn't have associated stream IDs");
//            } else { // HTTP/2 has a PUSH_PROMISE frame.
//                writer.pushPromise(associatedStreamId, streamId, requestHeaders);
//            }
//        }
//
//        if (flushHeaders) {
//            writer.flush();
//        }
//
//        return stream;
//    }
//
//    void writeSynReply(int streamId, boolean outFinished, List<Header> alternating)
//            throws IOException {
//        writer.synReply(outFinished, streamId, alternating);
//    }
//
//    /**
//     * Callers of this method are not thread safe, and sometimes on application threads. Most often,
//     * this method will be called to send a buffer worth of data to the peer.
//     *
//     * <p>Writes are subject to the write window of the stream and the connection. Until there is a
//     * window sufficient to send {@code byteCount}, the caller will block. For example, a user of
//     * {@code HttpURLConnection} who flushes more bytes to the output stream than the connection's
//     * write window will block.
//     *
//     * <p>Zero {@code byteCount} writes are not subject to flow control and will not block. The only
//     * use case for zero {@code byteCount} is closing a flushed output stream.
//     */
//    public void writeData(int streamId, boolean outFinished, Buffer buffer, long byteCount)
//            throws IOException {
//        if (byteCount == 0) { // Empty data frames are not flow-controlled.
//            writer.data(outFinished, streamId, buffer, 0);
//            return;
//        }
//
//        while (byteCount > 0) {
//            int toWrite;
//            synchronized (okhttp3.internal.http2.Http2Connection.this) {
//                try {
//                    while (bytesLeftInWriteWindow <= 0) {
//                        // Before blocking, confirm that the stream we're writing is still open. It's possible
//                        // that the stream has since been closed (such as if this write timed out.)
//                        if (!streams.containsKey(streamId)) {
//                            throw new IOException("stream closed");
//                        }
//                        okhttp3.internal.http2.Http2Connection.this.wait(); // Wait until we receive a WINDOW_UPDATE.
//                    }
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt(); // Retain interrupted status.
//                    throw new InterruptedIOException();
//                }
//
//                toWrite = (int) Math.min(byteCount, bytesLeftInWriteWindow);
//                toWrite = Math.min(toWrite, writer.maxDataLength());
//                bytesLeftInWriteWindow -= toWrite;
//            }
//
//            byteCount -= toWrite;
//            writer.data(outFinished && byteCount == 0, streamId, buffer, toWrite);
//        }
//    }
//
//    void writeSynResetLater(final int streamId, final ErrorCode errorCode) {
//        try {
//            writerExecutor.execute(new NamedRunnable("OkHttp %s stream %d", hostname, streamId) {
//                @Override public void execute() {
//                    try {
//                        writeSynReset(streamId, errorCode);
//                    } catch (IOException e) {
//                        failConnection();
//                    }
//                }
//            });
//        } catch (RejectedExecutionException ignored) {
//            // This connection has been closed.
//        }
//    }
//
//    void writeSynReset(int streamId, ErrorCode statusCode) throws IOException {
//        writer.rstStream(streamId, statusCode);
//    }
//
//    void writeWindowUpdateLater(final int streamId, final long unacknowledgedBytesRead) {
//        try {
//            writerExecutor.execute(
//                    new NamedRunnable("OkHttp Window Update %s stream %d", hostname, streamId) {
//                        @Override public void execute() {
//                            try {
//                                writer.windowUpdate(streamId, unacknowledgedBytesRead);
//                            } catch (IOException e) {
//                                failConnection();
//                            }
//                        }
//                    });
//        } catch (RejectedExecutionException ignored) {
//            // This connection has been closed.
//        }
//    }
//
//    final class PingRunnable extends NamedRunnable {
//        final boolean reply;
//        final int payload1;
//        final int payload2;
//
//        PingRunnable(boolean reply, int payload1, int payload2) {
//            super("OkHttp %s ping %08x%08x", hostname, payload1, payload2);
//            this.reply = reply;
//            this.payload1 = payload1;
//            this.payload2 = payload2;
//        }
//
//        @Override public void execute() {
//            writePing(reply, payload1, payload2);
//        }
//    }
//
//    void writePing(boolean reply, int payload1, int payload2) {
//        if (!reply) {
//            boolean failedDueToMissingPong;
//            synchronized (this) {
//                failedDueToMissingPong = awaitingPong;
//                awaitingPong = true;
//            }
//            if (failedDueToMissingPong) {
//                failConnection();
//                return;
//            }
//        }
//
//        try {
//            writer.ping(reply, payload1, payload2);
//        } catch (IOException e) {
//            failConnection();
//        }
//    }
//
//    /** For testing: sends a ping and waits for a pong. */
//    void writePingAndAwaitPong() throws InterruptedException {
//        writePing(false, 0x4f4b6f6b /* "OKok" */, 0xf09f8da9 /* donut */);
//        awaitPong();
//    }
//
//    /** For testing: waits until {@code requiredPongCount} pings have been received from the peer. */
//    synchronized void awaitPong() throws InterruptedException {
//        while (awaitingPong) {
//            wait();
//        }
//    }
//
//    public void flush() throws IOException {
//        writer.flush();
//    }
//
//    /**
//     * Degrades this connection such that new streams can neither be created locally, nor accepted
//     * from the remote peer. Existing streams are not impacted. This is intended to permit an endpoint
//     * to gracefully stop accepting new requests without harming previously established streams.
//     */
//    public void shutdown(ErrorCode statusCode) throws IOException {
//        synchronized (writer) {
//            int lastGoodStreamId;
//            synchronized (this) {
//                if (shutdown) {
//                    return;
//                }
//                shutdown = true;
//                lastGoodStreamId = this.lastGoodStreamId;
//            }
//            // TODO: propagate exception message into debugData.
//            // TODO: configure a timeout on the reader so that it doesn’t block forever.
//            writer.goAway(lastGoodStreamId, statusCode, Util.EMPTY_BYTE_ARRAY);
//        }
//    }
//
//    /**
//     * Closes this connection. This cancels all open streams and unanswered pings. It closes the
//     * underlying input and output streams and shuts down internal executor services.
//     */
//    @Override public void close() throws IOException {
//        close(ErrorCode.NO_ERROR, ErrorCode.CANCEL);
//    }
//
//    void close(ErrorCode connectionCode, ErrorCode streamCode) throws IOException {
//        assert (!Thread.holdsLock(this));
//        IOException thrown = null;
//        try {
//            shutdown(connectionCode);
//        } catch (IOException e) {
//            thrown = e;
//        }
//
//        Http2Stream[] streamsToClose = null;
//        synchronized (this) {
//            if (!streams.isEmpty()) {
//                streamsToClose = streams.values().toArray(new Http2Stream[streams.size()]);
//                streams.clear();
//            }
//        }
//
//        if (streamsToClose != null) {
//            for (Http2Stream stream : streamsToClose) {
//                try {
//                    stream.close(streamCode);
//                } catch (IOException e) {
//                    if (thrown != null) thrown = e;
//                }
//            }
//        }
//
//        // Close the writer to release its resources (such as deflaters).
//        try {
//            writer.close();
//        } catch (IOException e) {
//            if (thrown == null) thrown = e;
//        }
//
//        // Close the socket to break out the reader thread, which will clean up after itself.
//        try {
//            socket.close();
//        } catch (IOException e) {
//            thrown = e;
//        }
//
//        // Release the threads.
//        writerExecutor.shutdown();
//        pushExecutor.shutdown();
//
//        if (thrown != null) throw thrown;
//    }
//
//    private void failConnection() {
//        try {
//            close(ErrorCode.PROTOCOL_ERROR, ErrorCode.PROTOCOL_ERROR);
//        } catch (IOException ignored) {
//        }
//    }
//
//    /**
//     * Sends any initial frames and starts reading frames from the remote peer. This should be called
//     * after {@link okhttp3.internal.http2.Http2Connection.Builder#build} for all new connections.
//     */
//    public void start() throws IOException {
//        start(true);
//    }
//
//    /**
//     * @param sendConnectionPreface true to send connection preface frames. This should always be true
//     *     except for in tests that don't check for a connection preface.
//     */
//    void start(boolean sendConnectionPreface) throws IOException {
//        if (sendConnectionPreface) {
//            // 向输出流写入一些配置
//            writer.connectionPreface();
//            writer.settings(okHttpSettings);
//            // stream的窗口值
//            int windowSize = okHttpSettings.getInitialWindowSize();
//            if (windowSize != Settings.DEFAULT_INITIAL_WINDOW_SIZE) {
//                writer.windowUpdate(0, windowSize - Settings.DEFAULT_INITIAL_WINDOW_SIZE);
//            }
//        }
//        new Thread(readerRunnable).start(); // Not a daemon thread.
//    }
//
//    /** Merges {@code settings} into this peer's settings and sends them to the remote peer. */
//    public void setSettings(Settings settings) throws IOException {
//        synchronized (writer) {
//            synchronized (this) {
//                if (shutdown) {
//                    throw new ConnectionShutdownException();
//                }
//                okHttpSettings.merge(settings);
//            }
//            writer.settings(settings);
//        }
//    }
//
//    public synchronized boolean isShutdown() {
//        return shutdown;
//    }
//
//    public static class Builder {
//        Socket socket;
//        String hostname;
//        BufferedSource source;
//        BufferedSink sink;
//        okhttp3.internal.http2.Http2Connection.Listener listener = okhttp3.internal.http2.Http2Connection.Listener.REFUSE_INCOMING_STREAMS;
//        PushObserver pushObserver = PushObserver.CANCEL;
//        boolean client;
//        int pingIntervalMillis;
//
//        /**
//         * @param client true if this peer initiated the connection; false if this peer accepted the
//         * connection.
//         */
//        public Builder(boolean client) {
//            this.client = client;
//        }
//
//        public okhttp3.internal.http2.Http2Connection.Builder socket(Socket socket) throws IOException {
//            return socket(socket, ((InetSocketAddress) socket.getRemoteSocketAddress()).getHostName(),
//                    Okio.buffer(Okio.source(socket)), Okio.buffer(Okio.sink(socket)));
//        }
//
//        public okhttp3.internal.http2.Http2Connection.Builder socket(
//                Socket socket, String hostname, BufferedSource source, BufferedSink sink) {
//            this.socket = socket;
//            this.hostname = hostname;
//            this.source = source;
//            this.sink = sink;
//            return this;
//        }
//
//        public okhttp3.internal.http2.Http2Connection.Builder listener(okhttp3.internal.http2.Http2Connection.Listener listener) {
//            this.listener = listener;
//            return this;
//        }
//
//        public okhttp3.internal.http2.Http2Connection.Builder pushObserver(PushObserver pushObserver) {
//            this.pushObserver = pushObserver;
//            return this;
//        }
//
//        public okhttp3.internal.http2.Http2Connection.Builder pingIntervalMillis(int pingIntervalMillis) {
//            this.pingIntervalMillis = pingIntervalMillis;
//            return this;
//        }
//
//        public okhttp3.internal.http2.Http2Connection build() {
//            return new okhttp3.internal.http2.Http2Connection(this);
//        }
//    }
//
//    /**
//     * Methods in this class must not lock FrameWriter.  If a method needs to write a frame, create an
//     * async task to do so.
//     */
//    class ReaderRunnable extends NamedRunnable implements Http2Reader.Handler {
//        final Http2Reader reader;
//
//        ReaderRunnable(Http2Reader reader) {
//            super("OkHttp %s", hostname);
//            this.reader = reader;
//        }
//
//        @Override protected void execute() {
//            ErrorCode connectionErrorCode = ErrorCode.INTERNAL_ERROR;
//            ErrorCode streamErrorCode = ErrorCode.INTERNAL_ERROR;
//            try {
//                // server读取connectionPreface进行比较，client读取下一帧
//                reader.readConnectionPreface(this);
//
//                // 进入Http2Reader中，循环读取帧，通过回调在当前runnable中处理
//                // HTTP/2 是基于帧的协议。采用分帧是为了将重要信息封装起来，让协议的解析方可以轻松阅读、解析并还原信息。
//                // 有多种帧类型，每次处理不同的帧，回调到当前runnable中处理 //see: https://zhuanlan.zhihu.com/p/141458270
//                while (reader.nextFrame(false, this)) {
//                }
//                // 循环体结束，连接结束
//                connectionErrorCode = ErrorCode.NO_ERROR;
//                streamErrorCode = ErrorCode.CANCEL;
//            } catch (IOException e) {
//                connectionErrorCode = ErrorCode.PROTOCOL_ERROR;
//                streamErrorCode = ErrorCode.PROTOCOL_ERROR;
//            } finally {
//                try {
//                    close(connectionErrorCode, streamErrorCode);
//                } catch (IOException ignored) {
//                }
//                Util.closeQuietly(reader);
//            }
//        }
//
//        @Override public void data(boolean inFinished, int streamId, BufferedSource source, int length)
//                throws IOException {
//            //
//            if (pushedStream(streamId)) {
//                pushDataLater(streamId, source, length, inFinished);
//                return;
//            }
//            // 根据streamId拿到对应的dataStream，每个HTTPS请求会在TCP连接上创建一个流
//            Http2Stream dataStream = getStream(streamId);
//            if (dataStream == null) {
//                writeSynResetLater(streamId, ErrorCode.PROTOCOL_ERROR);
//                updateConnectionFlowControl(length);
//                source.skip(length);
//                return;
//            }
//            // 将拿到的帧数据写到对应dataStream
//            dataStream.receiveData(source, length);
//            if (inFinished) {
//                dataStream.receiveFin();
//            }
//        }
//
//        @Override public void headers(boolean inFinished, int streamId, int associatedStreamId,
//                                      List<Header> headerBlock) {
//            if (pushedStream(streamId)) {
//                pushHeadersLater(streamId, headerBlock, inFinished);
//                return;
//            }
//            Http2Stream stream;
//            synchronized (okhttp3.internal.http2.Http2Connection.this) {
//                stream = getStream(streamId);
//
//                if (stream == null) {
//                    // If we're shutdown, don't bother with this stream.
//                    if (shutdown) return;
//
//                    // If the stream ID is less than the last created ID, assume it's already closed.
//                    if (streamId <= lastGoodStreamId) return;
//
//                    // If the stream ID is in the client's namespace, assume it's already closed.
//                    if (streamId % 2 == nextStreamId % 2) return;
//
//                    // Create a stream.
//                    Headers headers = Util.toHeaders(headerBlock);
//                    final Http2Stream newStream = new Http2Stream(streamId, okhttp3.internal.http2.Http2Connection.this,
//                            false, inFinished, headers);
//                    lastGoodStreamId = streamId;
//                    streams.put(streamId, newStream);
//                    listenerExecutor.execute(new NamedRunnable("OkHttp %s stream %d", hostname, streamId) {
//                        @Override public void execute() {
//                            try {
//                                listener.onStream(newStream);
//                            } catch (IOException e) {
//                                Platform.get().log(INFO, "Http2Connection.Listener failure for " + hostname, e);
//                                try {
//                                    newStream.close(ErrorCode.PROTOCOL_ERROR);
//                                } catch (IOException ignored) {
//                                }
//                            }
//                        }
//                    });
//                    return;
//                }
//            }
//
//            // Update an existing stream.
//            stream.receiveHeaders(headerBlock);
//            if (inFinished) stream.receiveFin();
//        }
//
//        @Override public void rstStream(int streamId, ErrorCode errorCode) {
//            if (pushedStream(streamId)) {
//                pushResetLater(streamId, errorCode);
//                return;
//            }
//            Http2Stream rstStream = removeStream(streamId);
//            if (rstStream != null) {
//                rstStream.receiveRstStream(errorCode);
//            }
//        }
//
//        @Override public void settings(boolean clearPrevious, Settings newSettings) {
//            long delta = 0;
//            Http2Stream[] streamsToNotify = null;
//            synchronized (okhttp3.internal.http2.Http2Connection.this) {
//                int priorWriteWindowSize = peerSettings.getInitialWindowSize();
//                if (clearPrevious) peerSettings.clear();
//                peerSettings.merge(newSettings);
//                applyAndAckSettings(newSettings);
//                int peerInitialWindowSize = peerSettings.getInitialWindowSize();
//                if (peerInitialWindowSize != -1 && peerInitialWindowSize != priorWriteWindowSize) {
//                    delta = peerInitialWindowSize - priorWriteWindowSize;
//                    if (!receivedInitialPeerSettings) {
//                        receivedInitialPeerSettings = true;
//                    }
//                    if (!streams.isEmpty()) {
//                        streamsToNotify = streams.values().toArray(new Http2Stream[streams.size()]);
//                    }
//                }
//                listenerExecutor.execute(new NamedRunnable("OkHttp %s settings", hostname) {
//                    @Override public void execute() {
//                        listener.onSettings(okhttp3.internal.http2.Http2Connection.this);
//                    }
//                });
//            }
//            if (streamsToNotify != null && delta != 0) {
//                for (Http2Stream stream : streamsToNotify) {
//                    synchronized (stream) {
//                        stream.addBytesToWriteWindow(delta);
//                    }
//                }
//            }
//        }
//
//        private void applyAndAckSettings(final Settings peerSettings) {
//            try {
//                writerExecutor.execute(new NamedRunnable("OkHttp %s ACK Settings", hostname) {
//                    @Override public void execute() {
//                        try {
//                            writer.applyAndAckSettings(peerSettings);
//                        } catch (IOException e) {
//                            failConnection();
//                        }
//                    }
//                });
//            } catch (RejectedExecutionException ignored) {
//                // This connection has been closed.
//            }
//        }
//
//        @Override public void ackSettings() {
//            // TODO: If we don't get this callback after sending settings to the peer, SETTINGS_TIMEOUT.
//        }
//
//        @Override public void ping(boolean reply, int payload1, int payload2) {
//            if (reply) {
//                synchronized (okhttp3.internal.http2.Http2Connection.this) {
//                    awaitingPong = false;
//                    okhttp3.internal.http2.Http2Connection.this.notifyAll();
//                }
//            } else {
//                try {
//                    // Send a reply to a client ping if this is a server and vice versa.
//                    writerExecutor.execute(new okhttp3.internal.http2.Http2Connection.PingRunnable(true, payload1, payload2));
//                } catch (RejectedExecutionException ignored) {
//                    // This connection has been closed.
//                }
//            }
//        }
//
//        @Override public void goAway(int lastGoodStreamId, ErrorCode errorCode, ByteString debugData) {
//            if (debugData.size() > 0) { // TODO: log the debugData
//            }
//
//            // Copy the streams first. We don't want to hold a lock when we call receiveRstStream().
//            Http2Stream[] streamsCopy;
//            synchronized (okhttp3.internal.http2.Http2Connection.this) {
//                streamsCopy = streams.values().toArray(new Http2Stream[streams.size()]);
//                shutdown = true;
//            }
//
//            // Fail all streams created after the last good stream ID.
//            for (Http2Stream http2Stream : streamsCopy) {
//                if (http2Stream.getId() > lastGoodStreamId && http2Stream.isLocallyInitiated()) {
//                    http2Stream.receiveRstStream(REFUSED_STREAM);
//                    removeStream(http2Stream.getId());
//                }
//            }
//        }
//
//        @Override public void windowUpdate(int streamId, long windowSizeIncrement) {
//            if (streamId == 0) {
//                synchronized (okhttp3.internal.http2.Http2Connection.this) {
//                    bytesLeftInWriteWindow += windowSizeIncrement;
//                    okhttp3.internal.http2.Http2Connection.this.notifyAll();
//                }
//            } else {
//                Http2Stream stream = getStream(streamId);
//                if (stream != null) {
//                    synchronized (stream) {
//                        stream.addBytesToWriteWindow(windowSizeIncrement);
//                    }
//                }
//            }
//        }
//
//        @Override public void priority(int streamId, int streamDependency, int weight,
//                                       boolean exclusive) {
//            // TODO: honor priority.
//        }
//
//        @Override
//        public void pushPromise(int streamId, int promisedStreamId, List<Header> requestHeaders) {
//            pushRequestLater(promisedStreamId, requestHeaders);
//        }
//
//        @Override public void alternateService(int streamId, String origin, ByteString protocol,
//                                               String host, int port, long maxAge) {
//            // TODO: register alternate service.
//        }
//    }
//
//    /** Even, positive numbered streams are pushed streams in HTTP/2. */
//    boolean pushedStream(int streamId) {
//        return streamId != 0 && (streamId & 1) == 0;
//    }
//
//    // Guarded by this.
//    final Set<Integer> currentPushRequests = new LinkedHashSet<>();
//
//    void pushRequestLater(final int streamId, final List<Header> requestHeaders) {
//        synchronized (this) {
//            if (currentPushRequests.contains(streamId)) {
//                writeSynResetLater(streamId, ErrorCode.PROTOCOL_ERROR);
//                return;
//            }
//            currentPushRequests.add(streamId);
//        }
//        try {
//            pushExecutorExecute(new NamedRunnable("OkHttp %s Push Request[%s]", hostname, streamId) {
//                @Override public void execute() {
//                    boolean cancel = pushObserver.onRequest(streamId, requestHeaders);
//                    try {
//                        if (cancel) {
//                            writer.rstStream(streamId, ErrorCode.CANCEL);
//                            synchronized (okhttp3.internal.http2.Http2Connection.this) {
//                                currentPushRequests.remove(streamId);
//                            }
//                        }
//                    } catch (IOException ignored) {
//                    }
//                }
//            });
//        } catch (RejectedExecutionException ignored) {
//            // This connection has been closed.
//        }
//    }
//
//    void pushHeadersLater(final int streamId, final List<Header> requestHeaders,
//                          final boolean inFinished) {
//        try {
//            pushExecutorExecute(new NamedRunnable("OkHttp %s Push Headers[%s]", hostname, streamId) {
//                @Override public void execute() {
//                    boolean cancel = pushObserver.onHeaders(streamId, requestHeaders, inFinished);
//                    try {
//                        if (cancel) writer.rstStream(streamId, ErrorCode.CANCEL);
//                        if (cancel || inFinished) {
//                            synchronized (okhttp3.internal.http2.Http2Connection.this) {
//                                currentPushRequests.remove(streamId);
//                            }
//                        }
//                    } catch (IOException ignored) {
//                    }
//                }
//            });
//        } catch (RejectedExecutionException ignored) {
//            // This connection has been closed.
//        }
//    }
//
//    /**
//     * Eagerly reads {@code byteCount} bytes from the source before launching a background task to
//     * process the data.  This avoids corrupting the stream.
//     */
//    void pushDataLater(final int streamId, final BufferedSource source, final int byteCount,
//                       final boolean inFinished) throws IOException {
//        final Buffer buffer = new Buffer();
//        source.require(byteCount); // Eagerly read the frame before firing client thread.
//        source.read(buffer, byteCount);
//        if (buffer.size() != byteCount) throw new IOException(buffer.size() + " != " + byteCount);
//        pushExecutorExecute(new NamedRunnable("OkHttp %s Push Data[%s]", hostname, streamId) {
//            @Override public void execute() {
//                try {
//                    boolean cancel = pushObserver.onData(streamId, buffer, byteCount, inFinished);
//                    if (cancel) writer.rstStream(streamId, ErrorCode.CANCEL);
//                    if (cancel || inFinished) {
//                        synchronized (Http2Connection.this) {
//                            currentPushRequests.remove(streamId);
//                        }
//                    }
//                } catch (IOException ignored) {
//                }
//            }
//        });
//    }
//
//    void pushResetLater(final int streamId, final ErrorCode errorCode) {
//        pushExecutorExecute(new NamedRunnable("OkHttp %s Push Reset[%s]", hostname, streamId) {
//            @Override public void execute() {
//                pushObserver.onReset(streamId, errorCode);
//                synchronized (okhttp3.internal.http2.Http2Connection.this) {
//                    currentPushRequests.remove(streamId);
//                }
//            }
//        });
//    }
//
//    private synchronized void pushExecutorExecute(NamedRunnable namedRunnable) {
//        if (!isShutdown()) {
//            pushExecutor.execute(namedRunnable);
//        }
//    }
//
//    /** Listener of streams and settings initiated by the peer. */
//    public abstract static class Listener {
//        public static final okhttp3.internal.http2.Http2Connection.Listener REFUSE_INCOMING_STREAMS = new okhttp3.internal.http2.Http2Connection.Listener() {
//            @Override public void onStream(Http2Stream stream) throws IOException {
//                stream.close(REFUSED_STREAM);
//            }
//        };
//
//        /**
//         * Handle a new stream from this connection's peer. Implementations should respond by either
//         * {@linkplain Http2Stream#writeHeaders replying to the stream} or {@linkplain
//         * Http2Stream#close closing it}. This response does not need to be synchronous.
//         */
//        public abstract void onStream(Http2Stream stream) throws IOException;
//
//        /**
//         * Notification that the connection's peer's settings may have changed. Implementations should
//         * take appropriate action to handle the updated settings.
//         *
//         * <p>It is the implementation's responsibility to handle concurrent calls to this method. A
//         * remote peer that sends multiple settings frames will trigger multiple calls to this method,
//         * and those calls are not necessarily serialized.
//         */
//        public void onSettings(okhttp3.internal.http2.Http2Connection connection) {
//        }
//    }
}
