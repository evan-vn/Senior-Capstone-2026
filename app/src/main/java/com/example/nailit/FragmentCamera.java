package com.example.nailit;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.Manifest;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.camera.view.PreviewView;

import com.example.nailit.Utility.Helper;
import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FragmentCamera extends Fragment {
    private PreviewView previewView;
    private Helper helper;
    private static final int  CAMERA_REQUEST_CODE= 100;
    private static final int MODEL_DIMENTION=480;
    private OverlayView overlayView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
       View v = inflater.inflate(R.layout.fragment_camera, container, false);
       previewView = v.findViewById(R.id.cameraPreview);
       overlayView = v.findViewById(R.id.overlay);
       //Call loading model
        helper = new Helper();
        helper.loadModel(requireContext());

       if(ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
           startCamera();
       }else{
           ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
       }


       return v;
    }

    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();

    private  void startCamera(){
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try{
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                //Image Analysis
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);


            }catch(Exception e){
                e.printStackTrace();
            }
        },ContextCompat.getMainExecutor(requireContext()));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            startCamera();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    private void analyzeImage(ImageProxy imageProxy) {
        try {
            Bitmap bitmap = imageProxyToBitmap(imageProxy);
            if (bitmap == null) return;


            int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
            Bitmap square = Bitmap.createBitmap(
                    bitmap,
                    (bitmap.getWidth() - size) / 2,
                    (bitmap.getHeight() - size) / 2,
                    size,
                    size
            );
            //
            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);

            Bitmap rotated = Bitmap.createBitmap(
                    square,
                    0, 0,
                    square.getWidth(),
                    square.getHeight(),
                    matrix,
                    true
            );


            Bitmap resized = Bitmap.createScaledBitmap(rotated, MODEL_DIMENTION, MODEL_DIMENTION, true);

            ByteBuffer inputBuffer = convertBitmapToInput(resized);
            inputBuffer.rewind();
            Interpreter tflite = helper.getInterpreter();
            if (tflite == null) return;

            float[][][] output = new float[1][20][4725];
            tflite.run(inputBuffer, output);

            // ----------------------
        // TEST A — Max objectness
            // ----------------------
            float maxObj = 0f;
            for (int i = 0; i < 4725; i++) {
                maxObj = Math.max(maxObj, output[0][4][i]);
            }
            Log.d("YOLO", "maxObj=" + maxObj);

        // ----------------------
        // 🔍 TEST B — Inspect raw x,y,w,h for anchor 0
        // ----------------------
            Log.d("YOLO", "x0=" + output[0][0][0]);
            Log.d("YOLO", "y0=" + output[0][1][0]);
            Log.d("YOLO", "w0=" + output[0][2][0]);
            Log.d("YOLO", "h0=" + output[0][3][0]);

            Log.d("YOLO", "out[0][4][0]=" + output[0][4][0]);
            Log.d("YOLO", "out[0][4][100]=" + output[0][4][100]);
            Log.d("YOLO", "out[0][4][1000]=" + output[0][4][1000]);


            List<RectF> boxes = processYOLOOutput(output, 0.5f, 0.45f);
            Log.d("YOLO", "detections=" + boxes.size());
            // Scale boxes to screen size
            float scaleX = (float) overlayView.getWidth() / MODEL_DIMENTION;
            float scaleY = (float) overlayView.getHeight() / MODEL_DIMENTION;

            List<RectF> scaled = new ArrayList<>();
            for (RectF b : boxes) {
                scaled.add(new RectF(
                        b.left * scaleX,
                        b.top * scaleY,
                        b.right * scaleX,
                        b.bottom * scaleY
                ));
            }
            Log.d("YOLO", "bitmap=" + bitmap.getWidth() + "x" + bitmap.getHeight());
            Log.d("YOLO", "square=" + square.getWidth() + "x" + square.getHeight());
            Log.d("YOLO", "resized=" + resized.getWidth() + "x" + resized.getHeight());

            requireActivity().runOnUiThread(() -> overlayView.setBoxes(scaled));

            requireActivity().runOnUiThread(() -> overlayView.setBoxes(scaled));

        } finally {
            imageProxy.close();
        }
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

        YuvImage yuvImage = new YuvImage(
                nv21,
                ImageFormat.NV21,
                image.getWidth(),
                image.getHeight(),
                null
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
        byte[] jpegBytes = out.toByteArray();

        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);

        return bitmap;
    }

    private ByteBuffer convertBitmapToInput(Bitmap bitmap) {
        ByteBuffer input = ByteBuffer.allocateDirect(1 * MODEL_DIMENTION * MODEL_DIMENTION * 3 * 4);
        input.order(ByteOrder.nativeOrder());

        for (int y = 0; y < MODEL_DIMENTION; y++) {
            for (int x = 0; x < MODEL_DIMENTION; x++) {
                int pixel = bitmap.getPixel(x, y);

                float r = ((pixel >> 16) & 0xFF) / 255f;
                float g = ((pixel >> 8) & 0xFF) / 255f;
                float b = (pixel & 0xFF) / 255f;

                input.putFloat(r);
                input.putFloat(g);
                input.putFloat(b);
            }
        }
        return input;
    }
    private List<RectF> processYOLOOutput(float[][][] output, float confThreshold, float iouThreshold) {
        Log.d("YOLO","Inside process");
        List<Detection> detections = new ArrayList<>();

        for (int i = 0; i < 4725; i++) {
            float obj = output[0][4][i];
              // objectness score
            if (obj < confThreshold) continue;

            float x = output[0][0][i];
            float y = output[0][1][i];
            float w = output[0][2][i];
            float h = output[0][3][i];


            float left = x - w / 2f;
            float top = y - h / 2f;
            float right = x + w / 2f;
            float bottom = y + h / 2f;
            Log.d("YOLO", "obj=" + obj + " x=" + x + " y=" + y + " w=" + w + " h=" + h);
            detections.add(new Detection(new RectF(left, top, right, bottom), obj));
        }

        return nonMaxSuppression(detections, iouThreshold);
    }
    private List<RectF> nonMaxSuppression(List<Detection> dets, float iouThreshold) {
        dets.sort((a, b) -> Float.compare(b.score, a.score));

        List<RectF> results = new ArrayList<>();

        boolean[] removed = new boolean[dets.size()];

        for (int i = 0; i < dets.size(); i++) {
            if (removed[i]) continue;

            RectF a = dets.get(i).box;
            results.add(a);

            for (int j = i + 1; j < dets.size(); j++) {
                if (removed[j]) continue;

                RectF b = dets.get(j).box;
                if (iou(a, b) > iouThreshold) {
                    removed[j] = true;
                }
            }
        }

        return results;
    }

    private float iou(RectF a, RectF b) {
        float interLeft = Math.max(a.left, b.left);
        float interTop = Math.max(a.top, b.top);
        float interRight = Math.min(a.right, b.right);
        float interBottom = Math.min(a.bottom, b.bottom);

        float interArea = Math.max(0, interRight - interLeft) * Math.max(0, interBottom - interTop);
        float areaA = (a.right - a.left) * (a.bottom - a.top);
        float areaB = (b.right - b.left) * (b.bottom - b.top);

        return interArea / (areaA + areaB - interArea);
    }

    private static class Detection {
        RectF box;
        float score;

        Detection(RectF b, float s) {
            box = b;
            score = s;
        }
    }


}
