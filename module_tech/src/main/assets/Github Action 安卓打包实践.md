# Github Action 安卓打包实践
## 前言
Github Action的大名听了很久了，最近终于有时间用自己练手的仓库试试给Android打包、发版，后面发现打包时间有点久，又给工作流加了个结束时通过邮件通知我的功能，基本是网上现成的例子，不过烨踩了一些坑，加上觉得还是很有意思的，就记录下吧！

## 简单说明
这里我觉得还是要简单说明下Github Action到底有什么用，这个一开始我懵逼了很久。

首先Github Action是GitHub提供的持续集成（Continuous Integration，简称 CI）服务，支持构建、测试、打包、部署项目等等操作。

GitHub提供服务器虚拟机给我们用，我们可以用yml来描述我们如何用它的虚拟机处理我们的代码仓库，还能键产物部署到仓库的assets去，像APK可以提供下载。

虚拟机处理的过程可以通过Action和shell来描述，GitHub的Action是独立的命令，而且GitHub提供了[Action市场](https://github.com/marketplace?category=&type=actions)，我们可以从上面找到我们要的功能。

将一系列Action和shell穿起来，并行或者串行执行，实际和我们人工操作没什么区别了，只不过它是用yml描述的，还更精准，不容易出错。

好了， 有了简单的理解，我们就能知道这个工具可以干嘛。

## 参考文章
下面内容基本上参考下面几篇文章做出来的，原创性不高，所以先贴别人文章，可能我还讲不到这么细:

[Android 项目使用 Github Actions 实现自动打包发布](https://ameow.xyz/archives/android-cicd-with-github-actions)

[针不戳！GitHub Actions 入坑指南](https://juejin.cn/post/6960126908180725773)

[Workflow syntax for GitHub Actions](https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions#jobsjob_idservicesservice_idenv)

## 打包安卓
其实打包安卓的代码和参考文章第一篇差不多，我加了写注释，改了下签名异常的BUG，先贴一下代码，后面讲几个我觉得要注意的地方:
> .github/workflows/android.yml
```yml
name: Release

# 触发条件: 这里是push以”v“开头的tag触发
on:
  push:
    # 根据branch或者tags来触发
    branches:
      - main

    tags:
      - "v*"
    # 忽略的标签类型，不触发构建流程(和tags不能同时写)
    # tags-ignore:
    #   - "d*"

# strategy:
  # 矩阵使用: ${{ matrix.os }}
  # 下面两个矩阵，会代码就会执行 2 * 3 = 6次
  # matrix:
  #  os: [ubuntu-16.04, ubuntu-18.04]
  #  node: [6, 8, 10]

# 一次持续集成的运行，可以完成多个任务(下面就build)
jobs:
  builds:
    # 构建的系统(ubuntu、windows、macos)
    runs-on: ubuntu-latest

    # 可以依赖其他job
    # needs: xxxJob、[job1, job2]

    # 构建权限，ncipollo/release-action需要使用
    permissions:
      contents: write

    # 操作步骤(按顺序执行)
    steps:

      # 拉取仓库代码
      - uses: actions/checkout@v3

      # 设置Java运行环境(temurin是开源jdk)，可以设置为: 1.8
      - uses: actions/setup-java@v3
        with:
          distribution: temurin
          java-version: 11

      # 设置gradle环境(比如: 6.5)、打包命令
      - uses: gradle/gradle-build-action@v2
        with:
          gradle-version: current
          arguments: assembleRelease

      # 签名apk
      - uses: r0adkll/sign-android-release@v1
        name: Sign app APK
        id: sign_app
        with:
          releaseDirectory: app/build/outputs/apk/release
          signingKeyBase64: ${{ secrets.SIGNING_KEY }}
          alias: ${{ secrets.ALIAS }}
          keyStorePassword: ${{ secrets.KEY_STORE_PASSWORD }}
          keyPassword: ${{ secrets.KEY_PASSWORD }}
        # 还能设置build-tools version，29.0.3好像不支持了
        env:
          # override default build-tools version (29.0.3) -- optional
          BUILD_TOOLS_VERSION: "34.0.0"

      # build-tools可能不存在，用step查看下支持哪些
      - run: ls /usr/local/lib/android/sdk/build-tools/

      # 重命名apk
      # GITHUB_REF_NAME是“触发workflow的分支或tag名称”
      - run: mv ${{steps.sign_app.outputs.signedReleaseFile}} fundark_$GITHUB_REF_NAME.apk
        # 可以指定shell命令的执行目录
        # working-directory: ./temp
        # 只当shell的类型
        # shell: bash

      # 发布
      - uses: ncipollo/release-action@v1
        with:
          # 要包含到Release Assets中的文件
          artifacts: "*.apk"
          # 会使用一个临时的token来创建Release
          token: ${{ github.token }}
          # 自动生成一些变化列表之类的内容。
          generateReleaseNotes: true
```
在根目录下创建“.github/workflows/android.yml”文件，文件名随意，差不多填入上面内容，改下输出文件名，然后的话要去github上设置secret就可以了。

下面来讲下注意点。

### 触发条件
触发条件就是我们在github上面打包等脚本触发的条件，这里on有好多好多情况，我们这就看下branches和tags，细究的话看官方文档吧，如下:

- [官方文档 - on](https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions#on)

这有个坑，branches和tags可以同时设置，但是tags和tags-ignore不能同时设置，也就是说只能什么版本打包、什么版本不打包里面二选一:
```yml
# 触发条件: 这里是push以”v“开头的tag触发
on:
  push:
    # 根据branch或者tags来触发
    branches:
      - main

    tags:
      - "v*"
    # 忽略的标签类型，不触发构建流程(和tags不能同时写)
    # tags-ignore:
    #   - "d*"
```

### Strategy策略
也就是GitHub可以提供很多总环境，一起打包这一份代码，它可以通过matrix定义:
```yml
# strategy:
  # 矩阵使用: ${{ matrix.os }}
  # 下面两个矩阵，会代码就会执行 2 * 3 = 6次
  # matrix:
  #  os: [ubuntu-16.04, ubuntu-18.04]
  #  node: [6, 8, 10]
```
注意下这里多个不同变量的环境会相乘，而且啊GitHub的免费是有限制的，别环境太多把额度用完了:

![image.png](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/741c29e8578c41058c08517470a30f64~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1304&h=625&s=92667&e=png&b=121212)

### jobs
这里的jobs就是我们要执行的任务，可以定义多个job，他们可以并行运行，还可以依赖于其他job，感觉很像gradle的task，有一点区别的就是每一个job都会使用新的虚拟机，并安装相应的环境。

![image.png](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/dd91fdac2142467cbb3943d7389647f4~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1920&h=892&s=160866&e=png&b=262b31)

这里多写几个job有一点好处，那就是一个job出错了，可以重新运行该job，而不需要把整个流程运行一遍:

![image.png](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/c6499f36df7744698c92246c31b77f7f~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1920&h=892&s=147791&e=png&b=23272a)

### 构建的系统
前面已经知道了GitHub是通过虚拟机来帮助我们处理代码的，那虚拟机是什么配置呢？看图:

![a3aff0b45214083cefaff2ba90d824c.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/47171bcd7042494d866f7485d7209035~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1312&h=658&s=74524&e=png&b=121212)

还不错哦，别看上面mac的配置更好，但是mac只有免费的500分钟，还是乖乖用其他的吧。

### 构建权限
构建权限这块也有好多概念，还是看下官方文档吧:

[官方文档](https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions#permissions)

```yml
# 构建权限，ncipollo/release-action需要使用
permissions:
  contents: write
```

在Android打包过程最后的发布，要用到contents的write权限，可能是要把apk放到仓库去吧，是“ncipollo/release-action”这个action要求的，仓库说明如下:

[仓库说明](https://github.com/marketplace/actions/create-release)

### 操作步骤steps
这里和job类似，只不过steps运行在同一个虚拟机，同一个环境里面，而且是按顺序执行的，比如“gradle/gradle-build-action@v2”安装的Android SDK tools，在后面“r0adkll/sign-android-release@v1”就会用到，我们还能查看这些环境:

![image.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/76f745a4aa5f4f44802db1d041ce94fa~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1920&h=892&s=235151&e=png&b=252a30)

![image.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/5ccfb61e1d76482c97ad56914f80f4e3~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1920&h=892&s=134702&e=png&b=252a30)

说白了，这里的steps就是按顺序执行action和shell命令，action可以在action市场找，按它说明用就行，比如Java环境:
```yml
  # 设置Java运行环境(temurin是开源jdk)，可以设置为: 1.8
  - uses: actions/setup-java@v3
    with:
      distribution: temurin
      java-version: 11
```
对于shell，要使用run命令执行，可以指定目录和shell的类型，配合GPT写shell，还是异常强大的^_^:
```yml
  # 重命名apk
  # GITHUB_REF_NAME是“触发workflow的分支或tag名称”
  - run: mv ${{steps.sign_app.outputs.signedReleaseFile}} fundark_$GITHUB_REF_NAME.apk
    # 可以指定shell命令的执行目录
    # working-directory: ./temp
    # 指定shell的类型
    # shell: bash
```

### Secrets
就像Android项目里面有些不应该暴漏的变量我们放local.properties里面一样，GitHub也能把变量写到仓库的Secrets里面去，设置如下:

![image.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/0a02219459cc4fbebc536d319bc799a2~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1920&h=892&s=185944&e=png&b=131313)


![image.png](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/d29b4a75a24748f39216fe964a933145~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1920&h=892&s=127711&e=png&b=131313)

这里“r0adkll/sign-android-release@v1”要求通过signingKeyBase64传入base64加密后的密钥字符串，可以看下它文档的说明:

[仓库说明](https://github.com/marketplace/actions/sign-android-release)

![image.png](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/7c47e32fcae34f7fbba413a76f576d88~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1343&h=468&s=64647&e=png&b=121212)

我们不想公开的内容，都可以放仓库的Secrets去，而且在action里面可以正常用。

### 发布到仓库
以前用过很多软件，都可以直接通过GitHub跳转到它的各个版本下载，有了上面这些action，我们也能在我们的GitHub仓库发布自己的版本:

![image.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/59f0e9c3165547fcb0f5c36284366c8f~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1920&h=892&s=132609&e=png&b=121212)

## 发送短信通知
上面的打包过程，我这项目要花大概十三分钟左右，说实话有点慢，又看到别人说action还能发送邮件，又花了点时间搞了个邮件通知:
```yml
  # 增加个notice的job，在打包完成后发邮件通知
  notice:
    runs-on: ubuntu-latest

    # 依赖于build
    needs: builds

    # 操作步骤
    steps:
      - name: Send email
        uses: dawidd6/action-send-mail@v3
        with:
          # 邮箱配置，密码是SMTP服务的授权密码
          server_address: smtp.qq.com
          server_port: 465
          username: ${{secrets.MAIL_USERNAME}}
          password: ${{secrets.MAIL_PASSWORD}}

          # 邮件内容
          subject: Github Actions job result
          from: ${{secrets.MAIL_USERNAME}}
          to: ${{secrets.MAIL_TOUSERNAME}}
          body: Build job of ${{github.repository}} completed successfully!
```
比较简单，用法上面基本都有提到过了，重点就是要用needs依赖下上面“builds” job，简单说下邮箱使用的一些问题吧。

### 发送邮件Action
这里用的是"dawidd6/action-send-mail@v3"这个action，它的仓库地址如下，里面也有详细的说明:

[仓库地址](https://github.com/dawidd6/action-send-mail)

我们要填的也就是上面代码的两部分，第一部分是账号信息，第二部分是邮件信息，邮件信息没什么好说的，看下QQ邮箱的设置吧。

### QQ邮箱设置
邮箱配置那要我们填四个信息，服务地址和服务端口，我们可以看下QQ邮箱的说明:

![image.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/91b45889ea45413b96d21fa9bf3fe6d0~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1270&h=545&s=91672&e=png&b=121212)

我们这是用来发短信的，选SMTP服务，端口465，用户名这个就是完整的QQ邮箱地址了，比如:

> 2xxxxxxx@qq.com

这样前面三个我们就填好了，剩下的password比较麻烦点，它是SMTP服务的授权密钥，要去QQ邮箱开通SMTP服务并拿到这个授权密钥:

![image.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/a3d47134d1f54293ac21b75c9a577657~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1605&h=770&s=110558&e=png&b=202529)

![image.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/b32113f7e8234e95b0c057d82f817da4~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1603&h=706&s=117147&e=png&b=1f2428)

![image.png](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/dc3b74e3f75046fcae041ba87a0346ce~tplv-k3u1fbpfcp-jj-mark:0:0:0:0:q75.image#?w=1920&h=892&s=107556&e=png&b=131313)

中间还要发短信什么的，还有点麻烦，拿到这些信息后去GitHub的Secrets上面填就行了，别填错了。

## 源码参考
这个源码就在我练手的仓库，可以正常打包和发邮件:

[Demo](https://github.com/silencefly96/fundark/blob/main/.github/workflows/android.yml)

## 小结
这篇文章用GitHub的action給我练手的Android项目打包并发布，还通过配置QQ邮箱，在发布结束后通过邮件通知，又学了点新东西！