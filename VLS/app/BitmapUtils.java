package com.example.cameraxvideorecorder;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.YuvImage;

import java.io.ByteArrayOutputStream;

public class BitmapUtils {

    public static Bitmap getBitmapFromNv21(byte[] nv21, int width, int height) {
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, width, height), 100, outputStream);
        byte[] jpegData = outputStream.toByteArray();
        return android.graphics.BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
    }
}
