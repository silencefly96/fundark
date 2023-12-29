# fundark

## 代码结构
```
├── app
│   ├── buildSrc                # 放些简单gradle插件
│   ├── build-pllugins          # gradle插件，独立工程
│   │   ├── optimize                # 优化.gradle文件编写
│   │   ├── privacy                 # 处理隐私协议整改问题(ASM)
│   ├── module_base             # 基础库
│   │   ├── base                    # Activity、Fragment、Dialog、Adapter基类
│   │   ├── crash                   # 崩溃日志捕捉
│   │   ├── utils                   # 工具类
│   │   ├── ext                     # kotlin扩展
│   │   ├── massage                 # 短信验证码获取
│   │   ├── net                     # TODO 网络辅助类
│   ├── module_mvvm             # mvvm架构：计划app
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
│   │   ├── face                    # TODO 人脸识别
│   ├── module_media            # TODO 多媒体
│   ├── module_third            # 第三方库源码解析
│   │   ├── arouter                 # TODO
│   │   ├── cordova                 # cordova解析
│   │   ├── okhttp3                 # okhttp3解析
│   │   ├── glide                   # glide解析
│   │   ├── volley                  # volley解析
│   │   ├── enventbus               # TODO
│   │   ├── leakcanary              # TODO
│   │   ├── mmkv                    # TODO
│   │   ├── retrofit                # TODO
│   │   ├── rxjava                  # TODO
│   ├── module_tech             # 技术和实践
│   │   ├── tech                     # 一些实践和基础功能
│   │   ├── tool                     # 一些工具类代码
```