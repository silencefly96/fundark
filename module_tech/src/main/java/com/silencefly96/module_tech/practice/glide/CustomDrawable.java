package com.silencefly96.module_tech.practice.glide;

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
            Log.d("TAG", "draw: " + getBounds());
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
