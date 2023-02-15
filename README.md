# fundark

## 代码结构
```
├── app
│   ├── module_base             #基础库
│   │   ├── base                    #Activity、Fragment、Dialog、Adapter基类
│   │   ├── crash                   #崩溃日志捕捉
│   │   ├── utils                   #工具类
│   │   ├── ext                     #kotlin扩展
│   │   ├── net                     #TODO 网络辅助类
│   ├── module_mvvm             #mvvm架构：计划app
│   ├── module_views            #自定义view
│   │   ├── custom                  #自定义view
│   │   ├── game                    #自定义view做的游戏
│   ├── module_hardware         #硬件功能
│   │   ├── bluetooth               #TODO 蓝牙
│   │   ├── socket                  #TODO socket本地网络
│   │   ├── camera                  #TODO 相机
│   │   ├── nfc                     #TODO NFC
│   │   ├── sensor                  #TODO 传感器
│   │   ├── usb                     #TODO U盘
│   │   ├── power                   #TODO 电源管理
│   │   ├── face                    #TODO 人脸识别
│   ├── module_media            #TODO 多媒体
│   ├── module_map              #TODO 地图功能
│   ├── module_aidl             #TODO 多进程
```