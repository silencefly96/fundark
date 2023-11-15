package com.silencefly96.module_tech.tool.glide;

import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.SimpleResource;

import java.nio.ByteBuffer;

public class CustomDrawableBufferDecoder implements ResourceDecoder<ByteBuffer, CustomDrawable> {

    @Override
    public boolean handles(@NonNull ByteBuffer source, @NonNull Options options) {
        String str = String.valueOf(source);
        return str.startsWith("===") && str.endsWith("===");
    }

    @Override
    public Resource<CustomDrawable> decode(@NonNull ByteBuffer source, int width, int height, @NonNull Options options) {
        return new SimpleResource<>(new CustomDrawable(String.valueOf(source)));
    }
}
