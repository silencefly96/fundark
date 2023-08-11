# Cordova-Android源码解析

## 前言
最近有时间把公司Android代码中的Cordova源码看了一遍，虽然这个框架很老了，也没什么人用了(公司的版本还是6.1.2，看的就是这个)，不过还是挺有价值的。

Cordova这个框架用的是Android提供的API进行混合开发，对于去了解Android与JS交互方式、交互管理、WebView相关概念很有帮助，东西虽老，但是能学到很多。

ps. 文章有点长了，可以根据目录慢慢看哈。

## 启动流程
CordovaActivity继承了Activity，混合应用的Activity应该继承它，并调用调用loadUrl(url)方法。

### 读取config配置
在CordovaActivity的onCreate方法中调用了loadConfig()，进行config.xml的加载:
```
    protected void loadConfig() {
        // 处理config.xml
        ConfigXmlParser parser = new ConfigXmlParser();
        parser.parse(this);
        
        // 获得参数
        preferences = parser.getPreferences();
        preferences.setPreferencesBundle(getIntent().getExtras());
        launchUrl = parser.getLaunchUrl();
        pluginEntries = parser.getPluginEntries();
        Config.parser = parser;
    }
```
XML读取的核心代码如下:
```
    public void handleStartTag(XmlPullParser xml) {
        String strNode = xml.getName();
        
        // feature标签表示的是config.xml中的插件
        if (strNode.equals("feature")) {
            insideFeature = true;
            service = xml.getAttributeValue(null, "name");
        }
        // ...  其他属性
    }
```
在loadConfig()获得config.xml中的配置并读取到CordovaActivity之后，在CordovaActivity的onCreate中，将运用这些参数，例如设置title、Fullscreen等:
```
    if (!preferences.getBoolean("ShowTitle", false)) {
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
    }
```

### 创建CordovaInterface
在CordovaActivity的onCreate方法最后，还创建了CordovaInterface，这个CordovaInterface应该是类似context，提供Cordova的上下文环境:
```
    // 创建CordovaInterface的实例
    cordovaInterface = makeCordovaInterface();
    if (savedInstanceState != null) {
        cordovaInterface.restoreInstanceState(savedInstanceState);
    }
    
    // CordovaInterface的一个示例，读取savedInstanceState中的数据，并设置
    // CordovaInterfaceImpl中
    public void restoreInstanceState(Bundle savedInstanceState) {
        initCallbackService = savedInstanceState.getString("callbackService");
        savedPluginState = savedInstanceState.getBundle("plugin");
        activityWasDestroyed = true;
    }
```
实际这个创建函数只是返回了一个CordovaInterfaceImpl对象，CordovaInterfaceImpl是CordovaInterface的实现类，这里重写了它的onMessage方法，并传递消息到CordovaActivity:
```
    protected CordovaInterfaceImpl makeCordovaInterface() {
        return new CordovaInterfaceImpl(this) {
            @Override
            public Object onMessage(String id, Object data) {
                // Plumb this to CordovaActivity.onMessage for backwards compatibility
                return CordovaActivity.this.onMessage(id, data);
            }
        };
    }
```
这个onMessage，我看了下源码，应该是调用PluginManager的postMessage，给所有插件发送消息后，插件都没返回，最终到达CordovaInterface的onMessage才处理(类似点击事件处理？):
```
    // Called when a message is sent to plugin. 当消息发送到插件时调用
    public Object onMessage(String id, Object data) {
        // 处理消息
        if ("onReceivedError".equals(id)) {
            JSONObject d = (JSONObject) data;
            try {
                this.onReceivedError(d.getInt("errorCode"), d.getString("description"), d.getString("url"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if ("exit".equals(id)) {
            finish();
        }
        return null;
    }
```
怎么说，我看应该就是一种全局广播，先遍历插件，看看插件处不处理这个广播(Message)，插件可以通过返回任意object阻断这个广播(Message)的传递，如果所有插件都不阻断这个广播(Message)，那就最终由CordovaInterface去处理:
```
// 在各个地方调用postMessage的情况: 
appView.getPluginManager().postMessage("onCreateOptionsMenu", menu);

pluginManager.postMessage("onPageStarted", newUrl);

// Broadcast message that page has loaded
pluginManager.postMessage("onPageFinished", url);
pluginManager.postMessage("exit", null);

webView.getPluginManager().postMessage("telephone", "ringing");
```
### CordovaInterface简述
这里提到了CordovaInterface，也简单看下这个Cordova上下文的接口:
```
    // 打开activity
    void startActivityForResult(CordovaPlugin command, Intent intent, int requestCode);
    // 设置打开activity的result callback
    void setActivityResultCallback(CordovaPlugin plugin);
    
    // 获取当前activity(context)
    Activity getActivity();
    
    // 处理全局广播的消息
    Object onMessage(String id, Object data);
    
    // 提供的默认线程池，不应该自己再去创建
    ExecutorService getThreadPool();
    
    // 权限请求相关
    void requestPermission(CordovaPlugin plugin, int requestCode, String permission);
    void requestPermissions(CordovaPlugin plugin, int requestCode, String[] permissions);
    boolean hasPermission(String permission);
```
上面还是比较基础的方法，下面继续看下CordovaInterfaceImpl这个实现类，CordovaActivity持有的是CordovaInterfaceImpl(奇怪哦):
```
    public void onCordovaInit(PluginManager pluginManager)
    
    // 接收CordovaActivity的onActivityResult，并根据
    public boolean onActivityResult(int requestCode, int resultCode, Intent intent)
    
    // 接收CordovaActivity的startActivityForResult的requestCode，并暂存变量
    // 配合CordovaInterface的setActivityResultCallback使用
    public void setActivityResultRequestCode(int requestCode)
    
    // 异常数据恢复
    public void onSaveInstanceState(Bundle outState)
    public void restoreInstanceState(Bundle savedInstanceState)
    
    // 接收CordovaActivity的onRequestPermissionsResult，并根据requestCode交给合适的插件回调
    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException
```
除了onCordovaInit，应该都好理解，主要就是activity中请求和回复的一个接管，使之能够让插件去使用。onCordovaInit很有意思，我们看下代码:
```
    public void onCordovaInit(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
        
        // 在加载本地url的时候调用
        if (savedResult != null) {
            // 有onActivityResult的保存数据，直接回调
            onActivityResult(savedResult.requestCode, savedResult.resultCode, savedResult.intent);
        } else if(activityWasDestroyed) {
        
            // 被系统activity杀了，重新恢复时做下通知 => 生命周期不一样？
            activityWasDestroyed = false;
            if(pluginManager != null) {
            
                // CoreAndroid是Cordova提供给JS方法的类
                CoreAndroid appPlugin = (CoreAndroid) pluginManager.getPlugin(CoreAndroid.PLUGIN_NAME);
                if(appPlugin != null) {
                    
                    // 发送一个resume消息
                    JSONObject obj = new JSONObject();
                    try {
                        obj.put("action", "resume");
                    } catch (JSONException e) {
                        LOG.e(TAG, "Failed to create event message", e);
                    }
                    appPlugin.sendResumeEvent(new PluginResult(PluginResult.Status.OK, obj));
                }
            }
        }
    }
```
这里是一个数据的恢复好像，讲解前先看下onActivityResult内代码:
```
    public boolean onActivityResult(int requestCode, int resultCode, Intent intent) {
        CordovaPlugin callback = activityResultCallback;
        
        // activityResultCallback是设置接收onActivityResult的，如果为null就是activity重启了，未初始化为null
        // initCallbackService是设置初始化时处理onActivityResult的
        if(callback == null && initCallbackService != null) {
            
            // savedResult是在这里把onActivityResult结果封装起来的
            savedResult = new ActivityResultHolder(requestCode, resultCode, intent);
            if (pluginManager != null) {
                callback = pluginManager.getPlugin(initCallbackService);
                if(callback != null) {
                    
                    // 保存到initCallbackService
                    callback.onRestoreStateForActivityResult(savedPluginState.getBundle(callback.getServiceName()),
                            new ResumeCallback(callback.getServiceName(), pluginManager));
                }
            }
        }
        // 消耗本次的activityResultCallback
        activityResultCallback = null;
        
        // ... callback.onActivityResult 逻辑
    }    
```
结合上面两段代码，大致意思就是CordovaInterfaceImpl或者pluginManager的生命周期会比activity长，activity被系统回收并恢复的时候，这里会恢复中间接收的onActivityResult结果。

### 创建WebView
在CordovaActivity的onCreate方法结束后，需要在继承其的activity调用loadUrl方法，加载链接:
```
    public void loadUrl(String url) {
        if (appView == null) {
            init();
        }

        // If keepRunning
        this.keepRunning = preferences.getBoolean("KeepRunning", true);

        appView.loadUrlIntoView(url, true);
    }
```
点进去init方法看一下:
```
    protected void init() {
        // 创建WebView
        appView = makeWebView();
        
        // 设置appView的一些属性，并将appView设置为ContentView，请求焦点
        createViews();
        
        // 初始化WebView
        if (!appView.isInitialized()) {
            appView.init(cordovaInterface, pluginEntries, preferences);
        }
        
        // 初始化cordovaInterface
        cordovaInterface.onCordovaInit(appView.getPluginManager());

        // Wire the hardware volume controls to control media if desired.
        String volumePref = preferences.getString("DefaultVolumeStream", "");
        if ("media".equals(volumePref.toLowerCase(Locale.ENGLISH))) {
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
        }
    }
```
处理初始化初始化cordovaInterface和最后几行不知名代码外，都是和webView有关，createViews没什么好说的，我们着重看下makeWebView和appView.init:
```
    // CordovaWebView不是webview，是Cordova的一个交互接口，功能强大
    // Main interface for interacting with a Cordova webview 
    protected CordovaWebView makeWebView() {
        return new CordovaWebViewImpl(makeWebViewEngine());
    }

    // 实现类CordovaWebViewImpl，是Cordova和webView交互的接口
    // Glue class between CordovaWebView (main Cordova logic) and SystemWebView (the actual View).
    protected CordovaWebViewEngine makeWebViewEngine() {
        return CordovaWebViewImpl.createEngine(this, preferences);
    }
```
这里创建了两个对象，CordovaWebViewImpl和CordovaWebViewEngineImpl，关于这两个类后面再说，这里指导下它的大致功能就行了。

下面再来大致看下appView.init方法:
```
    @Override
    public void init(CordovaInterface cordova, List<PluginEntry> pluginEntries, CordovaPreferences preferences) {
        if (this.cordova != null) { throw new IllegalStateException(); }
        
        // 赋值
        this.cordova = cordova;
        this.preferences = preferences;
        
        // 注意: 这里终于创建PluginManager了
        pluginManager = new PluginManager(this, this.cordova, pluginEntries);
        
        // 创建了CordovaResourceApi，Cordova的资源管理API
        resourceApi = new CordovaResourceApi(engine.getView().getContext(), pluginManager);
        
         // 创建了NativeToJsMessageQueue，安卓和JS交互的桥
        nativeToJsMessageQueue = new NativeToJsMessageQueue();
        nativeToJsMessageQueue.addBridgeMode(new NativeToJsMessageQueue.NoOpBridgeMode());
        nativeToJsMessageQueue.addBridgeMode(new NativeToJsMessageQueue.LoadUrlBridgeMode(engine, cordova));

        if (preferences.getBoolean("DisallowOverscroll", false)) {
            engine.getView().setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
        
        // 初始化了CordovaWebViewEngine
        engine.init(this, cordova, engineClient, resourceApi, pluginManager, nativeToJsMessageQueue);
        
        // This isn't enforced by the compiler, so assert here.
        assert engine.getView() instanceof CordovaWebViewEngine.EngineView;

        // 添加了名为CoreAndroid的插件
        pluginManager.addService(CoreAndroid.PLUGIN_NAME, "org.apache.cordova.CoreAndroid");
        
        // 启动插件管理器
        pluginManager.init();
    }
```
这里东西很多了，PluginManager、CordovaResourceApi、NativeToJsMessageQueue纷纷露面，不过这里先讲流程，这几个核心的类后面单独详细说。

CordovaWebViewEngine和pluginManager也在这里完成了初始化，这个都是和流程相关的东西，暂时先记住。

回过头来继续看loadUrl方法，它最后还调用了appView的loadUrlIntoView方法，传入了url，这里也简单看下这个方法有什么用:
```
    @Override
    public void loadUrlIntoView(final String url, boolean recreatePlugins) {
        // 拦截url，"javascript:"开头的代码为Android调用JS的方法
        if (url.equals("about:blank") || url.startsWith("javascript:")) {
            // 可能要用cordova.getActivity().runOnUiThread处理下
            engine.loadUrl(url, false);
            return;
        }

        // 重新初始化插件
        recreatePlugins = recreatePlugins || (loadedUrl == null);
        if (recreatePlugins) {
            // Don't re-initialize on first load.
            if (loadedUrl != null) {
                appPlugin = null;
                pluginManager.init();
            }
            loadedUrl = url;
        }

        // Create a timeout timer for loadUrl
        final int currentLoadUrlTimeout = loadUrlTimeout;
        final int loadUrlTimeoutValue = preferences.getInteger("LoadUrlTimeoutValue", 20000);

        // 加载失败的处理
        final Runnable loadError = () -> {
            stopLoading();
            LOG.e(TAG, "CordovaWebView: TIMEOUT ERROR!");

            // Handle other errors by passing them to the webview in JS
            JSONObject data = new JSONObject();
            try {
                data.put("errorCode", -6);
                data.put("description", "The connection to the server was unsuccessful.");
                data.put("url", url);
            } catch (JSONException e) {
                // Will never happen.
            }
            
            // 发送全局消息
            pluginManager.postMessage("onReceivedError", data);
        }

        // 超时处理
        final Runnable timeoutCheck = () -> {
            try {
                synchronized (this) {
                    wait(loadUrlTimeoutValue);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // If timeout, then stop loading and handle error
            if (loadUrlTimeout == currentLoadUrlTimeout) {
                cordova.getActivity().runOnUiThread(loadError);
            }
        }

        final boolean _recreatePlugins = recreatePlugins;
        cordova.getActivity().runOnUiThread( () -> {
            if (loadUrlTimeoutValue > 0) {
                cordova.getThreadPool().execute(timeoutCheck);
            }
            // 最终通过CordovaWebViewEngine去加载网页
            engine.loadUrl(url, _recreatePlugins);
        });
    }
```
代码比较长，总结下就是用CordovaWebViewEngine去加载url或者js，中间加了超时和失败的处理，并且加载的时候可以配置_recreatePlugins，对插件进行重新初始化。

说的这整个流程就差不多了，CordovaActivity中还有一些生命周期函方法者其他方法会通过appView去处理，这个是CordovaWebView的功能，后面会提到。

## 插件管理
Cordova的插件是由PluginManager管理的，其配置信息放在config.xml的feature标签里面，下面就来探究下Cordova中的插件如何运行。

### 插件配置
这里只讲Android的插件使用，并不涉及Cordova如何安装插件之类的，其实Cordova的插件只要在config.xml里面定义，指定包名存在继承CordovaPlugin的插件就OK了。

下面看下Cordova中插件的配置，点开config.xml，其实很简单，就名称、包名、是否加载:
```
    <feature name="SplashScreen">
        <param name="android-package" value="com.cordova.utils.SplashScreen" />
        <param name="onload" value="true" />
```
接下来，只要在指定包名定义继承CordovaPlugin的类:
```
public class SplashScreen extends CordovaPlugin {
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        // 重写方法，根据action分辨调用的哪个方法
        switch(action) {
            // ...
        }
    }
}
```
H5中使用，要写清插件名、方法名(action)、参数(这里要注意下参数是一个数组):
```
var exec = require('cordova/exec');
exec(success, failed, "SplashScreen", 'xxxMethod', args);
```

### 插件基类
在看PluginManager前，我们还是要看下插件的基类CordovaPlugin，PluginManager的一个主要功能就是调用插件的方法执行功能。

CordovaPlugin比较长，我们分几部分去理解它。

#### 插件生命周期方法
```
public class CordovaPlugin {
    // 数据类
    public CordovaWebView webView;
    public CordovaInterface cordova;
    protected CordovaPreferences preferences;
    private String serviceName;
    
    // PluginManager中初始化CordovaPlugin时调用
    public final void privateInitialize(String serviceName, CordovaInterface cordova, CordovaWebView webView, CordovaPreferences preferences){
        assert this.cordova == null;
        this.serviceName = serviceName;
        this.cordova = cordova;
        this.webView = webView;
        this.preferences = preferences;
        
        initialize(cordova, webView);
        pluginInitialize();
    }
    
    // 供外面插件初始化生命周期感知
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {...}
    // initialize无参数版本
    protected void pluginInitialize() {...}
    public String getServiceName() { return serviceName; }
    
    // 三个执行方法，至少继承一个
    public boolean execute(String action, String rawArgs, CallbackContext callbackContext) throws JSONException {...}
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {...}
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {...}
}    
```
这里的生命周期就是指的插件的初始化了，插件初始化的时候可以实现一些功能，比如获取插件全局变量(uuid、sdkVersion)之类的:

#### 感知外界方法
```
// Activity生命周期感知方法
public void onPause(boolean multitasking)
public void onResume(boolean multitasking)
public void onStart()
public void onStop()
public void onNewIntent(Intent intent)
public void onDestroy()
public Bundle onSaveInstanceState()
public void onConfigurationChanged(Configuration newConfig)

// 感知其他调用的方法
public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext)
public Object onMessage(String id, Object data)
public void onReset()
public void onActivityResult(int requestCode, int resultCode, Intent intent)
// ...
```
这些都没什么好说的，大致看下就行，这里没全部列出来，毕竟不是代码大放送，有的方法还是很有用的。

#### 控制方法
```
// 拦截webView处理url的方法
public boolean shouldAllowRequest(String url)
public Boolean shouldAllowNavigation(String url)
public Boolean shouldAllowBridgeAccess(String url)
public Boolean shouldOpenExternalUrl(String url)
public boolean onOverrideUrlLoading(String url)
// HTTP证书处理
public boolean onReceivedHttpAuthRequest(CordovaWebView view, ICordovaHttpAuthHandler handler, String host, String realm)
public boolean onReceivedClientCertRequest(CordovaWebView view, ICordovaClientCertRequest request) 

// 其他一些控制方法: Uri、
public Uri remapUri(Uri uri)
public CordovaResourceApi.OpenForReadResult handleOpenForRead(Uri uri) throws IOException
// ...

// 权限相关
public void requestPermissions(int requestCode)
public boolean hasPermisssion()
public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException
```
这里才是我们插件的主角了，插件可以控制webView的一些行为，而不仅仅时为H5提供方法，就和我们设置WebViewClient一样，能够拦截url，插件的扩展性真就大大提高了。

### 插件管理
讲完CordovaPlugin，我们再来看PluginManager就简单了。PluginManager顾名思义就是对插件管理的类，上承H5，下接插件，是H5和Android交互的核心，是实现扩展性的灵魂。

#### Plugin初始化流程 
前面讲解Cordova流程时，已经提到PluginManager在appView.init方法中创建并初始化
```
    @Override
    public void init(CordovaInterface cordova, List<PluginEntry> pluginEntries, CordovaPreferences preferences) {
        // 省略其他代码
        pluginManager = new PluginManager(this, this.cordova, pluginEntries);
 
        // 添加了名为CoreAndroid的插件
        pluginManager.addService(CoreAndroid.PLUGIN_NAME, "org.apache.cordova.CoreAndroid");
        
        // 启动插件管理器
        pluginManager.init();
    }
```
这里看下构造方法:
```
    public PluginManager(CordovaWebView cordovaWebView, CordovaInterface cordova, Collection<PluginEntry> pluginEntries) {
        this.ctx = cordova;
        this.app = cordovaWebView;
        setPluginEntries(pluginEntries);
    }
    
    public void setPluginEntries(Collection<PluginEntry> pluginEntries) {
        // 这里需要pluginManager.init()之后
        if (isInitialized) {
            this.onPause(false);
            this.onDestroy();
            pluginMap.clear();
            entryMap.clear();
        }
        
        // 根据config.xml中读取的插件进行加载
        for (PluginEntry entry : pluginEntries) {
            addService(entry);
        }
        
        // 启动插件，这里需要pluginManager.init()之后
        if (isInitialized) {
            startupPlugins();
        }
    }
```
继续看下addService和startupPlugins两个方法:
```
    public void addService(PluginEntry entry) {
        // 先保存entry
        this.entryMap.put(entry.service, entry);
        
        // 注意这里一路追踪过去，ConfigXmlParser中并没有创建plugin，暂时时null
        if (entry.plugin != null) {
        
            // 初始化插件，并添加到插件map
            entry.plugin.privateInitialize(entry.service, ctx, app, app.getPreferences());
            pluginMap.put(entry.service, entry.plugin);
        }
    }
    
    private void startupPlugins() {
        // 遍历创建插件
        for (PluginEntry entry : entryMap.values()) {
        
            // config.xml中的onload，为true时才加载
            if (entry.onload) {
                getPlugin(entry.service);
            } else {
                pluginMap.put(entry.service, null);
            }
        }
    }
```
流程走到这里，getPlugin方法内部应该就是真正创建插件的地方了吧:
```
    public CordovaPlugin getPlugin(String service) {
        // 插件map中拿
        CordovaPlugin ret = pluginMap.get(service);
        if (ret == null) {
        
            // 插件入口map查
            PluginEntry pe = entryMap.get(service);
            if (pe == null) {
                return null;
            }
            if (pe.plugin != null) {
                ret = pe.plugin;
            } else {
                // 啰啰嗦嗦两步，最终才创建
                ret = instantiatePlugin(pe.pluginClass);
            }
            
            // 创建完插件(或者拿到已有插件)，即调用初始化方法
            if (ret != null) {
                ret.privateInitialize(service, ctx, app, app.getPreferences());
                pluginMap.put(service, ret);
            }

        }
        return ret;
    }
    
    // 反射创建插件实例
    private CordovaPlugin instantiatePlugin(String className) {
        CordovaPlugin ret = null;
        try {
            Class<?> c = null;
            if ((className != null) && !("".equals(className))) {
                c = Class.forName(className);
            }
            if (c != null & CordovaPlugin.class.isAssignableFrom(c)) {
                ret = (CordovaPlugin) c.newInstance();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error adding plugin " + className + ".");
        }
        return ret;
    }
```
到这反射创建插件实例就完成了，并且创建的插件已经保存到pluginMap中，但是PluginManager构造方法执行不到这里:
```
    // 需要 pluginManager.init();
    if (isInitialized) {
        startupPlugins();
    }
    
    public void init() {
        LOG.d(TAG, "init()");
        isInitialized = true;
        
        // 强制重走activity生命周期
        this.onPause(false);
        this.onDestroy();
        
        // 清空旧的插件列表
        pluginMap.clear();
        
        // 启动初始化插件
        this.startupPlugins();
    }
```
不过pluginManager.init()在PluginManager构造方法后执行(appView.init方法中)，大差不差。

到这里Cordova插件就全部创建并初始化了，H5可以通过调用插件的方法，执行相关功能，当然这个调用都是PluginManager控制的。

#### PluginManager中H5调用插件
既然插件时提供给H5的方法，那么H5如何调用到Android的呢？关键就在于它的exec方法:
```
    // service: 插件名、action插件: 方法、callbackId: 回调id、rawArgs: 原始参数
    public void exec(final String service, final String action, final String callbackId, final String rawArgs) {
        
        // 获取到对应插件
        CordovaPlugin plugin = getPlugin(service);
        if (plugin == null) {
        
            // 向对应回调id返回异常PluginResult
            LOG.d(TAG, "exec() call to unknown plugin: " + service);
            PluginResult cr = new PluginResult(PluginResult.Status.CLASS_NOT_FOUND_EXCEPTION);
            app.sendPluginResult(cr, callbackId);
            return;
        }
        
        // 创建CallbackContext向外暴漏，实现连续交互
        CallbackContext callbackContext = new CallbackContext(callbackId, app);
        
        try {
            long pluginStartTime = System.currentTimeMillis();
            boolean wasValidAction = plugin.execute(action, rawArgs, callbackContext);
            long duration = System.currentTimeMillis() - pluginStartTime;
            
            // 搞了个计时，插件的exec方法执行在主线程，如果耗时太长容易ANR
            if (duration > SLOW_EXEC_WARNING_THRESHOLD) {
                LOG.w(TAG, "THREAD WARNING: exec() call to " + service + "." + action + " blocked the main thread for " + duration + "ms. Plugin should use CordovaInterface.getThreadPool().");
            }
            
            // wasValidAction是插件是否消耗这个请求，返回false则未处理
            if (!wasValidAction) {
                PluginResult cr = new PluginResult(PluginResult.Status.INVALID_ACTION);
                callbackContext.sendPluginResult(cr);
            }
        } catch (JSONException e) {
            PluginResult cr = new PluginResult(PluginResult.Status.JSON_EXCEPTION);
            callbackContext.sendPluginResult(cr);
        } catch (Exception e) {
            LOG.e(TAG, "Uncaught exception from plugin", e);
            callbackContext.error(e.getMessage());
        }
    }
```
代码很好理解，就是拿到对应的插件执行对应的方法，最后通过CallbackContext进行回调交互。

不过这个方法从哪调用呢？ctrl + alt + 左键，点进去发现这个方法是CordovaBridge调用的，这个我们后面再单独聊，先看下CallbackContext如何实现回调交互的。
```
    // 构造
    public CallbackContext(String callbackId, CordovaWebView webView) {
        this.callbackId = callbackId;
        this.webView = webView;
    }
    
    // 其他success和error都是通过该方法实现
    public void sendPluginResult(PluginResult pluginResult) {
    
        // 加了对象锁，不管哪个线程，未设置KeepCallback时，第一次使用就结束了
        synchronized (this) {
            // pluginResult设置了pluginResult才能多次交互，否则一次就结束
            if (finished) {
                LOG.w(LOG_TAG, "Attempted to send a second callback for ID: " + callbackId + "\nResult was: " + pluginResult.getMessage());
                return;
            } else {
                finished = !pluginResult.getKeepCallback();
            }
        }
        // 通过webView传递pluginResult的，需要指定callbackId
        webView.sendPluginResult(pluginResult, callbackId);
    }
```
这里就是为什么很多人的回调老不触发的原因了，这里如果要多次通信，一定要设置pluginResult的KeepCallback为true。

点进webView的sendPluginResult方法，好像是通过nativeToJsMessageQueue去处理的，然而它好像有点复杂，后面单独讲吧。
```
    @Override
    public void sendPluginResult(PluginResult cr, String callbackId) {
        nativeToJsMessageQueue.addPluginResult(cr, callbackId);
    }
```

#### PluginManager逻辑
上面介绍了PluginManager中H5调用插件的逻辑，但是前面讲了CordovaPlugin还有其他好多功能，这些都是需要PluginManager协调的。

下面我们就调前面说过的onMessage方法和感知activity的destroy方法来举个例子，其他的CordovaPlugin方法都类似:
```
    public Object postMessage(String id, Object data) {
        for (CordovaPlugin plugin : this.pluginMap.values()) {
            if (plugin != null) {
                
                // 区别就是部分方法会对plugin的遍历操作拦截，让后面插件不执行
                Object obj = plugin.onMessage(id, data);
                if (obj != null) {
                    return obj;
                }
            }
        }
        return ctx.onMessage(id, data);
    }
    
    public void onDestroy() {
        for (CordovaPlugin plugin : this.pluginMap.values()) {
            if (plugin != null) {
                plugin.onDestroy();
            }
        }
    }
```
这些方法还比较简单，但是还记得在CordovaPlugin中介绍的方法分了几类吗？从接收activity回调的、控制webView的、uri的几个，就是说PluginManager类似一个中心，把这些功能聚在一起:

- 全局Message: postMessage
- Activity回调: 生命周期函数、权限、startActivity、ConfigurationChanged
- CordovaResourceApi: Uri相关
- CordovaWebViewEngine: Page状态
- WebViewClient: HttpAuth、ClientCert
- CordovaWebViewImpl: onOverrideUrlLoading、shouldAllowNavigation

大致就这么几个，所以PluginManager不仅是H5和Android发消息交互的作用，还控制了很多功能，并会交给每个插件去感知、去使用。

## 数据交互
前面已经讲到了PluginManager的exec方法，它涉及了H5和Android的交互，涉及CordovaBridge和nativeToJsMessageQueue，那接下来我们详细看看。

### 调用栈追踪
首先，我们还是来追踪下PluginManager的exec方法到底是从哪里开始触发的，这里只有一个地方用到了它，即CordovaBridge中的jsExec方法:
```
    public String jsExec(int bridgeSecret, String service, String action, String callbackId, String arguments) throws JSONException, IllegalAccessException {
        // 验证调用方法是否正确，jsMessageQueue消息处理是按一个一个请求处理的
        if (!verifySecret("exec()", bridgeSecret)) {
            return null;
        }
        
        // 以“@”开头会触发使用jsBridge模式再尝试一次
        if (arguments == null) {
            return "@Null arguments.";
        }

        jsMessageQueue.setPaused(true);
        try {
            // Tell the resourceApi what thread the JS is running on.
            CordovaResourceApi.jsThread = Thread.currentThread();

            // 执行插件方法
            pluginManager.exec(service, action, callbackId, arguments);
            
            // CallbackContext执行sendPluginResult后，数据最终到了jsMessageQueue
            String ret = null;
            if (!NativeToJsMessageQueue.DISABLE_EXEC_CHAINING) {
                ret = jsMessageQueue.popAndEncode(false);
            }
            return ret;
        } catch (Throwable e) {
            e.printStackTrace();
            return "";
        } finally {
            jsMessageQueue.setPaused(false);
        }
    }
```
有几个陌生的东西，先不管，看下执行插件方法的地方和取返回结果的代码，CallbackContext的PluginResult再这里被取出来并返回了。

继续追踪这个jsExec在哪调用的，有两个地方，一个是CordovaBridge的promptOnJsPrompt方法，一个是SystemExposedJsApi的exec方法。

这里需要停一下了，因为下面涉及了webView和Android交互的方式，如果没有知识储备的话还是比较难理解的，我找了一篇觉得还可以的文章可以先看下:

> [Android WebView与JS的交互方式总结](https://juejin.cn/post/6844904153605505032)

看完文章，那我们再来看则两个地方，不就是jsBridge方式和onPrompt方式么？SystemExposedJsApi不就是jsBridge:
```
class SystemExposedJsApi implements ExposedJsApi {
    // CordovaBridge就是jsBridge的一个中转
    private final CordovaBridge bridge;

    SystemExposedJsApi(CordovaBridge bridge) {
        this.bridge = bridge;
    }

    // 熟悉的JavascriptInterface注解
    @JavascriptInterface
    public String exec(int bridgeSecret, String service, String action, String callbackId, String arguments) throws JSONException, IllegalAccessException {
        return bridge.jsExec(bridgeSecret, service, action, callbackId, arguments);
    }
    
    // ...
}
```
再追踪下SystemExposedJsApi的使用，发现是在SystemWebViewEngine的exposeJsInterface方法，而exposeJsInterface方法在SystemWebViewEngine的init方法中调用:
```
    private static void exposeJsInterface(WebView webView, CordovaBridge bridge) {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1)) {
            // Android 4.2以下有漏洞，不要使用jsBridge方式
            return;
        }
        
        // 将jsBridge注入到webView中
        SystemExposedJsApi exposedJsApi = new SystemExposedJsApi(bridge);
        webView.addJavascriptInterface(exposedJsApi, "_cordovaNative");
    }
    
    @Override
    public void init(CordovaWebView parentWebView, CordovaInterface cordova, CordovaWebViewEngine.Client client,
        // ...
        exposeJsInterface(webView, bridge);
    }
```
好了，jsBridge就是一个接口丢进webView去。再来看下CordovaBridge的promptOnJsPrompt方法:
```
public String promptOnJsPrompt(String origin, String message, String defaultValue) {
        if (defaultValue != null && defaultValue.length() > 3 && defaultValue.startsWith("gap:")) {
            JSONArray array;
            try {
                // 前四位是"gap:"
                array = new JSONArray(defaultValue.substring(4));
                int bridgeSecret = array.getInt(0);
                String service = array.getString(1);
                String action = array.getString(2);
                String callbackId = array.getString(3);
                
                // 将onJsPrompt拿到的数据交给jsExec去执行插件
                String r = jsExec(bridgeSecret, service, action, callbackId, message);
                return r == null ? "" : r;
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            return "";
        }
        // Sets the native->JS bridge mode.
        // ...
        // Polling for JavaScript messages
        // ...
        // gap_init
        // ...
        return null;
    }
```
省略了好几个startsWith的自定义协议，重点就是promptOnJsPrompt方法是将onJsPrompt的数据解析然后交给插件使用的。再追踪下就到了WebChromeClient的onJsPrompt了:
```
    @Override
    public boolean onJsPrompt(WebView view, String origin, String message, String defaultValue, final JsPromptResult result) {
        // Unlike the @JavascriptInterface bridge, this method is always called on the UI thread.
        String handledRet = parentEngine.bridge.promptOnJsPrompt(origin, message, defaultValue);
        if (handledRet != null) {
            // handledRet是插件返回结果 => 所以必需要有结果，不然到onJsPrompt默认实现
            result.confirm(handledRet);
        } else {
            // 弹AlertDialog对话框？替代onJsPrompt默认实现
            dialogsHelper.showPrompt(message, defaultValue, (success, value) -> {
                if (success) {
                    result.confirm(value);
                } else {
                    result.cancel();
                }
            });
        }
        return true;
    }
```
到这里，H5调用插件的流程就分析完了，有很多东西忽略了，但是整个过程贯通了。

### 数据传递
上面讲了H5如何调用插件，中间CordovaBridge的promptOnJsPrompt方法从jsMessageQueue拿到了插件返回的结果PluginResult(String了)，后面不管是onJsPrompt还是jsBridge都可以拿到。

但是，这个jsMessageQueue数据是怎么一个过程，还是要看下的。首先从CallbackContext的sendPluginResult出发，看下这里面有什么玄机:
```
    public void sendPluginResult(PluginResult pluginResult) {
        // ...
        webView.sendPluginResult(pluginResult, callbackId);
    }
```
这里就是交给了webView处理，webView的实现类是CordovaWebViewImpl:
```
    @Override
    public void sendPluginResult(PluginResult cr, String callbackId) {
        nativeToJsMessageQueue.addPluginResult(cr, callbackId);
    }
```
这里加到了NativeToJsMessageQueue，代码如下:
```
    public void addPluginResult(PluginResult result, String callbackId) {
        if (callbackId == null) {  return; }
        
        boolean noResult = result.getStatus() == PluginResult.Status.NO_RESULT.ordinal();
        boolean keepCallback = result.getKeepCallback();
        if (noResult && keepCallback) {
            return;
        }
        
        // 创建JsMessage
        JsMessage message = new JsMessage(result, callbackId);
        if (FORCE_ENCODE_USING_EVAL) {
            StringBuilder sb = new StringBuilder(message.calculateEncodedLength() + 50);
            message.encodeAsJsMessage(sb);
            message = new JsMessage(sb.toString());
        }
        
        // 放进队列
        enqueueMessage(message);
    }
```
这里确实是入了NativeToJsMessageQueue的队列，但是怎么能保证CordovaBridge的jsMessageQueue和CordovaWebViewImpl的nativeToJsMessageQueue是同一个对象呢？

其实追踪下去，会找到我们在启动流程讲过的代码CordovaWebViewImpl的init方法里:
```
    @Override
    public void init(CordovaInterface cordova, List<PluginEntry> pluginEntries, CordovaPreferences preferences) {
        // ...
        // CordovaWebViewImpl持有
        nativeToJsMessageQueue = new NativeToJsMessageQueue();
        nativeToJsMessageQueue.addBridgeMode(new NativeToJsMessageQueue.NoOpBridgeMode());
        nativeToJsMessageQueue.addBridgeMode(new NativeToJsMessageQueue.LoadUrlBridgeMode(engine, cordova));

        // engine的init会创建CordovaBridge
        engine.init(this, cordova, engineClient, resourceApi, pluginManager, nativeToJsMessageQueue);
        // ...
    }
    
    // CordovaWebViewImpl中init
    @Override
    public void init(CordovaWebView parentWebView, CordovaInterface cordova, CordovaWebViewEngine.Client client,
              CordovaResourceApi resourceApi, PluginManager pluginManager,
              NativeToJsMessageQueue nativeToJsMessageQueue) {
        // ...
        bridge = new CordovaBridge(pluginManager, nativeToJsMessageQueue);
        exposeJsInterface(webView, bridge);
    }
```
所以，CordovaBridge的jsMessageQueue和CordovaWebViewImpl的nativeToJsMessageQueue是同一个对象，那这个传递过程就是通的了。

### 消息管理
上面的流程虽然讲完了，但是CordovaBridge和NativeToJsMessageQueue跳过了很多东西，还是有必要详细讲讲的，这里涉及到一个消息的管理。

#### CordovaBridge
这里讲下CordovaBridge中漏掉的一些个内容，主要和jsMessageQueue、bridgeSecret有关，

在jsExec方法中，对bridgeSecret进行了个验证，CordovaBridge的私有域中也有一个expectedBridgeSecret属性，这个是做什么的呢？
```
    // written by UI thread, read by JS thread.
    private volatile int expectedBridgeSecret = -1; 
    
    public String jsExec(int bridgeSecret, String service, String action, String callbackId, String arguments) throws JSONException, IllegalAccessException {
        if (!verifySecret("exec()", bridgeSecret)) {
            return null;
        }
        // ...
    }
```
经过一系列的查找，可以发现这个bridgeSecret是从promptOnJsPrompt来的，也就是说是onJsPrompt模式的东西，大致看promptOnJsPrompt方法的几种情况:
```
    // origin = url，message = prompt对话框显示内容，defaultValue对话框输入的默认值
    public String promptOnJsPrompt(String origin, String message, String defaultValue) {
        // 方法请求
        if (... defaultValue.startsWith("gap:")) {...}
        // 修改bridge模式
        else if (... defaultValue.startsWith("gap_bridge_mode:")) {
            try {
                // 取出bridgeSecret
                int bridgeSecret = Integer.parseInt(defaultValue.substring(16));
                // 在NativeToJsMessageQueue执行方法，传入bridgeSecret和message
                jsSetNativeToJsBridgeMode(bridgeSecret, Integer.parseInt(message));
            } catch (NumberFormatException e){
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            return "";
        }
        // 获得队列种的数据，以String格式返回
        else if (... defaultValue.startsWith("gap_poll:")) {...}
        // 初始化
        else if (... defaultValue.startsWith("gap_init:")) {
             if (pluginManager.shouldAllowBridgeAccess(origin)) {
                // Enable the bridge
                int bridgeMode = Integer.parseInt(defaultValue.substring(9));
                jsMessageQueue.setBridgeMode(bridgeMode);
                
                // Tell JS the bridge secret. 在此生成的BridgeSecret
                int secret = generateBridgeSecret();
                return ""+secret;
            } else {
                LOG.e(LOG_TAG, "gap_init called from restricted origin: " + origin);
            }
        }
        
        // 创建随机的expectedBridgeSecret
        int generateBridgeSecret() {
            SecureRandom randGen = new SecureRandom();
            expectedBridgeSecret = randGen.nextInt(Integer.MAX_VALUE);
            return expectedBridgeSecret;
        }
    }
```
好像是初始化的时候，NativeToJsMessageQueue设置好mode后，会创建个随机的expectedBridgeSecret，并返回给JS。像是创建个序号，用来标识这个交互的，再看下verifySecret方法:
```
    private boolean verifySecret(String action, int bridgeSecret) throws IllegalAccessException {
        if (!jsMessageQueue.isBridgeEnabled()) {
            if (bridgeSecret == -1) {
                LOG.d(LOG_TAG, action + " call made before bridge was enabled.");
            } else {
                LOG.d(LOG_TAG, "Ignoring " + action + " from previous page load.");
            }
            return false;
        }
        // Bridge secret wrong and bridge not due to it being from the previous page.
        if (expectedBridgeSecret < 0 || bridgeSecret != expectedBridgeSecret) {
            clearBridgeSecret();
            throw new IllegalAccessException();
        }
        return true;
    }
```
好了，破案了，在onJsPrompt的交互方式下，JS需要先发送gap_init请求，这时候CordovaBridge会生成个随机的expectedBridgeSecret给JS，JS后续的请求必须带上这个bridgeSecret，不然就出错了。

CordovaBridge就这点内容了，下面在onJsPrompt的交互方式下的几个请求，都到了NativeToJsMessageQueue种，下面我们来看它。

#### NativeToJsMessageQueue
在CordovaBridge中onJsPrompt的交互方式下的: gap_bridge_mode、gap_poll、gap_init，都交给了NativeToJsMessageQueue处理，CordovaBridge中其他几个地方也用到了这个对象，下面就分几点来研究下。

##### JsMessage
NativeToJsMessageQueue内部实际储存的是JsMessage，有两种形式，一个是webView要执行的js代码，另一个是插件要发送的PluginResult:
```
    // 通过CordovaWebViewImpl向JS发送消息
    public void addJavaScript(String statement) {
        enqueueMessage(new JsMessage(statement));
    }

    // 来自CallbackContext
    public void addPluginResult(PluginResult result, String callbackId) {
        if (callbackId == null) { return; }
       
        boolean noResult = result.getStatus() == PluginResult.Status.NO_RESULT.ordinal();
        boolean keepCallback = result.getKeepCallback();
        if (noResult && keepCallback) { return; }
        
        // 将PluginResult封装成JsMessage
        JsMessage message = new JsMessage(result, callbackId);
        if (FORCE_ENCODE_USING_EVAL) {
            StringBuilder sb = new StringBuilder(message.calculateEncodedLength() + 50);
            message.encodeAsJsMessage(sb);
            message = new JsMessage(sb.toString());
        }

        enqueueMessage(message);
    }
    
    private void enqueueMessage(JsMessage message) {
        synchronized (this) {
            if (activeBridgeMode == null) { return; }
            
            // 添加到队列中
            queue.add(message);
            if (!paused) {
                activeBridgeMode.onNativeToJsMessageAvailable(this);
            }
        }
    }
```
上面是向NativeToJsMessageQueue保存JsMessage，取PluginResult的方法前面我们看到过，就是popAndEncode方法:
```
    public String popAndEncode(boolean fromOnlineEvent) {
        synchronized (this) {
            if (activeBridgeMode == null) { return null; }
            activeBridgeMode.notifyOfFlush(this, fromOnlineEvent);
            if (queue.isEmpty()) { return null; }
            
            // 在不超过限定大小(MAX_PAYLOAD_SIZE)的情况下，将JsMessage尽可能都发给JS
            int totalPayloadLen = 0;
            int numMessagesToSend = 0;
            for (JsMessage message : queue) {
                int messageSize = calculatePackedMessageLength(message);
                // 控制长度
                if (numMessagesToSend > 0 && totalPayloadLen + messageSize > 
                    MAX_PAYLOAD_SIZE && MAX_PAYLOAD_SIZE > 0) {
                        break;
                }
                totalPayloadLen += messageSize;
                numMessagesToSend += 1;
            }

            // 拼成字符串，并移除拼接了的JsMessage
            StringBuilder sb = new StringBuilder(totalPayloadLen);
            for (int i = 0; i < numMessagesToSend; ++i) {
                JsMessage message = queue.removeFirst();
                packMessage(message, sb);
            }

            // JsMessage没有被取干净，最后拼接上一个'*' => 再次调用gap_poll获取？
            if (!queue.isEmpty()) {
                // Attach a char to indicate that there are more messages pending.
                sb.append('*');
            }
            String ret = sb.toString();
            return ret;
        }
    }
```
这里居然是在限定长度下，把多个JsMessage消息封装到一起，然后发给JS，居然不是直接取特定的JsMessage发过去。而且这里的queue是一个LinkedList，遍历的话应该是按数组形式迭代，能保证要的JsMessage发送到JS吗？

popAndEncode方法在CordovaBridge两个地方使用，一个是插件执行的jsExec方法，用来取result，第二个是在promptOnJsPrompt方法的"gap_poll:"情况下，所以如果数据没取完，还会通过promptOnJsPrompt方法继续取吗？

还是会通过popAndEncodeAsJs方法继续取呢？popAndEncodeAsJs方法类似popAndEncode，但是格式不一样:
```
    public String popAndEncodeAsJs() {
        synchronized (this) {
            int length = queue.size();
            if (length == 0) { return null; }
            
            // 计算在大小限制下，能发送的jsMessage数量
            int totalPayloadLen = 0;
            int numMessagesToSend = 0;
            for (JsMessage message : queue) {
                int messageSize = message.calculateEncodedLength() + 50; // overestimate.
                if (numMessagesToSend > 0 && totalPayloadLen + messageSize > MAX_PAYLOAD_SIZE && MAX_PAYLOAD_SIZE > 0) {
                    break;
                }
                totalPayloadLen += messageSize;
                numMessagesToSend += 1;
            }
            
            // 是否能够全部发送
            boolean willSendAllMessages = numMessagesToSend == queue.size();
            
            StringBuilder sb = new StringBuilder(totalPayloadLen + (willSendAllMessages ? 0 : 100));
            // 遍历将message转成js
            for (int i = 0; i < numMessagesToSend; ++i) {
                JsMessage message = queue.removeFirst();
                // 能发送全部message，并且是最后一个，就不加try-catch
                if (willSendAllMessages && (i + 1 == numMessagesToSend)) {
                    message.encodeAsJsMessage(sb);
                } else {
                    // 给每个message处理增加一个try，使之不会影响下一个message处理
                    // 。。。，每次下一个message处理都在前一个的finally里面
                    sb.append("try{");
                    message.encodeAsJsMessage(sb);
                    sb.append("}finally{");
                }
            }
            
            // 在最后一个message处理完后，如果没有拿到全部数据，会调用poll继续拿去数据
            if (!willSendAllMessages) {
                sb.append("window.setTimeout(function(){cordova.require('cordova/plugin/android/polling').pollOnce();},0);");
            }
            
            // 给前面那些try-catch加上右括号，willSendAllMessages为true时最后一个不用加
            for (int i = willSendAllMessages ? 1 : 0; i < numMessagesToSend; ++i) {
                sb.append('}');
            }
            String ret = sb.toString();
            return ret;
        }
    }
```
好像都差不多，就是这里message调用encodeAsJsMessage方法处理需要捕获移除，这里很神奇的是，每次下一个message都在前一个的finally里面执行。

这里和popAndEncode方法不一样的是，encodeAsJsMessage方法如果数据没有拿完，它会在最后调用JS继续拿数据。还有个区别popAndEncode方法是JS调用插件或者gap_poll返回的，而encodeAsJsMessage方法却是通过BridgeMode处理的。

##### BridgeMode
onJsPrompt的交互方式调用"gap_init:"时，在NativeToJsMessageQueue设置了一个BridgeMode:
```
    public void setBridgeMode(int value) {
        // -1 是 null？其他是已有mode的index？
        if (value < -1 || value >= bridgeModes.size()) {
            LOG.d(LOG_TAG, "Invalid NativeToJsBridgeMode: " + value);
        } else {
            BridgeMode newMode = value < 0 ? null : bridgeModes.get(value);
            if (newMode != activeBridgeMode) {
                synchronized (this) {
                    // 修改标记的activeBridgeMode
                    activeBridgeMode = newMode;
                    if (newMode != null) {
                        // 重置
                        newMode.reset();
                        if (!paused && !queue.isEmpty()) {
                            // 通知可用
                            newMode.onNativeToJsMessageAvailable(this);
                        }
                    }
                }
            }
        }
    }
```
应该就是JS通过index修改了当前的activeBridgeMode，如果mode发生了修改，新的mode重置，还要发送可用通知。BridgeMode就是一个简单的抽象类:
```
    public static abstract class BridgeMode {
        public abstract void onNativeToJsMessageAvailable(NativeToJsMessageQueue queue);
        public void notifyOfFlush(NativeToJsMessageQueue queue, boolean fromOnlineEvent) {}
        public void reset() {}
    }
```
BridgeMode有好几个实现，下面大致看一下:
```
    /** Uses webView.evaluateJavascript to execute messages. */ 
    public static class EvalBridgeMode extends BridgeMode {...}
    
    /** Uses webView.loadUrl("javascript:") to execute messages. */
    public static class LoadUrlBridgeMode extends BridgeMode {...}
    
    /** Uses JS polls for messages on a timer.. */
    public static class NoOpBridgeMode extends BridgeMode {...}
    
    /** Uses online/offline events to tell the JS when to poll for messages. */
    public static class OnlineEventsBridgeMode extends BridgeMode {...}
```
上面两个好理解，就是安卓调用JS的方式，NoOpBridgeMode只是继承BridgeMode，但什么都不做(ps. 更新: 这个是JS轮询的方式，所以Android无需操作)。

OnlineEventsBridgeMode好像就是加了个OnlineEventsBridgeModeDelegate，让webView感知online变换。看得不是很明白。online是从promptOnJsPrompt方法的"gap_poll:"来的，"1".equals(message)时就是online，感觉就是JS通知webView网络变化。

> 好吧，是我知识浅薄了，又看到个博客写的很好，[《Cordova学习笔记（二）JS与Android原生交互》](http://blog.haoji.me/cordova-note-js-native-bridge-mode.html?from=xa#online/offline-shi-jian)，online/offline事件也是用来事件交互的: 
> 通过webView.setNetworkAvailable(true/false)来设置webView的联网与掉线，从而触发js的window.onOnline和window.onOffline事件，然后再主动通过retrieveJsMessages到原生的消息队列获取消息

下面着重看下前两个BridgeMode，就是这两个地方调用了popAndEncodeAsJs，所以这两个BridgeMode就是用来给JS发送消息的(ps. 更新: 四个BridgeMode都是原生给JS发送消息的)。
```
    public static class EvalBridgeMode extends BridgeMode {
        // ...
        @Override
        public void onNativeToJsMessageAvailable(final NativeToJsMessageQueue queue) {
            cordova.getActivity().runOnUiThread( () -> {
                String js = queue.popAndEncodeAsJs();
                if (js != null) {
                    engine.evaluateJavascript(js, null);
                }
            });
        }
    }
    
    public static class LoadUrlBridgeMode extends BridgeMode {
        // ...
        @Override
        public void onNativeToJsMessageAvailable(final NativeToJsMessageQueue queue) {
            cordova.getActivity().runOnUiThread( () -> {
                String js = queue.popAndEncodeAsJs();
                if (js != null) {
                    engine.loadUrl("javascript:" + js, false);
                }
            });
        }
    }
```
下面看下这个BridgeMode的onNativeToJsMessageAvailable会在哪调用:
```
// 切换新模式后，不是暂停状态时
public void setBridgeMode(int value) {...}
// 加入队列，不是暂时状态时
private void enqueueMessage(JsMessage message) {...}
// 设置pause为false时
public void setPaused(boolean value) {...}
```
那有意思了，逻辑算是清晰了，就是JS和Android交互的数据都存放在NativeToJsMessageQueue里面，比如插件返回结果、Android主动执行的JS脚本，JS和Android之间会在经常交换数据。

还有个问题，就是NativeToJsMessageQueue到底有用到了那些BridgeMode:
```
    // CordovaWebViewImpl
    @Override
    public void init(CordovaInterface cordova, List<PluginEntry> pluginEntries, CordovaPreferences preferences) {
        // NativeToJsMessageQueue创建的时候默认带了两个BridgeMode
        nativeToJsMessageQueue = new NativeToJsMessageQueue();
        nativeToJsMessageQueue.addBridgeMode(new NativeToJsMessageQueue.NoOpBridgeMode());
        nativeToJsMessageQueue.addBridgeMode(new NativeToJsMessageQueue.LoadUrlBridgeMode(engine, cordova));
        
        // 初始化engine
        engine.init(this, cordova, engineClient, resourceApi, pluginManager, nativeToJsMessageQueue);
    }
    
    // SystemWebViewEngine
    @Override
    public void init(CordovaWebView parentWebView, CordovaInterface cordova, CordovaWebViewEngine.Client client,
              CordovaResourceApi resourceApi, PluginManager pluginManager,
              NativeToJsMessageQueue nativeToJsMessageQueue) {
        
        // engine中又加了两个BridgeMode
        nativeToJsMessageQueue.addBridgeMode(new NativeToJsMessageQueue.OnlineEventsBridgeMode(new NativeToJsMessageQueue.OnlineEventsBridgeMode.OnlineEventsBridgeModeDelegate() {
            @Override
            public void setNetworkAvailable(boolean value) {
                // 就是给webView一个网络通知？
                webView.setNetworkAvailable(value);
            }
            @Override
            public void runOnUiThread(Runnable r) {
                SystemWebViewEngine.this.cordova.getActivity().runOnUiThread(r);
            }
        }));
        
        // Android 4.3后，才能使用webView.evaluateJavascript形式执行JS
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2)
            nativeToJsMessageQueue.addBridgeMode(new NativeToJsMessageQueue.EvalBridgeMode(this, cordova));}
```
四个BridgeMode都有了，看JS需要使用哪个，然后通过onJsPrompt切换，切换后BridgeMode就能把message通过不同形式交给JS了。

## 浏览器相关
上面花了大量篇幅讲解了Cordova的启动流程、插件管理、数据交互，关于浏览器相关的内容也非常多，下面复习下前面启动流程，然后再看涉及的几个类吧。

在前面启动流程里，我们讲解到CordovaActivity的init方法中会创建一个CordovaWebView，并将其中engine的view设置为activity的页面，CordovaWebView并不是一个WebView，而是Cordova的一个交互接口，功能强大，其中真正的webView是engine中创建的。
```
    protected void init() {
        // 创建WebView
        appView = makeWebView();
        
        // 设置appView的一些属性，并将appView设置为ContentView，请求焦点
        createViews();
        
        // 初始化WebView
        if (!appView.isInitialized()) {
            appView.init(cordovaInterface, pluginEntries, preferences);
        }
        // ...
    }
    
    // CordovaWebView不是webview，是Cordova的一个交互接口，功能强大
    // Main interface for interacting with a Cordova webview 
    protected CordovaWebView makeWebView() {
        return new CordovaWebViewImpl(makeWebViewEngine());
    }

    // 实现类CordovaWebViewImpl，是Cordova和webView交互的接口
    // Glue class between CordovaWebView (main Cordova logic) and SystemWebView (the actual View).
    protected CordovaWebViewEngine makeWebViewEngine() {
        return CordovaWebViewImpl.createEngine(this, preferences);
    }
    
    protected void createViews() {
        // ... 设置为activity页面，View从engine.getView()来
        setContentView(appView.getView());
        // ...  申请焦点
        appView.getView().requestFocusFromTouch();
    }
```
稍微复习了一下，大致有这么几个类需要去研究下: CordovaWebViewImpl、CordovaWebViewEngine、engine.getView()得到的webView。

### CordovaWebViewImpl
> // Main interface for interacting with a Cordova webview

先来看下CordovaWebViewImpl，它继承了CordovaWebView接口，算是Cordova的主要交互入口，基本就是个god类吧，但是没什么好讲的，都是通过持有的对象去执行对应操作，下面看下它持有的对象:
```
    // 插件管理器
    private PluginManager pluginManager;
    // 浏览器引擎
    protected final CordovaWebViewEngine engine;
    // Cordova上下文
    private CordovaInterface cordova;
    // 资源管理器
    private CordovaResourceApi resourceApi;
    // config.xml设置的preferences
    private CordovaPreferences preferences;
    // Android核心插件
    private CoreAndroid appPlugin;
    // 事件队列
    private NativeToJsMessageQueue nativeToJsMessageQueue;
    // 监听WebChromeClient一些事件的回调接口
    private EngineClient engineClient = new EngineClient();
    // Android上下文
    private static Activity gContext;
```
基本就是Cordova中能见到的大部分了，有些类上面讲过，有些没讲过，后面补充。下面看下几个我觉得有意思的方法。

#### 有意思的方法

##### loadUrlIntoView
第一个当然是加载入口页面了，但是上面启动流程讲的很详细了，这里稍微再提下，实际就是交给engine了:
```
    @Override
    public void loadUrlIntoView(final String url, boolean recreatePlugins) {
        // 拦截url，"javascript:"开头的代码为Android调用JS的方法
        if (url.equals("about:blank") || url.startsWith("javascript:")) {
            // 可能要用cordova.getActivity().runOnUiThread处理下
            engine.loadUrl(url, false);
            return;
        }

        // ...
        final boolean _recreatePlugins = recreatePlugins;
        cordova.getActivity().runOnUiThread( () -> {
            // ...
            // 最终通过CordovaWebViewEngine去加载网页
            engine.loadUrl(url, _recreatePlugins);
        });
    }
```

##### showWebPage
Cordova不仅提供了加载H5的功能，还提供了打开Uri的功能，使用showWebPage方法可以打开链接或者访问系统文件:
```
    @Override
    public void showWebPage(String url, boolean openExternal, boolean clearHistory, Map<String, Object> params) {
        // If clearing history
        if (clearHistory) { engine.clearHistory(); }

        // If loading into our webview
        if (!openExternal) {
        
            // 遍历所有插件，查看是否允许
            if (pluginManager.shouldAllowNavigation(url)) {
                loadUrlIntoView(url, true);
            } else {...}
        }
        
        // 打开uri
        if (!pluginManager.shouldOpenExternalUrl(url)) { return; }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            Uri uri = Uri.parse(url);
            
            // file需要设置MimeType
            if ("file".equals(uri.getScheme())) {
                intent.setDataAndType(uri, resourceApi.getMimeType(uri));
            } else {
                intent.setData(uri);
            }
            cordova.getActivity().startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            LOG.e(TAG, "Error loading url " + url, e);
        }
    }
```
##### setButtonPlumbedToJs
Cordova还能重写系统按钮的事件，通过setButtonPlumbedToJs可以设置重写的按钮事件，这里是先保存在boundKeyCodes的map里面，在EngineClient的onDispatchKeyEvent会去处理，EngineClient是WebViewClient的一个代理:
```
    @Override
    public void setButtonPlumbedToJs(int keyCode, boolean override) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_MENU:
                // TODO: Why are search and menu buttons handled separately?
                if (override) {
                    boundKeyCodes.add(keyCode);
                } else {
                    boundKeyCodes.remove(keyCode);
                }
                return;
            default:
                throw new IllegalArgumentException("Unsupported keycode: " + keyCode);
        }
    }
```
##### EngineClient
前面多少已经提到了EngineClient就是WebViewClient的一个代理，WebViewClient的一些方法会交给他处理:
```
    protected class EngineClient implements CordovaWebViewEngine.Client {
        // 加载失败计数吧
        public void clearLoadTimeoutTimer() {...}
        
        // 代理WebViewClient的几个方法
        public void onPageStarted(String newUrl) {...}
        public void onReceivedError(int errorCode, String description, String failingUrl) {...}
        public void onPageFinishedLoading(String url) {...}
        
        // 代理SystemWebView中按键事件监听
        public Boolean onDispatchKeyEvent(KeyEvent event) {...}
        
        // 代理WebViewClient的shouldOverrideUrlLoading
        public boolean onNavigationAttempt(String url) {...}
    }
```
这些个方法大致都是通过pluginManager.postMessage方法，传递了一个通知。不过后面两个方法比较有意思，可以看下:
```
    @Override
    public Boolean onDispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        // 手机返回键
        boolean isBackButton = keyCode == KeyEvent.KEYCODE_BACK;
        // 只有拦截ACTION_DOWN才能收到后续事件
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (isBackButton && mCustomView != null) {
                // 有自定义view(如视频全屏)，拦截了
                return true;
            } else if (boundKeyCodes.contains(keyCode)) {
                // 设置了重写物理按键，拦截了
                return true;
            } else if (isBackButton) {
                // 交给浏览器判断是否拦截，网页后退
                return engine.canGoBack();
            }
            
        // 抬起事件，做出响应    
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            if (isBackButton && mCustomView != null) {
                // 关闭自定义view，比如隐藏全屏视频
                hideCustomView();
                return true;
                
            // 设置了拦截物理按键，发送JS事件给H5，交给H5响应
            } else if (boundKeyCodes.contains(keyCode)) {
                String eventName = null;
                switch (keyCode) {
                    case KeyEvent.KEYCODE_VOLUME_DOWN:
                        eventName = "volumedownbutton";
                        break;
                    case KeyEvent.KEYCODE_VOLUME_UP:
                        eventName = "volumeupbutton";
                        break;
                    case KeyEvent.KEYCODE_SEARCH:
                        eventName = "searchbutton";
                        break;
                    case KeyEvent.KEYCODE_MENU:
                        eventName = "menubutton";
                        break;
                    case KeyEvent.KEYCODE_BACK:
                        eventName = "backbutton";
                        break;
                }
                if (eventName != null) {
                    // 向JS发送事件
                    sendJavascriptEvent(eventName);
                    return true;
                }
            } else if (isBackButton) {
                // isResume在这改？没必要吧，start内会修改
                if (!CordovaActivity.isResume) {
                    CordovaActivity.isResume = true;
                    return null;
                }
                return engine.goBack();
            }
        }
        return null;
    }
```
对KeyEvent的处理，还是很容易让Android开发觉得有兴趣的，虽然这里事件处理比较简单。

另一个方法就是代理WebViewClient中shouldOverrideUrlLoading方法的onNavigationAttempt，是对url的拦截功能:
```
    @Override
    public boolean onNavigationAttempt(String url) {
        // Give plugins the chance to handle the url
        if (pluginManager.onOverrideUrlLoading(url)) {
            // 被插件拦截，默认false
            return true;
        } else if (pluginManager.shouldAllowNavigation(url)) {
            // 被插件允许，默认放行file协议和"about:blank"页面
            return false;
        } else if (pluginManager.shouldOpenExternalUrl(url)) {
            // 外部链接
            showWebPage(url, true, false, null);
            return true;
        }
        
        // 默认拦截？默认在第二个if通过(不被拦截)
        LOG.w(TAG, "Blocked (possibly sub-frame) navigation to non-allowed URL: " + url);
        return true;
    }
```
所以当CordovaActivity调用loadUrl方法之后，webView的WebViewClient会把拦截操作交到这里，先由所有插件判断是否拦截，再又所有插件判断是否特殊放行，默认file协议和空页面在这泛型，最后判断是否是打开外部页面(uri)，剩下的其他链接则不予显示。

#### CordovaResourceApi
前面已经出现了很多次的CordovaResourceApi，这里被CordovaWebViewImpl持有，并使用了，下面还是看一下吧。

先看下这个类的说明:
```
/**
 * What this class provides:
 * 1. Helpers for reading & writing to URLs.
 *   - E.g. handles assets, resources, content providers, files, data URIs, http[s]
 *   - E.g. Can be used to query for mime-type & content length.
 *
 * 2. To allow plugins to redirect URLs (via remapUrl).
 *   - All plugins should call remapUrl() on URLs they receive from JS *before*
 *     passing the URL onto other utility functions in this class.
 *   - For an example usage of this, refer to the com.xxx.file plugin.
 */
```
大致就是协助读写URLs、协助插件对URLs重定向两个功能，先来看第一个:
```
    // 处理的URLs类型
    // file协议
    public static final int URI_TYPE_FILE = 0;
    // file协议，但是路径包含android_asset
    public static final int URI_TYPE_ASSET = 1;
    // content协议，即Provider的协议
    public static final int URI_TYPE_CONTENT = 2;
    // APP内的resource
    public static final int URI_TYPE_RESOURCE = 3;
    // data协议，保存小段数据，格式: data:[<mediatype>][;base64],<data>
    public static final int URI_TYPE_DATA = 4;
    // 网络协议
    public static final int URI_TYPE_HTTP = 5;
    public static final int URI_TYPE_HTTPS = 6;
    // Cordova插件，指定某个插件去处理uri
    public static final int URI_TYPE_PLUGIN = 7;
    public static final int URI_TYPE_UNKNOWN = -1;
    
    public CordovaResourceApi(Context context, PluginManager pluginManager) {
        // 对uri、asset、plugin控制的对象
        this.contentResolver = context.getContentResolver();
        this.assetManager = context.getAssets();
        this.pluginManager = pluginManager;
    }
    
    // 根据uri判断所属的类型
    public static int getUriType(Uri uri) {...}
```
大致就是根据Uri调用getUriType判断上面八种类型，然后通过contentResolver、assetManager、pluginManager提供相应功能(读写)，内容很多就不列举了。
```
    public OpenForReadResult openForRead(Uri uri, boolean skipThreadCheck) throws IOException {...}
    public OutputStream openOutputStream(Uri uri, boolean append) throws IOException {...}
    public File mapUriToFile(Uri uri) {...}
    public HttpURLConnection createHttpConnection(Uri uri) throws IOException {...}
    public void copyResource(OpenForReadResult input, OutputStream outputStream) throws IOException {...}
```

另外一个功能就是协助插件对URLs重定向了，应该就remapUri一个方法了:
```
    public Uri remapUri(Uri uri) {
        assertNonRelative(uri);
        // 让所有插件去判断是否拦截
        Uri pluginUri = pluginManager.remapUri(uri);
        return pluginUri != null ? pluginUri : uri;
    }
    
    public String remapPath(String path) {
        return remapUri(Uri.fromFile(new File(path))).getPath();
    }
```
大致意思就是对于所有的URLs，使用前都应该调用remapUri判断下，pluginManager会遍历所有插件，判断是否要拦截或者修改uri，返回行uri或者null不拦截。

#### CoreAndroid
CoreAndroid前面实际已经用到好几次了:

> 一个是CordovaInterfaceImpl中，onCordovaInit方法中，Activity被系统杀死时，用CoreAndroid给JS发送了resume消息
> 一个时在CordovaWebViewImpl的init方法中，向pluginManager添加了一个名为CoreAndroid.PLUGIN_NAME的CoreAndroid插件

说白了，这就是一个默认添加的插件，用来处理一些由Cordova插件去解决的事情，下面大致讲一下，先看下这四个方法:
```
    // 执行JS代码，通过sendEventMessage放到事件队列去
    public void fireJavascriptEvent(String action) { sendEventMessage(action); }
    // 插件初始化，这时候注册了手机通话状态监听的receiver: RINGING, OFFHOOK and IDLE
    public void pluginInitialize() { this.initTelephonyReceiver(); }
    // 当activity被系统杀死时，用来发送resume事件，需要利用messageChannel(CallbackContext，所以要JS先发起请求)
    public void sendResumeEvent(PluginResult resumeEvent) {...}
    
    // 这里提供了大量方法，可以供JS调用，也可以给Android用(public方法)
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {...}
```
前面三个方法，都是特定的功能，具体代码就不列举了，下面看下execute中提供了那些方法:
```
    // 清除webView的cache
    if (action.equals("clearCache")) {...}
    // 显示webView
    else if (action.equals("show")) {...}
    // 加载url
    else if (action.equals("loadUrl")) {...}
    // 取消加载url
    else if (action.equals("cancelLoadUrl")) {...}
    // 清除webView历史记录
    else if (action.equals("clearHistory")) {...}
    // 根据webView历史记录后退
    else if (action.equals("backHistory")) {...}
    // 重写安卓物理按键(音量)
    else if (action.equals("overrideButton")) {...}
    // 重写安卓返回键
    else if (action.equals("overrideBackbutton")) {...}
    // 退出APP
    else if (action.equals("exitApp")) {...}
    // JS向Android传递一个messageChannel(CallbackContext)，接下来Android可以持续向JS发送消息
    else if (action.equals("messageChannel")) {...}
```
源码就不详解了，功能都挺好理解的。

### CordovaWebViewEngine
前面讲了CordovaWebViewImpl只是一个交互的类，并不是一个webView，真正的webView是由CordovaWebViewEngine提供的，其实CordovaWebViewEngine也还是一个webView的交互类，只不过它更专注与webView上，下面我们就来研究下。

#### 创建位置
首先看下CordovaWebViewEngine是在哪创建的:
```
    // CordovaActivity中
    protected CordovaWebView makeWebView() {
        return new CordovaWebViewImpl(makeWebViewEngine());
    }

    protected CordovaWebViewEngine makeWebViewEngine() {
        return CordovaWebViewImpl.createEngine(this, preferences);
    }
    
    // CordovaWebViewImpl中
    public static CordovaWebViewEngine createEngine(Context context, CordovaPreferences preferences) {
        if (gContext == null && context instanceof Activity) {
            gContext = (Activity) context;
        }
        
        // 通过反射创建
        String className = preferences.getString("webview", SystemWebViewEngine.class.getCanonicalName());
        try {
            Class<?> webViewClass = Class.forName(className);
            Constructor<?> constructor = webViewClass.getConstructor(Context.class, CordovaPreferences.class);
            return (CordovaWebViewEngine) constructor.newInstance(context, preferences);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create webview. ", e);
        }
    }
```
这里是CordovaActivity中创建的，最终创建的时候是在CordovaWebViewImpl中通过反射创建的，反射的class名称来自config.xml中，默认是SystemWebViewEngine.class。

SystemWebViewEngine.class是默认提供的，当然很多都是用X5内核替换它，这里就需要重写config.xml的配置了，下面就SystemWebViewEngine为例讲一下.

#### SystemWebViewEngine
CordovaWebViewEngine实际就是一个接口，SystemWebViewEngine只实现了它的方法，并没有其他额外的功能，下面大致看下有哪些功能。
```
    CordovaWebView getCordovaWebView();
    ICordovaCookieManager getCookieManager();
    // 这个才是真正的webView
    View getView();

    // 加载链接
    void loadUrl(String url, boolean clearNavigationStack);
    // 停止webView加载
    void stopLoading();
    
    String getUrl();
    void clearCache();
    void clearHistory();
    // 判断是否能够返回上一个页面
    boolean canGoBack();
    // 返回上一个页面
    boolean goBack();

    // 会调用webView的onPause()/pauseTimers、onResume/resumeTimers，应该类似activity的pause/resume
    void setPaused(boolean value);
    void destroy();

    // 执行JS代码
    void evaluateJavascript(String js, ValueCallback<String> callback);
```
基本都是一些直接交给webView操作的方法，唯一需要注意的就是getView()这个方法，它返回了一个View，但是这个才是真正的webView。

当然上面还漏了一个重要的方法——init方法，这个前面我们也见了很多次了，下面详细看下，它具体做了什么:
```
    @Override
    public void init(CordovaWebView parentWebView, CordovaInterface cordova, CordovaWebViewEngine.Client client,
              CordovaResourceApi resourceApi, PluginManager pluginManager,
              NativeToJsMessageQueue nativeToJsMessageQueue) {
              
        if (this.cordova != null) { throw new IllegalStateException(); }
        
        // 取参数
        if (preferences == null) { preferences = parentWebView.getPreferences(); }
        this.parentWebView = parentWebView;
        this.cordova = cordova;
        this.client = client;
        this.resourceApi = resourceApi;
        this.pluginManager = pluginManager;
        this.nativeToJsMessageQueue = nativeToJsMessageQueue;
        
        // webView初始化
        webView.init(this, cordova);

        // 设置WebViewSettings，写过的都知道，一大堆，但是很重要
        initWebViewSettings();

        // BridgeMode是Android给JS发消息的工具
        // 添加OnlineEventsBridgeMode，使用webView的online/offline事件的Android => JS交互
        nativeToJsMessageQueue.addBridgeMode(new NativeToJsMessageQueue.OnlineEventsBridgeMode(new NativeToJsMessageQueue.OnlineEventsBridgeMode.OnlineEventsBridgeModeDelegate() {
            @Override
            public void setNetworkAvailable(boolean value) {
                webView.setNetworkAvailable(value);
            }
            @Override
            public void runOnUiThread(Runnable r) {
                SystemWebViewEngine.this.cordova.getActivity().runOnUiThread(r);
            }
        }));
        // Android 4.4后应该使用evaluateJavascript来给JS发送消息
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2)
            nativeToJsMessageQueue.addBridgeMode(new NativeToJsMessageQueue.EvalBridgeMode(this, cordova));
	    
	    // 创建CordovaBridge，Android和JS双向互相的控制类
	    bridge = new CordovaBridge(pluginManager, nativeToJsMessageQueue);
        
        // 为webView创建jsBridge(JS调用Android的方法之一，另一个是onPrompt)，并交给CordovaBridge管理
        exposeJsInterface(webView, bridge);
    }
```
有些我们前面已经讲过了，比如创建BridgeMode添加到nativeToJsMessageQueue、创建CordovaBridge、为webView创建jsBridge几个。这里就再看下initWebViewSettings，有点长，混个脸熟吧:
```
    private void initWebViewSettings() {
        webView.setInitialScale(0);
        webView.setVerticalScrollBarEnabled(false);
        
        // Enable JavaScript 启用JS
        final WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setLayoutAlgorithm(LayoutAlgorithm.NORMAL);
        
        // ...

        //We don't save any form data in the application 不保存数据和密码
        settings.setSaveFormData(false);
        settings.setSavePassword(false);

        // 设置是否允许通过 file url 加载的 Javascript 可以访问其他的源(包括http、https等源)
        // 在Android 4.1前默认允许，在Android 4.1后默认禁止
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowUniversalAccessFromFileURLs(true);
        }
        
        // 在Api Level 17以后，android增加了对于由WebView加载的网页播放声音是否需要用户的手势触发，默认值为true
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }
        
        // Enable database 数据库
        // We keep this disabled because we use or shim to get around DOM_EXCEPTION_ERROR_16
        String databasePath = webView.getContext().getApplicationContext().getDir("database", Context.MODE_PRIVATE).getPath();
        settings.setDatabaseEnabled(true);
        settings.setDatabasePath(databasePath);

        // 允许调试加载到此应用程序的任何WebView中的Web内容（HTML、CSS、JavaScript）。debug模式及Android 4.4+ 
        ApplicationInfo appInfo = webView.getContext().getApplicationContext().getApplicationInfo();
        if ((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            enableRemoteDebugging();
        }
        
        // 设置地理位置数据库的存储路径。为保证定位权限和位置缓存被保存，调用此方法时必须传入应用可写入的路径。(Andorid7.0 开始废弃了)
        settings.setGeolocationDatabasePath(databasePath);
        
        // Enable built-in geolocation 启动定位
        settings.setGeolocationEnabled(true);

        // Enable DOM storage
        settings.setDomStorageEnabled(true);

        // Enable AppCache
        // Fix for CB-2282
        settings.setAppCacheMaxSize(5 * 1048576);
        settings.setAppCachePath(databasePath);
        settings.setAppCacheEnabled(true);

        // Fix for CB-1405
        // Google issue 4641
        String defaultUserAgent = settings.getUserAgentString();

        // Fix for CB-3360
        String overrideUserAgent = preferences.getString("OverrideUserAgent", null);
        if (overrideUserAgent != null) {
            settings.setUserAgentString(overrideUserAgent);
        } else {
            String appendUserAgent = preferences.getString("AppendUserAgent", null);
            if (appendUserAgent != null) {
                settings.setUserAgentString(defaultUserAgent + " " + appendUserAgent);
            }
        }
        // End CB-3360

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        if (this.receiver == null) {
            this.receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    settings.getUserAgentString();
                }
            };
            webView.getContext().registerReceiver(this.receiver, intentFilter);
        }
        // end CB-1405
    }
```
有点长，就大致看下吧，主要就启用JS、DOM、跨域的注意下。

### SystemWebView
SystemWebView没有改多少内容，就是复写了一些WebView的方法，加了个自己的getCordovaWebView，下面大致看下：
```
    // 获得CordovaWebView
    public CordovaWebView getCordovaWebView() {...}
    // 拿到viewClient变量
    public void setWebViewClient(WebViewClient client) {...}
    // 拿到chromeClient变量
    public void setWebChromeClient(WebChromeClient client) {...}
    // 将按键事件转给CordovaWebViewEngine.Client
    public boolean dispatchKeyEvent(KeyEvent event) {...}
```
这几个都没什么好看的，着重看下它的init方法:
```
    void init(SystemWebViewEngine parentEngine, CordovaInterface cordova) {
        this.cordova = cordova;
        this.parentEngine = parentEngine;
        
        // 创建WebViewClient
        if (this.viewClient == null) {
            setWebViewClient(new SystemWebViewClient(parentEngine));
        }
        
        // 创建WebChromeClient
        if (this.chromeClient == null) {
            setWebChromeClient(new SystemWebChromeClient(parentEngine));
        }
    }
```
也就是创建了两个对象，SystemWebViewClient和SystemWebChromeClient，其实我们这里也就剩下这两部分内容了。

### WebViewClient
这里先来看下WebViewClient的概念:
> WebView通过该类对外通知页面加载相关的消息用来在页面加载的各个阶段进行业务处理，处理加载错误情况，拦截页面内和页面外的请求。

所以这是一个提供webView一些处理方案的类，在Cordova里面，实现它的是SystemWebViewClient，在SystemWebView的init方法中创建，传入了SystemWebViewEngine对象。

先来看下它重写的WebViewClient方法:
```
    // 拦截url，交给CordovaWebViewEngine.Client处理了
    public boolean shouldOverrideUrlLoading(WebView view, String url) {...}
    // 处理HTTP验证请求(客户端认证)，先从url中取，再交给pluginManager处理，默认401
    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {...}
    // TargetApi(21)，客户端证书认证，需要提供证书信息，交给pluginManager取认证
    public void onReceivedClientCertRequest (WebView view, ClientCertRequest request){...}
    // webView打开/结束新页面，交给CordovaWebViewEngine.Client处理
    public void onPageStarted(WebView view, String url, Bitmap favicon) {...}
    public void onPageFinished(WebView view, String url) {...}
    // 处理页面异常，协议不支持默认返回
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {...}
    // 处理SSL异常，忽略(proceed)或者取消(cancel)，Google Play要求写个弹窗给用户选择
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {...}
    // shouldOverrideUrlLoading拦截的是url加载阶段，主要拦截url
    // shouldInterceptRequest加载的是响应主体阶段，可拦截url、js、css等，拦截资源加载
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {...}
```
下面挑几个简单看下。

#### onReceivedHttpAuthRequest
先来看onReceivedHttpAuthRequest方法，SystemWebViewClient中多出来的几个public方法都和它有关:
```
    @Override
    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {

        // 从保存的authenticationTokens中取
        AuthenticationToken token = this.getAuthenticationToken(host, realm);
        if (token != null) {
            handler.proceed(token.getUserName(), token.getPassword());
            return;
        }

        // 遍历插件，看看有没有插件能够处理
        PluginManager pluginManager = this.parentEngine.pluginManager;
        if (pluginManager != null && pluginManager.onReceivedHttpAuthRequest(null, new CordovaHttpAuthHandler(handler), host, realm)) {
            parentEngine.client.clearLoadTimeoutTimer();
            return;
        }

        // 默认401状态码: Unauthorized 代表客户端错误
        super.onReceivedHttpAuthRequest(view, handler, host, realm);
    }
```
SystemWebViewClient中多出来的几个public方法就是对authenticationTokens管理:
```
    private Hashtable<String, AuthenticationToken> authenticationTokens = new Hashtable<String, AuthenticationToken>();
    // 四个管理的方法
    public void setAuthenticationToken(AuthenticationToken authenticationToken, String host, String realm) {...}
    public AuthenticationToken removeAuthenticationToken(String host, String realm) {...}
    public AuthenticationToken getAuthenticationToken(String host, String realm) {...}
    public void clearAuthenticationTokens() {...}
```
#### onReceivedSslError
这里看下Cordova的onReceivedSslError怎么写的，实际就是看了下是不是debug模式，是的话就跳过，不是就交给默认处理。
```
    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {

        final String packageName = parentEngine.cordova.getActivity().getPackageName();
        final PackageManager pm = parentEngine.cordova.getActivity().getPackageManager();

        ApplicationInfo appInfo;
        try {
            appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            if ((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                // debug = true
                handler.proceed();
                return;
            } else {
                // debug = false
                super.onReceivedSslError(view, handler, error);
            }
        } catch (NameNotFoundException e) {
            // When it doubt, lock it out!
            super.onReceivedSslError(view, handler, error);
        }
    }
```
实际上不应该这么写，需要写一个dialog交给用户去处理，不然上架Google时会被退回:
```
    AlertDialog.Builder(context).apply {
      val errorMsg = when (error?.primaryError) {
        SslError.SSL_UNTRUSTED -> "The certificate authority is not trusted."
        SslError.SSL_EXPIRED -> "The certificate has expired."
        SslError.SSL_IDMISMATCH -> "The certificate Hostname mismatch."
        SslError.SSL_NOTYETVALID -> "The certificate is not yet valid."
        SslError.SSL_DATE_INVALID -> "The date of the certificate is invalid"
        else -> "SSL Certificate error."
      }
      setMessage("$errorMsg Do you want to continue anyway?")
      setPositiveButton("continue") { _, _ ->
        handler?.proceed()
      }
      setNegativeButton("cancel") { _, _ ->
        handler?.cancel()
      }
    }.create().show()
```
#### shouldInterceptRequest
这个方法还是要注意下，新的API有变化，不过还是差不多。

> shouldInterceptRequest(WebView view, String url)在 API 21 后废弃，shouldInterceptRequest(WebView view, WebResourceRequest request)为新增的方法，不过本质上还是调用的是前一个方法。

这里是webView对资源文件的拦截，用到了我们前面讲到CordovaResourceApi
```
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
        try {

            // 交给插件拦截一遍，被拦截了返回404
            if (!parentEngine.pluginManager.shouldAllowRequest(url)) {
                // Results in a 404.
                return new WebResourceResponse("text/plain", "UTF-8", null);
            }

            // 修改Uri，还是交给插件去看看有没有修改，没用修改就返回原来的
            CordovaResourceApi resourceApi = parentEngine.resourceApi;
            Uri origUri = Uri.parse(url);
            Uri remappedUri = resourceApi.remapUri(origUri);
            
            // Uri有修改，需要通过CordovaResourceApi得到新资源的inputStream
            if (!origUri.equals(remappedUri) || needsSpecialsInAssetUrlFix(origUri) || needsKitKatContentUrlFix(origUri)) {
                CordovaResourceApi.OpenForReadResult result = resourceApi.openForRead(remappedUri, true);
                return new WebResourceResponse(result.mimeType, "UTF-8", result.inputStream);
            }
            
            // 默认不修改
            return null;
        } catch (IOException e) {
            if (!(e instanceof FileNotFoundException)) {
                LOG.e(TAG, "Error occurred while loading a file (returning a 404).", e);
            }
            // Results in a 404.
            return new WebResourceResponse("text/plain", "UTF-8", null);
        }
    }
```

### WebChromeClient
前面已经讲到了WebViewClient，它是用来在页面加载的各个阶段进行业务处理的类，主要影响html页面的事件，而WebChromeClient类似，但是处理的是影响浏览器的事件，下面就来看下Cordova中如何写的。

和上面WebViewClient一样，我们先大致看下Cordova的SystemWebChromeClient重写了哪些方法:
```
    // JS对话框的三个方法，会由Cordova显示Android对话框，onJsPrompt会用来和Android交互
    public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {...}
    public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {...}
    public boolean onJsPrompt(WebView view, String origin, String message, String defaultValue, final JsPromptResult result) {...}
    
    // 通知应用程序webview内核web sql 数据库超出配额，请求是否扩大数据库磁盘配额。默认行为是不会增加数据库配额。
    public void onExceededDatabaseQuota(String url, String databaseIdentifier, long currentQuota, long estimatedSize,
            long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater) {...}
    
    // 拿到H5的console消息
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {...}
    
    // 当前页面请求是否允许进行定位，通过Callback设置
    public void onGeolocationPermissionsShowPrompt(String origin, Callback callback) {...}
    
    // 显示自定义View，比如全屏的时候使用
    public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {...}
    public void onHideCustomView() {...}
    
    // 播放视频时，在第一帧呈现之前，需要花一定的时间来进行数据缓冲。ChromeClient可以使用这个函数来提供一个在数据缓冲时显示的视图。
    public View getVideoLoadingProgressView() {...}
    
    // 用来选取文件
    public boolean onShowFileChooser(WebView webView, final ValueCallback<Uri[]> filePathsCallback, final WebChromeClient.FileChooserParams fileChooserParams) {...}
    
    // 权限请求
    public void onPermissionRequest(final PermissionRequest request) {...}
```
有些方法还是很有意思的，部分讲过的就不多写了，下面也是挑几个讲下。

#### onConsoleMessage
webView实际和我们的APP不在一个application，有时候调试都不会跳进去我们的断点，如何再我们Android的log里面显示H5的log就很重要，特别是去查看JS的error日志:
```
    @TargetApi(8)
    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        // 在Android的日志中输出
        if (consoleMessage.message() != null)
            LOG.d(LOG_TAG, "%s: Line %d : %s" , consoleMessage.sourceId() , consoleMessage.lineNumber(), consoleMessage.message());
         return super.onConsoleMessage(consoleMessage);
    }
```

#### onGeolocationPermissionsShowPrompt
这里是使用webView进行定位，不过这里也有几个前提，首先要增加权限声明:
```
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```
然后需要在webSetting中设置启用定位:
```
    webSettings.setJavaScriptEnabled(true);
    webSettings.setGeoLoactionEnabled(true); //这个其实默认为true
    
    // 有的还得加上这个，设置地理位置数据库的存储路径(定位权限和位置缓存)，不过Andorid7.0 开始废弃了
    String dir = getApplicationContext().getFilesDir().getPath();
    settings.setGeolocationDatabasePath(dir); 
```
满足上面这些要求后，webView进行定位就可以进行定位了:
```
    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissionsCallback callback) {
        super.onGeolocationPermissionsShowPrompt(origin, callback);
        // 同意权限申请，Android7.0 开始，只有来自安全的链接如 https 才会回调此方法
        callback.invoke(origin, true, false);
        // 还拿了个插件去处理，不过默认好像没有这个插件
        CordovaPlugin geolocation = parentEngine.pluginManager.getPlugin("Geolocation");
        if(geolocation != null && !geolocation.hasPermisssion()) {
            geolocation.requestPermissions(0);
        }
    }
```
#### onShowFileChooser
onShowFileChooser方法其实在平时也经常用得到，可以配合H5的uri请求，使用系统默认的文件选择器，选择照片文件之类的。Cordova里面都是默认实现:
```
    // SDK >= 21
    public boolean onShowFileChooser(WebView webView, final ValueCallback<Uri[]> filePathsCallback, 
        final WebChromeClient.FileChooserParams fileChooserParams) {
        
        // 创建一个action.VIEW的intent，去打开系统的文件选择器
        Intent intent = fileChooserParams.createIntent();
        try {
            parentEngine.cordova.startActivityForResult(new CordovaPlugin() {
                @Override
                public void onActivityResult(int requestCode, int resultCode, Intent intent) {
                    Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, intent);
                    LOG.d(LOG_TAG, "Receive file chooser URL: " + result);
                    filePathsCallback.onReceiveValue(result);
                }
            }, intent, FILECHOOSER_RESULTCODE);
        } catch (ActivityNotFoundException e) {
            LOG.w("No activity found to handle file chooser intent.", e);
            filePathsCallback.onReceiveValue(null);
        }
        return true;
    }
```

#### onPermissionRequest
这里是H5权限的申请，这里默认就通过了，其实应该弹出一个dialog让用户选择的:
```
    public void onPermissionRequest(final PermissionRequest request) {
        request.grant(request.getResources());
    }
```
正确写法示例:
```
    public void onPermissionRequest(final PermissionRequest request) {
        webkitPermissionRequest = request
        val requestedResources = request.resources
        for (r in requestedResources) {
            if (r == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                // In this sample, we only accept video capture request.
                val alertDialogBuilder = AlertDialog.Builder(activity)
                    .setTitle("Allow Permission to camera")
                    .setPositiveButton("Allow") { dialog, which ->
                        dialog.dismiss()
                        webkitPermissionRequest.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                        Log.d(TAG, "Granted")
                    }
                    .setNegativeButton("Deny") { dialog, which ->
                        dialog.dismiss()
                        webkitPermissionRequest.deny()
                        Log.d(TAG, "Denied")
                    }
                val alertDialog = alertDialogBuilder.create()
                alertDialog.show()
                break
            }
        }
    }
```

## 小结
写的有点长了，总的来说，我感觉写下来逻辑还是挺清楚的，就是有的地方写得很啰嗦、有的地方代码抄的有点多、有的地方因为内容多又跳过了很多内容，算是学习的一个纪录吧。