package com.example.cameraxvideorecorder;

import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class VideoUtils {
    public static File createVideoFromFrames(List<Bitmap> frames, File cacheDir) {
        File outputFile = new File(cacheDir, "output_video.mp4");

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            // Chuyển đổi từng frame sang byte array và ghi vào file
            for (Bitmap frame : frames) {
                frame.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            }
            return outputFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
