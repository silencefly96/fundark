# fundark
用来练习 Android 各种功能，目标是用组件化构建一个桌面APP，里面展现练习模块的子APP，并能通过热更新动态加载。

项目包含自己编写的一些内容:
- Gradle插件
- 基类代码模块、通用代码模块
- 自定义view的练习模块
- 硬件使用的练习模块
- 多媒体使用的练习模块
- 独立APP实践: 计划app(mvvm)、日记app(mvi)
- 其他技术实践及文章总结模块

## 代码结构
```
├── app
│   ├── buildSrc                # 放些简单gradle插件
│   ├── build-pllugins          # gradle插件，独立工程(composingBuild)
│   │   ├── optimize                # 优化.gradle文件编写
│   │   ├── privacy                 # 处理隐私协议整改问题(ASM)
│   ├── module_base             # 基础库(原生库)
│   │   ├── base                    # Activity、Fragment、Dialog、Adapter基类
│   │   ├── crash                   # 崩溃日志捕捉
│   │   ├── ext                     # kotlin扩展
│   │   ├── utils                   # 工具类
│   ├── module_common           # 通用功能(包含第三方库)
│   │   ├── net                     # TODO 网络库(okhttp、retrofit、gson)
│   │   ├── persistence             # TODO 持久化(room、mmkv)
│   │   ├── push                    # TODO 推送(jiguang、xiaomi、huawei)
│   │   ├── scan                    # TODO 扫码(Zxing、OpenCV)
│   │   ├── share                   # TODO 分享(wechat、weibo、qq-zone、dingding)
│   │   ├── crash                   # TODO Crash监控、崩溃分析(Bugly)
│   │   ├── analysis                # TODO 统计分析(umeng)
│   │   ├── map                     # TODO 地图服务
│   │   ├── asr                     # TODO 语音转文字
│   │   ├── tts                     # TODO 文字转语音
│   │   ├── face                    # TODO 人脸识别
│   │   ├── webview                 # TODO 优化的webview
│   ├── module_views            # 自定义view
│   │   ├── custom                  # 自定义view
│   │   ├── game                    # 自定义view做的游戏
│   │   ├── widget                  # 自定义控件
│   ├── module_hardware         # 硬件功能
│   │   ├── bluetooth               # 蓝牙
│   │   ├── socket                  # TODO socket本地网络
│   │   ├── camera                  # 相机
│   │   ├── nfc                     # TODO NFC
│   │   ├── sensor                  # TODO 传感器
│   │   ├── usb                     # U盘
│   │   ├── power                   # TODO 电源管理
│   ├── module_media            # TODO 多媒体
│   │   ├── richtext                # TODO 富文本
│   │   ├── audio                   # TODO 音频
│   │   ├── live                    # TODO 直播
│   │   ├── picture                 # TODO 图片
│   │   ├── short                   # TODO 短视频
│   │   ├── video                   # TODO 视频
│   ├── module_mvvm             # mvvm架构：计划app(实践JetPack)
│   │   ├── mvvm                    # mvvm架构: lifecycle、liveData、viewModel、DataBinding
│   │   ├── navigation              # navigation导航跳转
│   │   ├── room                    # room数据库储存
│   │   ├── retrofit                # retrofit网络请求
│   │   ├── hilt                    # hilt依赖注入
│   │   ├── pager                   # pager数据分页
│   │   ├── slice                   # room数据库储存
│   ├── module_mvi              # TODO mvi架构：日记app(实践module_common、compose)
│   ├── module_tech             # 技术实践及文章总结
│   │   ├── practice                # 技术实践
│   │   ├── drawable                # Drawable、动画、过渡等
│   │   ├── activity                # activity生命周期、Intent Flag等实践
│   │   ├── service                 # 后台服务、通知、JobScheduler、WorkManager
│   │   ├── provider                # provider动态加载等
│   │   ├── source(asset)               # 源码解析系列文章
│   │   │   ├── arouter                 # TODO
│   │   │   ├── cordova                 # cordova解析
│   │   │   ├── okhttp3                 # okhttp3解析
│   │   │   ├── glide                   # glide解析
│   │   │   ├── volley                  # volley解析
│   │   │   ├── enventbus               # TODO
│   │   │   ├── leakcanary              # TODO
│   │   │   ├── mmkv                    # TODO
│   │   │   ├── retrofit                # TODO
│   │   │   ├── rxjava                  # TODO
│   │   ├── loopholes(asset)        # 漏洞修复系列文章
```