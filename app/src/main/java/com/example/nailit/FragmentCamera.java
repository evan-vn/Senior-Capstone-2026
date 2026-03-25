package com.example.nailit;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;

import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;

import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.Manifest;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.camera.view.PreviewView;

import com.example.nailit.data.model.Polish;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;


import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import java.util.List;
import java.util.concurrent.Executors;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.LinearGradient;
import android.graphics.Shader;

public class FragmentCamera extends Fragment {
    private PreviewView previewView;
    HandLandmarker handLandmarker;
    ImageView resultImage;
    ImageCapture imageCapture;
    Polish selectedPolish;
    String selectedHex;
    String thumbnailHex;
    Bitmap cachedPolishBitmap;

    private static final int  CAMERA_REQUEST_CODE= 100;

    Bitmap latestBitmap;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
       View v = inflater.inflate(R.layout.fragment_camera, container, false);
        previewView = v.findViewById(R.id.previewView);
        resultImage = v.findViewById(R.id.resultImage);


        previewView.setVisibility(View.VISIBLE);
        resultImage.setVisibility(View.VISIBLE);
        resultImage.setScaleType(ImageView.ScaleType.FIT_XY);

        setupHandModel();



       if(ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
           startCamera();
       }else{
           ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
       }


       return v;
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            selectedHex = args.getString("hex");
            thumbnailHex = args.getString("thumbnail");

            Log.d("COLOR_DEBUG", "selectedHex = " + selectedHex);
            Log.d("COLOR_DEBUG", "thumbnailHex = " + (thumbnailHex != null));
        }

        if (thumbnailHex != null) {
            byte[] bytes = hexToBytes(thumbnailHex);
            if (bytes != null) {
                cachedPolishBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                Log.d("THUMB_DEBUG", "Bitmap decoded: " + (cachedPolishBitmap != null));
                Log.d("THUMB_DEBUG", "bytes length = " + (bytes != null ? bytes.length : 0));
                Log.d("THUMB_DEBUG", "bitmap = " + cachedPolishBitmap);
            }
        }
    }

    private byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() < 4) return null;

        if (hex.startsWith("\\x") || hex.startsWith("\\X")) {
            hex = hex.substring(2);
        }

        int len = hex.length();
        if (len % 2 != 0) return null;

        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) return null;

            data[i / 2] = (byte) ((hi << 4) + lo);
        }

        return data;
    }



    private void startCamera() {

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(getContext());

        cameraProviderFuture.addListener(() -> {

            try {

                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(new Size(480, 640)) // smaller resolution = faster
                        .build();

                imageAnalysis.setAnalyzer(
                        Executors.newSingleThreadExecutor(),
                        imageProxy -> {
                            processFrame(imageProxy);  // your method to detect & color nails
                            imageProxy.close();        // very important to avoid blocking camera
                        }
                );
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();

                CameraSelector cameraSelector =
                        CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageCapture,   // optional, keep if you want capture button
                        imageAnalysis   // live frame analyzer
                );

            } catch (Exception e) {
                e.printStackTrace();
            }

        }, ContextCompat.getMainExecutor(getContext()));
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 100 &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            startCamera();
        }
    }

    private void setupHandModel() {
        BaseOptions baseOptions =
                BaseOptions.builder()
                        .setModelAssetPath("hand_landmarker.task")
                        .build();

        HandLandmarker.HandLandmarkerOptions options =
                HandLandmarker.HandLandmarkerOptions.builder()
                        .setBaseOptions(baseOptions)
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setNumHands(1)
                        .setResultListener((result, inputImage) -> {

                            if (latestBitmap == null) return;

                            Bitmap output = drawNails(latestBitmap, result);

                            requireActivity().runOnUiThread(() ->
                                    resultImage.setImageBitmap(output));
                        })

                        .build();
        handLandmarker = HandLandmarker.createFromOptions(requireContext(), options);
    }






    private Bitmap imageProxyToBitmap(ImageProxy image) {

        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21,
                image.getWidth(), image.getHeight(), null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuv.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
        byte[] bytes = out.toByteArray();

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
    private void processFrame(ImageProxy image) {

        if (handLandmarker == null) {
            return;
        }

        Bitmap bitmap = imageProxyToBitmap(image);

        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        latestBitmap = bitmap;

        MPImage mpImage = new BitmapImageBuilder(bitmap).build();

        handLandmarker.detectAsync(mpImage, System.currentTimeMillis());

        image.close();
    }




    private Bitmap drawNails(Bitmap bitmap, HandLandmarkerResult result) {

        Bitmap mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutable);

        if (result.landmarks().isEmpty())
            return mutable;

        List<NormalizedLandmark> landmarks = result.landmarks().get(0);

        // Smooth bitmap rendering
        Paint bitmapPaint = new Paint();
        bitmapPaint.setAntiAlias(true);
        bitmapPaint.setFilterBitmap(true);
        bitmapPaint.setDither(true);

        int[] tips = {4, 8, 12, 16, 20};
        int[] pips = {3, 7, 11, 15, 19};

        // Improved sizes (more natural)
        float[] widths  = {28, 22, 24, 22, 18};
        float[] heights = {16, 12, 14, 12, 10};

        float forwardFactor = 0.5f; // better placement

        for (int i = 0; i < tips.length; i++) {

            int tip = tips[i];
            int pip = pips[i];

            float tipX = landmarks.get(tip).x() * bitmap.getWidth();
            float tipY = landmarks.get(tip).y() * bitmap.getHeight();
            float pipX = landmarks.get(pip).x() * bitmap.getWidth();
            float pipY = landmarks.get(pip).y() * bitmap.getHeight();

            float dx = tipX - pipX;
            float dy = tipY - pipY;

            float cx = tipX + dx * forwardFactor;
            float cy = tipY + dy * forwardFactor;

            //Move thumb nail downward
            if(i==0){
                cy += 6;
            }

            float angle = (float) Math.toDegrees(Math.atan2(dy, dx));

            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(angle);

            float w = widths[i];
            float h = heights[i];

            RectF rectF = new RectF(-w, -h, w, h);

// Nail shape
            Path nailPath = new Path();
            nailPath.addRoundRect(rectF, w * 0.8f, h * 0.8f, Path.Direction.CW);

            canvas.save();
            canvas.clipPath(nailPath);




//            if (cachedPolishBitmap != null) {
//
//              
//                Rect dst = new Rect((int)-w, (int)-h, (int)w, (int)h);
//
//                Paint texturePaint = new Paint(bitmapPaint);
//                texturePaint.setAlpha(230); // slight transparency
//
//                canvas.drawBitmap(cachedPolishBitmap, null, dst, texturePaint);
//
//                Paint glossPaint = new Paint();
//                glossPaint.setShader(new LinearGradient(
//                        -w, -h,
//                        w, h,
//                        Color.argb(80, 255, 255, 255),
//                        Color.TRANSPARENT,
//                        Shader.TileMode.CLAMP
//                ));
//                canvas.drawOval(rectF, glossPaint);
//            }
            if (selectedHex != null) {

                Paint basePaint = new Paint();
                basePaint.setColor(Color.parseColor(selectedHex));
                basePaint.setAntiAlias(true);

                // 👉 FULL solid fill (NO shadow)
                canvas.drawOval(rectF, basePaint);

                // 👉 VERY LIGHT gloss (don’t change color)
                Paint glossPaint = new Paint();
                glossPaint.setShader(new LinearGradient(
                        0, -h,
                        0, 0,
                        Color.argb(60, 255, 255, 255),
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                ));
                canvas.drawOval(rectF, glossPaint);
            }

            canvas.restore();

            Paint edgePaint = new Paint();
            edgePaint.setStyle(Paint.Style.STROKE);
            edgePaint.setColor(Color.argb(80, 0, 0, 0));
            edgePaint.setStrokeWidth(1.2f);
            edgePaint.setAntiAlias(true);

            canvas.drawOval(rectF, edgePaint);

            canvas.restore();
        }

        return mutable;
    }


}
