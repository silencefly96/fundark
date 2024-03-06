package com.silencefly96.module_tech.practice.glide;

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