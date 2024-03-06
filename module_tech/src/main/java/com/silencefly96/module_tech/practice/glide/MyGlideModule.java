package com.silencefly96.module_tech.practice.glide;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;

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
