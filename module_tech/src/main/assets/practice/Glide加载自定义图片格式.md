# Glide加载自定义图片格式
## 前言
最近在看Glide的源码，写到Glide的扩展卡住了，是关于Glide图片格式扩展功能方面的，找了挺多资料，发现对Glide图片格式扩展的文章很少，于是想自己下场试试！

## 加载逻辑
Glide数据加载大致有三个流程: 数据加载 -> 解码 -> 转码，分别对应:
- [ModelLoader](https://muyangmin.github.io/glide-docs-cn/tut/custom-modelloader.html): 将url(举例，实际范围更大，load(xxx)方法中参数)转为InputStream或者ByteBuffer
- ResourceDecoder: 将ModelLoader数据解码，比如转为File、Bitmap、Drawable之类的
- ResourceTranscoder: 将一种类型转为另一种类型，比如File转为Drawable

ModelLoader的内容可以点进链接去看下官方文档，比较长，下面用到再说。ResourceDecoder和ResourceTranscoder都差不多，可以在ResourceDecoder一步到位，也可以在ResourceTranscoder中再中转一下。

需要注意的是Glide的加载过程，会自动寻找注册好的ResourceDecoder和ResourceTranscoder，通过多次转换，转成你要的格式，中间的过程先忽略，我会写原理篇来解释，这篇文章来讲的是如何使用。

## 例子
对加载逻辑有一点了解后，我们就知道，要实现Glide格式扩展，我们至少得实现ResourceDecoder，下面来写一个例子。

### 自定义格式
我这假设我有一种新的格式“.custom”，里面格式如下:
> ===width,height,color===

大致就是定义了宽高和颜色，然后我们新建一个pic.custom文件，内容如下:
```
// pic.custom
===100,100,red===
```
根据你的需要，把文件放到我们Android项目的raw目录或者asset目录:
- raw目录: 通过R.raw.pic去访问
- asset目录: 通过"file:///android_asset/pic.custom"去访问

### 自定义Drawable
因为Glide最后是把Drawable加载到imageView的，所以我们要定义一个Drawable，用来展示我们的“.custom”格式，代码如下:
```
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

import androidx.annotation.NonNull;

public class CustomDrawable extends BitmapDrawable {

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final String source;

    private int width;
    private int height;
    private int color;

    private final Paint paint;

    private boolean parsed = false;

    @SuppressWarnings("deprecation")
    public CustomDrawable(String source) {
        this.source = source;
        this.paint = new Paint();
        paint.setStrokeWidth(5f);
        paint.setFlags(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        
        try {
            parseData(source);
            paint.setColor(color);
            parsed = true;
        }catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    private void parseData(String source) throws IllegalArgumentException {
        int ps = source.indexOf("===");
        int pe = source.lastIndexOf("===");
        if (ps >= 0 && pe >= 0 && ps != pe) {
            String data = source.substring(ps+3, pe);
            String[] arr = data.split(",");
            if (arr.length == 3) {
                // 解析出错也会抛出IllegalArgumentException异常
                width = Integer.parseInt(arr[0]);
                height = Integer.parseInt(arr[1]);
                color = Color.parseColor(arr[2]);
                return;
            }
        }
        throw new IllegalArgumentException("source parse fail");
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (parsed) {
            canvas.drawRect(getBounds(), paint);
        }
    }

    @Override
    public int getIntrinsicWidth() {
        return width;
    }

    @Override
    public int getIntrinsicHeight() {
        return height;
    }
}
``` 
代码不多，就是根据我们自定义的格式进行解析，并将数据应用到drawable上。我们先用setImageDrawable试一下，能正常显示:
```
binding.image.setImageDrawable(CustomDrawable("===100,100,red==="))
```
ps. 需要注意下的是，这里用了BitmapDrawable，刚开始写的时候直接继承的Drawable，居然加载失败了，Glide设置图片的时候需要一个BitmapDrawable，当然也可以加一个ResourceTranscoder处理下，我觉得不如直接给它继承BitmapDrawable算了，就是构造方法会提示被废弃，不管它了。

### 编写ResourceDecoder
完成上面两步后，我们就是要让Glide能够把资源处理成我们要的CustomDrawable格式。

这里选择把InputStream转为我们的CustomDrawable(一开始用的ByteBuffer好像有点问题):
```
import androidx.annotation.NonNull;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.SimpleResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class CustomDrawableDecoder implements ResourceDecoder<InputStream, CustomDrawable> {

    @Override
    public boolean handles(@NonNull InputStream source, @NonNull Options options) {
        String str = getStr(source);
        return str.startsWith("===") && str.endsWith("===");
    }

    @Override
    public Resource<CustomDrawable> decode(@NonNull InputStream source, int width, int height, @NonNull Options options) {
        return new SimpleResource<>(new CustomDrawable(getStr(source)));
    }

    private String getStr(InputStream source) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(source));
        StringBuilder sb = new StringBuilder();
        String line = "";
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }
}
```
需要继承两个方法，handles方法用来判断是不是能解析，能解析才交给这个ResourceDecoder处理，decode方法就是具体的解码了。

### 注册自定义解码器
完成上面操作后，我们要让Glide支持我们的格式，还要把解码器添加到Glide中去:
```
Glide.get(context)
   .registry.append(
      ByteBuffer::class.java, 
      CustomDrawable::class.java, 
      CustomDrawableBufferDecoder())
```
这里要在调用前添加，注意这里是使用的get方法来获取glide单例的，和with的区别是，get获取的是Glide对象的全局单例，而with方法获得的是RequestManager对象。

### 使用
接下来就是从raw文件夹中加载我们自定义的图片了:
```
Glide.with(context)
    .`as`(CustomDrawable::class.java)
    // .load("file:///android_asset/pic.custom")
    .load(R.raw.pic)
    .into(binding.image)
```
这里的as方法是kotlin里面的关键字，所以加了个引号，但是不影响使用，把APP安装到手机上发现就能生效了。

## 优化
上面使用还比较麻烦，我们可以结合Generated API一起使用，优化下，官网说明地址如下:
> 官方文档: https://muyangmin.github.io/glide-docs-cn/doc/generatedapi.html

具体原理与问题可以看我另一篇文章: [《Glide源码解析(3) Glide的扩展》]()，下面就默认注解生成功能已经能够正常使用了。

要对Glide扩展，首先我们要创建一个AppGlideModule:
```
import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;

import org.jetbrains.annotations.NotNull;

import java.io.InputStream;

// 定义扩展的名称
@GlideModule(glideName = "GlideAppExt")
public class MyGlideModule extends AppGlideModule {
    
    @Override
    public void registerComponents(@NonNull @NotNull Context context,
                                   @NonNull @NotNull Glide glide,
                                   @NonNull @NotNull Registry registry) {
        super.registerComponents(context, glide, registry);
        // 把我们自定义的解码器注册上
        registry.append(InputStream.class, CustomDrawable.class, new CustomDrawableDecoder());
    }
}
```
这里我们创建了一个AppGlideModule，并在registerComponents方法里面把我们自定义的解码器给注册上了。

另外，我们再创建个AppGlideExtension，用来优化我们as方法的使用:
```
import androidx.annotation.NonNull;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.annotation.GlideExtension;
import com.bumptech.glide.annotation.GlideType;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;

@GlideExtension
public class MyAppGlideExtension {
    private static final RequestOptions DECODE_TYPE_CUSTOM
            = RequestOptions.decodeTypeOf(CustomDrawable.class).lock();

    private MyAppGlideExtension() {}
    
    @NonNull
    @GlideType(CustomDrawable.class)
    public static RequestBuilder<CustomDrawable> asCustom(RequestBuilder<CustomDrawable> requestBuilder) {
        return requestBuilder
                .transition(new DrawableTransitionOptions())
                .apply(DECODE_TYPE_CUSTOM);
    }
}
```
这里我们就会给RequestBuilder通过GlideType注解添加一个asCustom方法，我们在Glide的load方法就可以直接使用:
```
GlideAppExt.with(it)
    .asCustom()
    .load(R.raw.pic)
    .into(binding.image)
```
运行下，OK了！是不是感觉比前面那样使用方便得多了，这里还有个GlideOption注解用来修改option的，我么们这没用到，有需要的可以了解下哈！

## Demo及源码
Demo及源码我已经提交到了github，有需要的可以参考下:
[Demo地址](https://github.com/silencefly96/fundark/blob/main/module_views/src/main/java/com/silencefly96/module_views/tool/demo/CustomGlideDemo.kt)、[部分源码地址](https://github.com/silencefly96/fundark/tree/main/module_views/src/main/java/com/silencefly96/module_views/tool/glide)


