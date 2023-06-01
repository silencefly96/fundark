package com.silencefly96.module_third.okhttp3.connection;

import java.io.IOException;
import java.lang.ref.Reference;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownServiceException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import okhttp3.Address;
import okhttp3.Call;
import okhttp3.CertificatePinner;
import okhttp3.Connection;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.internal.Internal;
import okhttp3.internal.Util;
import okhttp3.internal.Version;
import okhttp3.internal.connection.ConnectionSpecSelector;
import okhttp3.internal.connection.RouteException;
import okhttp3.internal.connection.StreamAllocation;
import okhttp3.internal.http.HttpCodec;
import okhttp3.internal.http.HttpHeaders;
import okhttp3.internal.http1.Http1Codec;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.Http2Codec;
import okhttp3.internal.http2.Http2Connection;
import okhttp3.internal.http2.Http2Stream;
import okhttp3.internal.platform.Platform;
import okhttp3.internal.tls.OkHostnameVerifier;
import okhttp3.internal.ws.RealWebSocket;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PROXY_AUTH;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static okhttp3.internal.Util.closeQuietly;

import androidx.annotation.Nullable;

/**
 * 连接
 *
 * 主要功能：
 * 1，在ConnectInterceptor调用streamAllocation.newStream时创建(findConnection)，来自连接池connectionPool
 *
 */
public final class RealConnection1 extends Http2Connection.Listener implements Connection {
    private static final String NPE_THROW_WITH_NULL = "throw with null exception";
    private static final int MAX_TUNNEL_ATTEMPTS = 21;

    private final ConnectionPool connectionPool;
    private final Route route;

    // The fields below are initialized by connect() and never reassigned.

    /** The low-level TCP socket. */
    private Socket rawSocket;

    /**
     * The application layer socket. Either an {@link SSLSocket} layered over {@link #rawSocket}, or
     * {@link #rawSocket} itself if this connection does not use SSL.
     */
    private Socket socket;
    private Handshake handshake;
    private Protocol protocol;
    private Http2Connection http2Connection;
    private BufferedSource source;
    private BufferedSink sink;

    // The fields below track connection state and are guarded by connectionPool.

    /** If true, no new streams can be created on this connection. Once true this is always true. */
    public boolean noNewStreams;

    public int successCount;

    /**
     * The maximum number of concurrent streams that can be carried by this connection. If {@code
     * allocations.size() < allocationLimit} then new streams can be created on this connection.
     */
    public int allocationLimit = 1;

    /** Current streams carried by this connection. */
    public final List<Reference<StreamAllocation1>> allocations = new ArrayList<>();

    /** Nanotime timestamp when {@code allocations.size()} reached zero. */
    public long idleAtNanos = Long.MAX_VALUE;

    public RealConnection1(ConnectionPool connectionPool, Route route) {
        this.connectionPool = connectionPool;
        this.route = route;
    }

    public static RealConnection1 testConnection(
            ConnectionPool connectionPool, Route route, Socket socket, long idleAtNanos) {
        RealConnection1 result = new RealConnection1(connectionPool, route);
        result.socket = socket;
        result.idleAtNanos = idleAtNanos;
        return result;
    }

    public void connect(int connectTimeout, int readTimeout, int writeTimeout,
                        int pingIntervalMillis, boolean connectionRetryEnabled, Call call,
                        EventListener eventListener) {
        if (protocol != null) throw new IllegalStateException("already connected");

        // 第一步，验证route参数
        RouteException routeException = null;
        List<ConnectionSpec> connectionSpecs = route.address().connectionSpecs();
        ConnectionSpecSelector connectionSpecSelector = new ConnectionSpecSelector(connectionSpecs);

        // sslSocketFactory: null 的时候不是 HTTPS address
        if (route.address().sslSocketFactory() == null) {
            // CLEARTEXT: 不加密、不认证的http url
            if (!connectionSpecs.contains(ConnectionSpec.CLEARTEXT)) {
                throw new RouteException(new UnknownServiceException(
                        "CLEARTEXT communication not enabled for client"));
            }
            String host = route.address().url().host();
            // 是否允许CLEARTEXT
            if (!Platform.get().isCleartextTrafficPermitted(host)) {
                throw new RouteException(new UnknownServiceException(
                        "CLEARTEXT communication to " + host + " not permitted by network security policy"));
            }
        } else {
            // HTTPS链接：CLEARTEXT需要H2_PRIOR_KNOWLEDGE，但HTTPS不需要这个
            if (route.address().protocols().contains(Protocol.H2_PRIOR_KNOWLEDGE)) {
                throw new RouteException(new UnknownServiceException(
                        "H2_PRIOR_KNOWLEDGE cannot be used with HTTPS"));
            }
        }

        // 第二步，在循环中进行连接
        while (true) {
            try {
                // 是否需要隧道: Returns true if this route tunnels HTTPS through an HTTP proxy
                // 如果此路由通过 HTTP 代理隧道传输 HTTPS，则返回 true
                if (route.requiresTunnel()) {
                    connectTunnel(connectTimeout, readTimeout, writeTimeout, call, eventListener);
                    if (rawSocket == null) {
                        // We were unable to connect the tunnel but properly closed down our resources.
                        break;
                    }
                } else {
                    // 根据route信息创建socket连接，获得输入输出流
                    connectSocket(connectTimeout, readTimeout, call, eventListener);
                }
                // 根据协议进行连接，HTTP直接返回，HTTPS使用http2Connection去处理，完全HTTPS还有Tls
                establishProtocol(connectionSpecSelector, pingIntervalMillis, call, eventListener);
                eventListener.connectEnd(call, route.socketAddress(), route.proxy(), protocol);
                break;
            } catch (IOException e) {
                closeQuietly(socket);
                closeQuietly(rawSocket);
                socket = null;
                rawSocket = null;
                source = null;
                sink = null;
                handshake = null;
                protocol = null;
                http2Connection = null;

                eventListener.connectFailed(call, route.socketAddress(), route.proxy(), null, e);

                if (routeException == null) {
                    routeException = new RouteException(e);
                } else {
                    routeException.addConnectException(e);
                }

                if (!connectionRetryEnabled || !connectionSpecSelector.connectionFailed(e)) {
                    throw routeException;
                }
            }
        }

        if (route.requiresTunnel() && rawSocket == null) {
            ProtocolException exception = new ProtocolException("Too many tunnel connections attempted: "
                    + MAX_TUNNEL_ATTEMPTS);
            throw new RouteException(exception);
        }

        if (http2Connection != null) {
            synchronized (connectionPool) {
                allocationLimit = http2Connection.maxConcurrentStreams();
            }
        }
    }

    /**
     * Does all the work to build an HTTPS connection over a proxy tunnel. The catch here is that a
     * proxy server can issue an auth challenge and then close the connection.
     */
    private void connectTunnel(int connectTimeout, int readTimeout, int writeTimeout, Call call,
                               EventListener eventListener) throws IOException {
        // 创建一个request
        Request tunnelRequest = createTunnelRequest();
        HttpUrl url = tunnelRequest.url();
        // 是做MAX_TUNNEL_ATTEMPTS次尝试创建Tunnel吗？
        for (int i = 0; i < MAX_TUNNEL_ATTEMPTS; i++) {
            // 根据route信息创建socket连接，获得输入输出流
            connectSocket(connectTimeout, readTimeout, call, eventListener);
            // 通过HTTP代理创建HTTPS连接，发送一次请求，正常情况返回null
            tunnelRequest = createTunnel(readTimeout, writeTimeout, tunnelRequest, url);

            // 正常情况(HTTP_OK)返回null
            if (tunnelRequest == null) break; // Tunnel successfully created.

            // The proxy decided to close the connection after an auth challenge. We need to create a new
            // connection, but this time with the auth credentials.
            closeQuietly(rawSocket);
            rawSocket = null;
            sink = null;
            source = null;
            eventListener.connectEnd(call, route.socketAddress(), route.proxy(), null);
        }
    }

    /** Does all the work necessary to build a full HTTP or HTTPS connection on a raw socket. */
    private void connectSocket(int connectTimeout, int readTimeout, Call call,
                               EventListener eventListener) throws IOException {
        Proxy proxy = route.proxy();
        Address address = route.address();

        rawSocket = proxy.type() == Proxy.Type.DIRECT || proxy.type() == Proxy.Type.HTTP
                ? address.socketFactory().createSocket()
                : new Socket(proxy);

        eventListener.connectStart(call, route.socketAddress(), proxy);
        rawSocket.setSoTimeout(readTimeout);
        try {
            // 调用socket的connect
            Platform.get().connectSocket(rawSocket, route.socketAddress(), connectTimeout);
        } catch (ConnectException e) {
            ConnectException ce = new ConnectException("Failed to connect to " + route.socketAddress());
            ce.initCause(e);
            throw ce;
        }

        // The following try/catch block is a pseudo hacky way to get around a crash on Android 7.0
        // More details:
        // https://github.com/square/okhttp/issues/3245
        // https://android-review.googlesource.com/#/c/271775/
        try {
            // 获得原始socket的输入输出流
            source = Okio.buffer(Okio.source(rawSocket));
            sink = Okio.buffer(Okio.sink(rawSocket));
        } catch (NullPointerException npe) {
            if (NPE_THROW_WITH_NULL.equals(npe.getMessage())) {
                throw new IOException(npe);
            }
        }
    }

    private void establishProtocol(ConnectionSpecSelector connectionSpecSelector,
                                   int pingIntervalMillis, Call call, EventListener eventListener) throws IOException {
        // 为null的时候为HTTP连接
        if (route.address().sslSocketFactory() == null) {
            // 通过HTTP运行的HTTPS
            if (route.address().protocols().contains(Protocol.H2_PRIOR_KNOWLEDGE)) {
                socket = rawSocket;
                protocol = Protocol.H2_PRIOR_KNOWLEDGE;
                // 上面设置好socket、协议，用http2Connection去处理
                startHttp2(pingIntervalMillis);
                return;
            }

            // HTTP协议使用rawSocket就可以了，并且到这就连接结束了
            socket = rawSocket;
            protocol = Protocol.HTTP_1_1;
            return;
        }

        eventListener.secureConnectStart(call);
        // 进行TLS连接，sslSocket会代替原始socket，输入输出流会更新
        // HTTPS是运行在SSL\TLS协议之上的，HTTP2使用了SSL\TLS协议，实际就是HTTPS
        connectTls(connectionSpecSelector);
        eventListener.secureConnectEnd(call, handshake);

        if (protocol == Protocol.HTTP_2) {
            // 使用Http2Connection进行处理
            startHttp2(pingIntervalMillis);
        }
    }

    private void startHttp2(int pingIntervalMillis) throws IOException {
        // HTTP/2 连接超时是按流设置的，指后面再设置吗？
        socket.setSoTimeout(0); // HTTP/2 connection timeouts are set per-stream.
        http2Connection = new Http2Connection.Builder(true)
                .socket(socket, route.address().url().host(), source, sink)
                // onStream和onSetting两个方法
                .listener(this)
                .pingIntervalMillis(pingIntervalMillis)
                .build();
        http2Connection.start();
    }

    private void connectTls(ConnectionSpecSelector connectionSpecSelector) throws IOException {
        // Address在RetryAndFollowUpInterceptor中创建
        // 创建Address的时候如果是HTTPS，传入了okhttpClient的sslSocketFactory、hostnameVerifier、certificatePinner
        Address address = route.address();
        SSLSocketFactory sslSocketFactory = address.sslSocketFactory();
        boolean success = false;
        SSLSocket sslSocket = null;
        try {
            // 封装原始socket，TLS是ssl的升级版，端口号443，运行在TCP/IP协议之上
            // Create the wrapper over the connected socket.
            sslSocket = (SSLSocket) sslSocketFactory.createSocket(
                    rawSocket, address.url().host(), address.url().port(), true /* autoClose */);

            // 设置TLS的密码、版本、扩展等，并会返回参数信息
            // Configure the socket's ciphers, TLS versions, and extensions.
            ConnectionSpec connectionSpec = connectionSpecSelector.configureSecureSocket(sslSocket);
            if (connectionSpec.supportsTlsExtensions()) {
                Platform.get().configureTlsExtensions(
                        sslSocket, address.url().host(), address.protocols());
            }

            // Force handshake. This can throw!
            sslSocket.startHandshake();
            // block for session establishment
            SSLSession sslSocketSession = sslSocket.getSession();
            // 保存了local和server的证书，tls版本，加密套件(密钥交换算法、身份验证算法 、对称加密算法和信息摘要算法)
            Handshake unverifiedHandshake = Handshake.get(sslSocketSession);

            // 对获取到的TLS证书进行验证：IP或域名
            // Verify that the socket's certificates are acceptable for the target host.
            if (!address.hostnameVerifier().verify(address.url().host(), sslSocketSession)) {
                X509Certificate cert = (X509Certificate) unverifiedHandshake.peerCertificates().get(0);
                throw new SSLPeerUnverifiedException("Hostname " + address.url().host() + " not verified:"
                        + "\n    certificate: " + CertificatePinner.pin(cert)
                        + "\n    DN: " + cert.getSubjectDN().getName()
                        + "\n    subjectAltNames: " + OkHostnameVerifier.allSubjectAltNames(cert));
            }

            // 通过CertificatePinner约束哪些证书是可信的(没看懂。。)，应该是在okhttpClient创建时传入的
            // Check that the certificate pinner is satisfied by the certificates presented.
            address.certificatePinner().check(address.url().host(),
                    unverifiedHandshake.peerCertificates());

            // 支持TLS扩展，所以协议不一样？
            // Success! Save the handshake and the ALPN protocol.
            String maybeProtocol = connectionSpec.supportsTlsExtensions()
                    ? Platform.get().getSelectedProtocol(sslSocket)
                    : null;

            // 更新socket以及输入输出流
            socket = sslSocket;
            source = Okio.buffer(Okio.source(socket));
            sink = Okio.buffer(Okio.sink(socket));

            // 保存相关信息
            handshake = unverifiedHandshake;
            protocol = maybeProtocol != null
                    ? Protocol.get(maybeProtocol)
                    : Protocol.HTTP_1_1;
            success = true;
        } catch (AssertionError e) {
            if (Util.isAndroidGetsocknameError(e)) throw new IOException(e);
            throw e;
        } finally {
            if (sslSocket != null) {
                // 关闭因为设置 configureTlsExtensions 时申请的资源
                Platform.get().afterHandshake(sslSocket);
            }
            if (!success) {
                closeQuietly(sslSocket);
            }
        }
    }

    /**
     * To make an HTTPS connection over an HTTP proxy, send an unencrypted CONNECT request to create
     * the proxy connection. This may need to be retried if the proxy requires authorization.
     */
    private Request createTunnel(int readTimeout, int writeTimeout, Request tunnelRequest,
                                 HttpUrl url) throws IOException {
        // Make an SSL Tunnel on the first message pair of each SSL + proxy connection.
        String requestLine = "CONNECT " + Util.hostHeader(url, true) + " HTTP/1.1";
        while (true) {
            // Http1Codec用来操作socket输入输出流，createTunnel前面已经connectSocket了
            // 关键是Http1Codec吗？所以用的是Http，SSL放在Header内吗？
            Http1Codec tunnelConnection = new Http1Codec(null, null, source, sink);
            source.timeout().timeout(readTimeout, MILLISECONDS);
            sink.timeout().timeout(writeTimeout, MILLISECONDS);

            // 向输出流写入header，finishRequest发送
            tunnelConnection.writeRequest(tunnelRequest.headers(), requestLine);
            tunnelConnection.finishRequest();

            // 读取返回流的header
            Response response = tunnelConnection.readResponseHeaders(false)
                    .request(tunnelRequest)
                    .build();

            // The response body from a CONNECT should be empty, but if it is not then we should consume
            // it before proceeding.
            long contentLength = HttpHeaders.contentLength(response);
            if (contentLength == -1L) {
                contentLength = 0L;
            }
            // 这里要求response body为empty，如果不是就跳过内容(到达exhausted)，或者deadline到达
            Source body = tunnelConnection.newFixedLengthSource(contentLength);
            Util.skipAll(body, Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
            body.close();

            switch (response.code()) {
                case HTTP_OK:
                    // Assume the server won't send a TLS ServerHello until we send a TLS ClientHello. If
                    // that happens, then we will have buffered bytes that are needed by the SSLSocket!
                    // This check is imperfect: it doesn't tell us whether a handshake will succeed, just
                    // that it will almost certainly fail because the proxy has sent unexpected data.
                    if (!source.buffer().exhausted() || !sink.buffer().exhausted()) {
                        throw new IOException("TLS tunnel buffered too many bytes!");
                    }
                    return null;

                case HTTP_PROXY_AUTH:
                    // 代理需要认证认证，修改tunnelRequest进入下一个循环
                    tunnelRequest = route.address().proxyAuthenticator().authenticate(route, response);
                    if (tunnelRequest == null) throw new IOException("Failed to authenticate with proxy");

                    // response告知连接结束，直接返回tunnelRequest,上一步中会做关闭流等操作
                    if ("close".equalsIgnoreCase(response.header("Connection"))) {
                        return tunnelRequest;
                    }
                    break;

                default:
                    throw new IOException(
                            "Unexpected response code for CONNECT: " + response.code());
            }
        }
    }

    /**
     * Returns a request that creates a TLS tunnel via an HTTP proxy. Everything in the tunnel request
     * is sent unencrypted to the proxy server, so tunnels include only the minimum set of headers.
     * This avoids sending potentially sensitive data like HTTP cookies to the proxy unencrypted.
     *
     * <p>In order to support preemptive authentication we pass a fake “Auth Failed” response to the
     * authenticator. This gives the authenticator the option to customize the CONNECT request. It can
     * decline to do so by returning null, in which case OkHttp will use it as-is
     */
    private Request createTunnelRequest() throws IOException {
        // 对代理服务器的request(TLS tunnel)，request是未加密的，不要发敏感数据(cookies)
        Request proxyConnectRequest = new Request.Builder()
                .url(route.address().url())
                .method("CONNECT", null)
                .header("Host", Util.hostHeader(route.address().url(), true))
                .header("Proxy-Connection", "Keep-Alive") // For HTTP/1.0 proxies like Squid.
                .header("User-Agent", Version.userAgent())
                .build();

        // 为了支持先发的认证(preemptive authentication)，创建一个假的“Auth Failed” response给authenticator
        Response fakeAuthChallengeResponse = new Response.Builder()
                .request(proxyConnectRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(HttpURLConnection.HTTP_PROXY_AUTH)
                .message("Preemptive Authenticate")
                .body(Util.EMPTY_RESPONSE)
                .sentRequestAtMillis(-1L)
                .receivedResponseAtMillis(-1L)
                .header("Proxy-Authenticate", "OkHttp-Preemptive")
                .build();

        // authenticatedRequest=null 则不需要先发的认证
        Request authenticatedRequest = route.address().proxyAuthenticator()
                .authenticate(route, fakeAuthChallengeResponse);

        return authenticatedRequest != null
                ? authenticatedRequest
                : proxyConnectRequest;
    }

    /**
     * Returns true if this connection can carry a stream allocation to {@code address}. If non-null
     * {@code route} is the resolved route for a connection.
     */
    public boolean isEligible(Address address, @Nullable Route route) {
        // If this connection is not accepting new streams, we're done.
        if (allocations.size() >= allocationLimit || noNewStreams) return false;

        // If the non-host fields of the address don't overlap, we're done.
        if (!Internal.instance.equalsNonHost(this.route.address(), address)) return false;

        // If the host exactly matches, we're done: this connection can carry the address.
        if (address.url().host().equals(this.route().address().url().host())) {
            return true; // This connection is a perfect match.
        }

        // At this point we don't have a hostname match. But we still be able to carry the request if
        // our connection coalescing requirements are met. See also:
        // https://hpbn.co/optimizing-application-delivery/#eliminate-domain-sharding
        // https://daniel.haxx.se/blog/2016/08/18/http2-connection-coalescing/

        // 1. This connection must be HTTP/2.
        if (http2Connection == null) return false;

        // 2. The routes must share an IP address. This requires us to have a DNS address for both
        // hosts, which only happens after route planning. We can't coalesce connections that use a
        // proxy, since proxies don't tell us the origin server's IP address.
        if (route == null) return false;
        if (route.proxy().type() != Proxy.Type.DIRECT) return false;
        if (this.route.proxy().type() != Proxy.Type.DIRECT) return false;
        if (!this.route.socketAddress().equals(route.socketAddress())) return false;

        // 3. This connection's server certificate's must cover the new host.
        if (route.address().hostnameVerifier() != OkHostnameVerifier.INSTANCE) return false;
        if (!supportsUrl(address.url())) return false;

        // 4. Certificate pinning must match the host.
        try {
            address.certificatePinner().check(address.url().host(), handshake().peerCertificates());
        } catch (SSLPeerUnverifiedException e) {
            return false;
        }

        return true; // The caller's address can be carried by this connection.
    }

    public boolean supportsUrl(HttpUrl url) {
        if (url.port() != route.address().url().port()) {
            return false; // Port mismatch.
        }

        if (!url.host().equals(route.address().url().host())) {
            // We have a host mismatch. But if the certificate matches, we're still good.
            return handshake != null && OkHostnameVerifier.INSTANCE.verify(
                    url.host(), (X509Certificate) handshake.peerCertificates().get(0));
        }

        return true; // Success. The URL is supported.
    }

    public HttpCodec newCodec(OkHttpClient client, Interceptor.Chain chain,
                              StreamAllocation streamAllocation1) throws SocketException {
        if (http2Connection != null) {
            return new Http2Codec(client, chain, streamAllocation1, http2Connection);
        } else {
            socket.setSoTimeout(chain.readTimeoutMillis());
            source.timeout().timeout(chain.readTimeoutMillis(), MILLISECONDS);
            sink.timeout().timeout(chain.writeTimeoutMillis(), MILLISECONDS);
            return new Http1Codec(client, streamAllocation1, source, sink);
        }
    }

    public RealWebSocket.Streams newWebSocketStreams(final StreamAllocation streamAllocation) {
        return new RealWebSocket.Streams(true, source, sink) {
            @Override public void close() throws IOException {
                streamAllocation.streamFinished(true, streamAllocation.codec(), -1L, null);
            }
        };
    }

    @Override public Route route() {
        return route;
    }

    public void cancel() {
        // Close the raw socket so we don't end up doing synchronous I/O.
        closeQuietly(rawSocket);
    }

    @Override public Socket socket() {
        return socket;
    }

    /** Returns true if this connection is ready to host new streams. */
    public boolean isHealthy(boolean doExtensiveChecks) {
        if (socket.isClosed() || socket.isInputShutdown() || socket.isOutputShutdown()) {
            return false;
        }

        if (http2Connection != null) {
            return !http2Connection.isShutdown();
        }

        if (doExtensiveChecks) {
            try {
                int readTimeout = socket.getSoTimeout();
                try {
                    socket.setSoTimeout(1);
                    if (source.exhausted()) {
                        return false; // Stream is exhausted; socket is closed.
                    }
                    return true;
                } finally {
                    socket.setSoTimeout(readTimeout);
                }
            } catch (SocketTimeoutException ignored) {
                // Read timed out; socket is good.
            } catch (IOException e) {
                return false; // Couldn't read; socket is closed.
            }
        }

        return true;
    }

    /** Refuse incoming streams. */
    @Override public void onStream(Http2Stream stream) throws IOException {
        stream.close(ErrorCode.REFUSED_STREAM);
    }

    /** When settings are received, adjust the allocation limit. */
    @Override public void onSettings(Http2Connection connection) {
        synchronized (connectionPool) {
            allocationLimit = connection.maxConcurrentStreams();
        }
    }

    @Override public Handshake handshake() {
        return handshake;
    }

    /**
     * Returns true if this is an HTTP/2 connection. Such connections can be used in multiple HTTP
     * requests simultaneously.
     */
    public boolean isMultiplexed() {
        return http2Connection != null;
    }

    @Override public Protocol protocol() {
        return protocol;
    }

    @Override public String toString() {
        return "Connection{"
                + route.address().url().host() + ":" + route.address().url().port()
                + ", proxy="
                + route.proxy()
                + " hostAddress="
                + route.socketAddress()
                + " cipherSuite="
                + (handshake != null ? handshake.cipherSuite() : "none")
                + " protocol="
                + protocol
                + '}';
    }
}
