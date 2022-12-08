# fundark

## 代码结构
```
├── app
│   ├── module_base             #基础库
│   │   ├── base                    #Activity、Fragment、Dialog、Adapter基类
│   │   ├── crash                   #崩溃日志捕捉
│   │   ├── utils                   #工具类
│   ├── module_common           #通用功能库
│   │   ├── ext                     #kotlin扩展
│   │   ├── net                     #网络辅助类
│   │   ├── version                 #TODO 版本控制
│   │   ├── push                    #TODO 推送
│   ├── module_mvvm             #mvvm架构：计划app
│   ├── module_views            #自定义view
│   │   ├── custom                  #自定义view
│   │   ├── game                    #自定义view做的游戏
│   ├── module_hardware         #硬件功能
│   │   ├── bluetooth               #TODO 蓝牙
│   │   ├── socket                  #TODO socket本地网络
│   │   ├── camera                  #TODO 相机
│   │   ├── nfc                     #TODO NFC
│   │   ├── 传感器                   #TODO 传感器
│   │   ├── usb                     #TODO U盘
│   │   ├── 电源                     #TODO 电源管理
│   ├── module_media            #多媒体
│   ├── module_map              #地图功能
│   ├── module_aidl             #多进程
```