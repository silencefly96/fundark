package com.silencefly96.module_tech.practice.glide;

import androidx.annotation.NonNull;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.annotation.GlideExtension;
import com.bumptech.glide.annotation.GlideOption;
import com.bumptech.glide.annotation.GlideType;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.BaseRequestOptions;
import com.bumptech.glide.request.RequestOptions;

@GlideExtension
public class MyAppGlideExtension {
    // Size of mini thumb in pixels.
    private static final int MINI_THUMB_SIZE = 100;
    private static final RequestOptions DECODE_TYPE_GIF
            = RequestOptions.decodeTypeOf(GifDrawable.class).lock();
    private static final RequestOptions DECODE_TYPE_CUSTOM
            = RequestOptions.decodeTypeOf(CustomDrawable.class).lock();

    private MyAppGlideExtension() {}

    @NonNull
    @GlideOption
    public static BaseRequestOptions<?> miniThumb(BaseRequestOptions<?> options) {
        return options
                .fitCenter()
                .override(MINI_THUMB_SIZE);
    }

    @NonNull
    @GlideOption
    public static BaseRequestOptions<?> miniThumbSize(BaseRequestOptions<?> options, int size) {
        return options
                .fitCenter()
                .override(size);
    }

    @NonNull
    @GlideType(GifDrawable.class)
    public static RequestBuilder<GifDrawable> as2Gif(RequestBuilder<GifDrawable> requestBuilder) {
        return requestBuilder
                .transition(new DrawableTransitionOptions())
                .apply(DECODE_TYPE_GIF);
    }

    @NonNull
    @GlideType(CustomDrawable.class)
    public static RequestBuilder<CustomDrawable> asCustom(RequestBuilder<CustomDrawable> requestBuilder) {
        return requestBuilder
                .transition(new DrawableTransitionOptions())
                .apply(DECODE_TYPE_CUSTOM);
    }
}
