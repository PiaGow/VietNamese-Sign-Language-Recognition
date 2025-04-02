package com.example.cameraxvideorecorder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.media.Image;

import androidx.annotation.NonNull;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.annotation.OptIn;
import com.google.common.util.concurrent.ListenableFuture;

import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@OptIn(markerClass = ExperimentalGetImage.class)
public class MainActivity extends AppCompatActivity {
    ExecutorService service;
    ImageButton toggleFlash, flipCamera;
    PreviewView previewView;

    private TextView responseTextView;
    int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private ApiService apiService;

    private final List<Bitmap> frameBuffer = new ArrayList<>();
    private static final int FRAME_BATCH_SIZE = 70;

    private ProgressBar progressBar;

    // Thêm biến trạng thái xử lý
    private boolean isProcessing = false;

    private final ActivityResultLauncher<String> activityResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera(cameraFacing);
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        responseTextView = findViewById(R.id.responseTextView);
        previewView = findViewById(R.id.viewFinder);
        toggleFlash = findViewById(R.id.toggleFlash);
        flipCamera = findViewById(R.id.flipCamera);
        progressBar = findViewById(R.id.progressBar);

        service = Executors.newSingleThreadExecutor();

        // Khởi tạo Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://192.168.1.78:9898/api/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        apiService = retrofit.create(ApiService.class);

        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(Manifest.permission.CAMERA);
        } else {
            startCamera(cameraFacing);
        }

        flipCamera.setOnClickListener(view -> {
            if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
                cameraFacing = CameraSelector.LENS_FACING_FRONT;
            } else {
                cameraFacing = CameraSelector.LENS_FACING_BACK;
            }
            startCamera(cameraFacing);
        });
    }

    public void startCamera(int cameraFacing) {
        ListenableFuture<ProcessCameraProvider> processCameraProvider = ProcessCameraProvider.getInstance(MainActivity.this);

        processCameraProvider.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = processCameraProvider.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build();

                imageAnalysis.setAnalyzer(service, image -> {
                    // Bỏ qua frame nếu đang xử lý
                    if (isProcessing) {
                        image.close();
                        return;
                    }

                    Bitmap bitmap = imageProxyToBitmap(image);
                    if (bitmap != null) {
                        frameBuffer.add(bitmap);
                        runOnUiThread(() -> {
                            int progress = frameBuffer.size();
                            progressBar.setVisibility(View.VISIBLE);
                            progressBar.setProgress(progress);
                        });

                        // Gửi khi đủ số lượng frame
                        if (frameBuffer.size() >= FRAME_BATCH_SIZE) {
                            isProcessing = true; // Đặt trạng thái đang xử lý
                            sendFramesToServer(new ArrayList<>(frameBuffer)); // Gửi bản sao buffer
                            frameBuffer.clear(); // Xóa buffer
                        }
                    }

                    image.close();
                });

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(cameraFacing).build();

                cameraProvider.unbindAll();
                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

                toggleFlash.setOnClickListener(view -> toggleFlash(camera));
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(MainActivity.this));
    }

    private void toggleFlash(Camera camera) {
        if (camera.getCameraInfo().hasFlashUnit()) {
            if (camera.getCameraInfo().getTorchState().getValue() == 0) {
                camera.getCameraControl().enableTorch(true);
                toggleFlash.setImageResource(R.drawable.round_flash_off_24);
            } else {
                camera.getCameraControl().enableTorch(false);
                toggleFlash.setImageResource(R.drawable.round_flash_on_24);
            }
        } else {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Flash is not available currently", Toast.LENGTH_SHORT).show());
        }
    }

    private Bitmap imageProxyToBitmap(@NonNull ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image == null || image.getFormat() != ImageFormat.YUV_420_888) {
            return null;
        }

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        int width = imageProxy.getWidth();
        int height = imageProxy.getHeight();

        Bitmap bitmap = BitmapUtils.getBitmapFromNv21(nv21, width, height);

        Matrix matrix = new Matrix();
        matrix.postRotate(imageProxy.getImageInfo().getRotationDegrees());

        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
    }

    private void sendFramesToServer(List<Bitmap> frames) {
        File videoFile = VideoUtils.createVideoFromFrames(frames, getCacheDir());
        if (videoFile == null) {
            Log.e("MainActivity", "Failed to create video file.");
            isProcessing = false; // Cho phép thu thập lại frame
            return;
        }

        RequestBody requestFile = RequestBody.create(MediaType.parse("video/mp4"), videoFile);
        MultipartBody.Part body = MultipartBody.Part.createFormData("video", videoFile.getName(), requestFile);

        apiService.uploadVideo(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    try {
                        String responseString = response.body() != null ? response.body().string() : "{}";
                        JSONObject jsonResponse = new JSONObject(responseString);
                        String predictions = jsonResponse.optString("predictions", "");

                        runOnUiThread(() -> {
                            responseTextView.setText(predictions);
                            progressBar.setVisibility(View.GONE);
                        });

                        // Chờ thêm 1 giây trước khi tiếp tục thu thập frame
                        service.execute(() -> {
                            try {
                                Thread.sleep(1000); // Chờ 1 giây
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            isProcessing = false; // Cho phép tiếp tục thu thập frame
                        });

                    } catch (Exception e) {
                        Log.e("MainActivity", "Error parsing response: " + e.getMessage());
                        runOnUiThread(() -> responseTextView.setText("Error parsing response."));
                        isProcessing = false;
                    }
                } else {
                    Log.e("MainActivity", "Upload failed: " + response.code());
                    runOnUiThread(() -> responseTextView.setText("Upload failed: " + response.code()));
                    isProcessing = false;
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("MainActivity", "Error: " + t.getMessage());
                runOnUiThread(() -> responseTextView.setText("Error: " + t.getMessage()));
                isProcessing = false;
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        service.shutdown();

    }
}