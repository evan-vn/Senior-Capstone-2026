package com.example.nailit;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.YuvImage;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.Manifest;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.nailit.data.model.Polish;
import com.example.nailit.data.model.TryOnViewModel;
import com.example.nailit.ui.NailShape;
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

public class FragmentCamera extends Fragment {

    private PreviewView previewView;
    private ImageView resultImage;
    private LinearLayout colorStrip;
    private LinearLayout shapeStrip;
    private ImageButton shutterBtn;
    private View flashOverlay;
    private ImageCapture imageCapture;
    HandLandmarker handLandmarker;

    String selectedHex;
    String selectedShadeName;
    Bitmap cachedPolishBitmap;
    Bitmap latestBitmap;
    Bitmap lastOutputBitmap;
    private LinearLayout galleryBtn;
    private ImageView galleryThumb;

    // Default shape is ROUND
    private NailShape selectedShape = NailShape.ROUND;
    private View lastSelectedShapeBtn = null;
    private android.net.Uri lastSavedUri = null;
    private static final int CAMERA_REQUEST_CODE = 100;
    private TryOnViewModel viewModel;
    private View lastSelectedSwatch = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_camera, container, false);
        previewView  = v.findViewById(R.id.previewView);
        resultImage  = v.findViewById(R.id.resultImage);
        colorStrip   = v.findViewById(R.id.colorStrip);
        shapeStrip   = v.findViewById(R.id.shapeStrip);
        shutterBtn   = v.findViewById(R.id.shutterBtn);
        flashOverlay = v.findViewById(R.id.flashOverlay);
        galleryBtn   = v.findViewById(R.id.galleryBtn);
        galleryThumb = v.findViewById(R.id.galleryThumb);

        galleryBtn.setOnClickListener(v2 -> openGallery());

        previewView.setVisibility(View.VISIBLE);
        resultImage.setVisibility(View.VISIBLE);
        resultImage.setScaleType(ImageView.ScaleType.FIT_XY);

        shutterBtn.setOnClickListener(v2 -> takeSnapshot());

        setupHandModel();

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    requireActivity(),
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_REQUEST_CODE);
        }

        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            selectedHex       = args.getString("hex");
            selectedShadeName = args.getString("shade_name");
            String imageUrl   = args.getString("image_url");
            loadSwatchBitmap(imageUrl);
        }

        viewModel = new ViewModelProvider(requireActivity()).get(TryOnViewModel.class);

        if (viewModel.cachedPolishes != null && !viewModel.cachedPolishes.isEmpty()) {
            buildColorStrip(viewModel.cachedPolishes);
        }

        buildShapeStrip();
        loadLatestThumbnail();
    }

    // ─── Gallery ─────────────────────────────────────────────────────────────────

    /** Opens the NailIt folder directly in the system gallery app. */
    private void openGallery() {
        // If we have a specific saved image, open it directly
        android.net.Uri uriToOpen = lastSavedUri;

        // If no URI in memory yet, query MediaStore for the latest NailIt image
        if (uriToOpen == null) {
            uriToOpen = queryLatestNailItUri();
        }

        if (uriToOpen == null) {
            Toast.makeText(getContext(), "No saved photos yet", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(uriToOpen, "image/jpeg");
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Cannot open image", Toast.LENGTH_SHORT).show();
        }
    }

    /** Queries MediaStore for the most recent NailIt image and returns its Uri. */
    private android.net.Uri queryLatestNailItUri() {
        try {
            String[] projection = {android.provider.MediaStore.Images.Media._ID};
            String selection;
            String[] selectionArgs;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                selection     = android.provider.MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
                selectionArgs = new String[]{"%" + "NailIt" + "%"};
            } else {
                selection     = android.provider.MediaStore.Images.Media.DATA + " LIKE ?";
                selectionArgs = new String[]{"%" + "NailIt" + "%"};
            }

            android.database.Cursor cursor = requireContext().getContentResolver().query(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    android.provider.MediaStore.Images.Media.DATE_ADDED + " DESC"
            );

            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                                android.provider.MediaStore.Images.Media._ID));
                cursor.close();
                return android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
            }
            if (cursor != null) cursor.close();
        } catch (Exception e) {
            Log.e("GALLERY", "Query failed", e);
        }
        return null;
    }

    /**
     * Loads the most recently saved NailIt image into the gallery thumbnail.
     * Called on launch and after every successful save.
     */
    private void loadLatestThumbnail() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                android.net.Uri collection =
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

                String[] projection = {
                        android.provider.MediaStore.Images.Media._ID,
                        android.provider.MediaStore.Images.Media.DATE_ADDED,
                        android.provider.MediaStore.Images.Media.DISPLAY_NAME
                };

                String selection;
                String[] selectionArgs;

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    selection     = android.provider.MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
                    selectionArgs = new String[]{"%" + "NailIt" + "%"};
                } else {
                    selection     = android.provider.MediaStore.Images.Media.DATA + " LIKE ?";
                    selectionArgs = new String[]{"%" + "NailIt" + "%"};
                }

                android.database.Cursor cursor = requireContext().getContentResolver().query(
                        collection,
                        projection,
                        selection,
                        selectionArgs,
                        android.provider.MediaStore.Images.Media.DATE_ADDED + " DESC"
                );

                if (cursor != null && cursor.moveToFirst()) {
                    long id = cursor.getLong(
                            cursor.getColumnIndexOrThrow(
                                    android.provider.MediaStore.Images.Media._ID));
                    android.net.Uri thumbUri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    cursor.close();

                    requireActivity().runOnUiThread(() ->
                            Glide.with(this)
                                    .load(thumbUri)
                                    .centerCrop()
                                    .placeholder(R.drawable.gallery_thumb_bg)
                                    .into(galleryThumb));
                } else {
                    if (cursor != null) cursor.close();
                    // No image yet — keep the empty placeholder
                }
            } catch (Exception e) {
                Log.e("GALLERY", "Failed to load thumbnail", e);
            }
        });
    }

    private void buildShapeStrip() {
        shapeStrip.removeAllViews();

        // Shape name shown on long press as a tooltip-style toast
        String[] labels  = {"Round", "Coffin", "Stiletto"};
        int[] icons = {
                R.drawable.ic_shape_round,
                R.drawable.ic_shape_coffin,
                R.drawable.ic_shape_stiletto
        };
        NailShape[] shapes = {
                NailShape.ROUND,
                NailShape.COFFIN,
                NailShape.STILETTO
        };

        int iconSize    = dpToPx(36);   // larger icon
        int btnPadding  = dpToPx(8);
        int marginV     = dpToPx(5);

        for (int i = 0; i < shapes.length; i++) {
            final NailShape shape = shapes[i];
            final String label    = labels[i];
            final int iconRes     = icons[i];

            // Pill-shaped button containing only the icon
            LinearLayout btn = new LinearLayout(requireContext());
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            btnParams.setMargins(0, marginV, 0, marginV);
            btn.setLayoutParams(btnParams);
            btn.setOrientation(LinearLayout.VERTICAL);
            btn.setGravity(android.view.Gravity.CENTER);
            btn.setPadding(btnPadding, btnPadding, btnPadding, btnPadding);

            // Background pill
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setShape(GradientDrawable.RECTANGLE);
            btnBg.setCornerRadius(dpToPx(12));
            boolean isSelected = shape == selectedShape;
            btnBg.setColor(isSelected
                    ? Color.argb(220, 255, 255, 255)
                    : Color.argb(80, 255, 255, 255));
            if (isSelected) {
                btnBg.setStroke(dpToPx(2), Color.WHITE);
            }
            btn.setBackground(btnBg);

            // Icon only — no text
            ImageView icon = new ImageView(requireContext());
            LinearLayout.LayoutParams iconParams =
                    new LinearLayout.LayoutParams(iconSize, iconSize);
            icon.setLayoutParams(iconParams);
            icon.setImageResource(iconRes);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setColorFilter(isSelected
                    ? Color.WHITE
                    : Color.argb(180, 255, 255, 255));

            btn.addView(icon);
            shapeStrip.addView(btn);

            if (isSelected) lastSelectedShapeBtn = btn;

            // Tap — select shape
            btn.setOnClickListener(v -> {
                // Deselect previous
                if (lastSelectedShapeBtn != null && lastSelectedShapeBtn != btn) {
                    GradientDrawable prevBg =
                            (GradientDrawable) lastSelectedShapeBtn.getBackground();
                    prevBg.setColor(Color.argb(80, 255, 255, 255));
                    prevBg.setStroke(0, Color.TRANSPARENT);
                    ImageView prevIcon =
                            (ImageView) ((LinearLayout) lastSelectedShapeBtn).getChildAt(0);
                    prevIcon.setColorFilter(Color.argb(180, 255, 255, 255));
                }
                // Highlight new
                btnBg.setColor(Color.argb(220, 255, 255, 255));
                btnBg.setStroke(dpToPx(2), Color.WHITE);
                icon.setColorFilter(Color.WHITE);
                lastSelectedShapeBtn = btn;
                selectedShape = shape;
            });

            // Long press — show shape name as a toast (replaces the text label)
            btn.setOnLongClickListener(v -> {
                Toast.makeText(requireContext(), label, Toast.LENGTH_SHORT).show();
                return true;
            });
        }
    }

    // ─── Color strip ─────────────────────────────────────────────────────────────

    private void buildColorStrip(List<Polish> polishes) {
        colorStrip.removeAllViews();

        int swatchSize  = dpToPx(44);
        int margin      = dpToPx(6);
        int borderWidth = dpToPx(2);

        for (Polish polish : polishes) {

            LinearLayout frame = new LinearLayout(requireContext());
            LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                    swatchSize + borderWidth * 2,
                    swatchSize + borderWidth * 2);
            frameParams.setMargins(margin, 0, margin, 0);
            frame.setLayoutParams(frameParams);
            frame.setPadding(borderWidth, borderWidth, borderWidth, borderWidth);
            frame.setGravity(android.view.Gravity.CENTER);

            GradientDrawable frameDrawable = new GradientDrawable();
            frameDrawable.setShape(GradientDrawable.OVAL);
            frameDrawable.setColor(Color.TRANSPARENT);
            frameDrawable.setStroke(borderWidth, Color.TRANSPARENT);
            frame.setBackground(frameDrawable);

            ImageView swatch = new ImageView(requireContext());
            swatch.setLayoutParams(new LinearLayout.LayoutParams(swatchSize, swatchSize));
            swatch.setScaleType(ImageView.ScaleType.CENTER_CROP);
            swatch.setClipToOutline(true);
            swatch.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });

            if (polish.getSwatchUrl() != null && !polish.getSwatchUrl().isEmpty()) {
                Glide.with(this)
                        .load(polish.getSwatchUrl())
                        .circleCrop()
                        .placeholder(makePlaceholder(polish.getHex(), swatchSize))
                        .error(makePlaceholder(polish.getHex(), swatchSize))
                        .into(swatch);
            } else {
                swatch.setImageDrawable(makePlaceholder(polish.getHex(), swatchSize));
            }

            frame.addView(swatch);
            colorStrip.addView(frame);

            if (polish.getHex() != null && polish.getHex().equals(selectedHex)) {
                frameDrawable.setStroke(borderWidth, Color.WHITE);
                lastSelectedSwatch = frame;
            }

            frame.setOnClickListener(v -> {
                if (lastSelectedSwatch != null) {
                    ((GradientDrawable) lastSelectedSwatch.getBackground())
                            .setStroke(borderWidth, Color.TRANSPARENT);
                }
                frameDrawable.setStroke(borderWidth, Color.WHITE);
                lastSelectedSwatch = frame;

                cachedPolishBitmap = null;
                selectedHex        = polish.getHex();
                selectedShadeName  = polish.getShadeName();
                loadSwatchBitmap(polish.getSwatchUrl());
            });
        }
    }

    private GradientDrawable makePlaceholder(String hex, int sizePx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setSize(sizePx, sizePx);
        try {
            d.setColor(Color.parseColor(hex));
        } catch (Exception e) {
            d.setColor(Color.LTGRAY);
        }
        return d;
    }

    private void loadSwatchBitmap(@Nullable String url) {
        if (url == null || url.isEmpty()) return;
        Glide.with(this)
                .asBitmap()
                .load(url)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource,
                                                @Nullable Transition<? super Bitmap> transition) {
                        cachedPolishBitmap = resource;
                    }
                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        cachedPolishBitmap = null;
                    }
                });
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    // ─── Camera ──────────────────────────────────────────────────────────────────

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(getContext());

        future.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(new Size(480, 640))
                        .build();

                imageAnalysis.setAnalyzer(
                        Executors.newSingleThreadExecutor(),
                        imageProxy -> {
                            processFrame(imageProxy);
                            imageProxy.close();
                        });

                imageCapture = new ImageCapture.Builder().build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                        imageAnalysis);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(getContext()));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    // ─── Hand model ──────────────────────────────────────────────────────────────

    private void setupHandModel() {
        BaseOptions baseOptions = BaseOptions.builder()
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
                            lastOutputBitmap = output;
                            requireActivity().runOnUiThread(() ->
                                    resultImage.setImageBitmap(output));
                        })
                        .build();

        handLandmarker = HandLandmarker.createFromOptions(requireContext(), options);
    }

    // ─── Frame processing ────────────────────────────────────────────────────────

    private void processFrame(ImageProxy image) {
        if (handLandmarker == null) return;

        Bitmap bitmap = imageProxyToBitmap(image);
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        bitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        latestBitmap = bitmap;
        MPImage mpImage = new BitmapImageBuilder(bitmap).build();
        handLandmarker.detectAsync(mpImage, System.currentTimeMillis());
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

    // ─── Nail drawing ────────────────────────────────────────────────────────────

    private Bitmap drawNails(Bitmap bitmap, HandLandmarkerResult result) {
        Bitmap mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutable);

        if (result.landmarks().isEmpty()) return mutable;

        List<NormalizedLandmark> landmarks = result.landmarks().get(0);

        int[]   tips    = {4, 8, 12, 16, 20};
        int[]   pips    = {3, 7, 11, 15, 19};
        float[] widths  = {28, 22, 24, 22, 18};
        float[] heights = {16, 12, 14, 12, 10};
        float forwardFactor = 0.5f;

        Paint bitmapPaint = new Paint();
        bitmapPaint.setAntiAlias(true);
        bitmapPaint.setFilterBitmap(true);
        bitmapPaint.setDither(true);



        for (int i = 0; i < tips.length; i++) {
            float tipX = landmarks.get(tips[i]).x() * bitmap.getWidth();
            float tipY = landmarks.get(tips[i]).y() * bitmap.getHeight();
            float pipX = landmarks.get(pips[i]).x() * bitmap.getWidth();
            float pipY = landmarks.get(pips[i]).y() * bitmap.getHeight();

            float dx = tipX - pipX;
            float dy = tipY - pipY;
            float cx = tipX + dx * forwardFactor;
            float cy = tipY + dy * forwardFactor;
            if (i == 0) cy += 6;

            float angle = (float) Math.toDegrees(Math.atan2(dy, dx));

            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(angle);

            float w = widths[i];
            float h = heights[i];
            RectF rectF    = new RectF(-w, -h, w, h);
            RectF drawRect = new RectF(
                    rectF.left - 1, rectF.top - 1,
                    rectF.right + 1, rectF.bottom + 1);

            // Clip to nail shape — this handles ALL shapes including coffin/stiletto
            Path nailPath = buildNailPath(rectF, w, h);
            canvas.save();
            canvas.clipPath(nailPath);

// FIX: always draw the full drawRect — the clipPath masks it to the correct shape
// Never use drawOval here — it ignores coffin/stiletto paths
            if (cachedPolishBitmap != null) {
                Matrix m = new Matrix();
                m.setRectToRect(
                        new RectF(0, 0,
                                cachedPolishBitmap.getWidth(),
                                cachedPolishBitmap.getHeight()),
                        drawRect,
                        Matrix.ScaleToFit.FILL);
                if (selectedShape == NailShape.ROUND) {

                    Paint shaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

                    BitmapShader shader = new BitmapShader(
                            cachedPolishBitmap,
                            Shader.TileMode.CLAMP,
                            Shader.TileMode.CLAMP
                    );

                    Matrix shaderMatrix = new Matrix();
                    shaderMatrix.setRectToRect(
                            new RectF(0, 0,
                                    cachedPolishBitmap.getWidth(),
                                    cachedPolishBitmap.getHeight()),
                            drawRect,
                            Matrix.ScaleToFit.FILL
                    );
                    shader.setLocalMatrix(shaderMatrix);

                    shaderPaint.setShader(shader);

                    canvas.drawPath(nailPath, shaderPaint);   // ✅ FIX

                } else {
                    canvas.drawBitmap(cachedPolishBitmap, m, bitmapPaint);
                }

                // Gloss drawn as rect too — clipPath shapes it correctly
                Paint glossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                glossPaint.setShader(new LinearGradient(
                        0, rectF.top, 0, rectF.centerY(),
                        Color.argb(80, 255, 255, 255),
                        Color.TRANSPARENT, Shader.TileMode.CLAMP));
                canvas.drawRect(drawRect, glossPaint);   // <-- drawRect not drawOval
//                if (selectedShape == NailShape.SQUARE ||
//                        selectedShape == NailShape.SQUOVAL ||
//                        selectedShape == NailShape.ROUND) {
//
//                    //  Fill using the actual path (fixes missing color)
//                    canvas.drawPath(nailPath, glossPaint);
//
//                } else {
//                    // Coffin & Stiletto still use rect + clip (works better for sharp shapes)
//                    canvas.drawRect(drawRect, glossPaint);
//                }

            } else if (selectedHex != null) {
                Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                basePaint.setColor(Color.parseColor(selectedHex));
                canvas.drawRect(drawRect, basePaint);
//                if (selectedShape == NailShape.SQUARE ||
//                        selectedShape == NailShape.SQUOVAL ||
//                        selectedShape == NailShape.ROUND) {
//
//                    canvas.drawPath(nailPath, basePaint);   // ✅ FIX
//
//                } else {
//                    canvas.drawRect(drawRect, basePaint);
//                }   // <-- drawRect not drawPath

                Paint glossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                glossPaint.setShader(new LinearGradient(
                        0, -h, 0, 0,
                        Color.argb(60, 255, 255, 255),
                        Color.TRANSPARENT, Shader.TileMode.CLAMP));
                canvas.drawRect(drawRect, glossPaint);  // <-- drawRect not drawPath
            }

            canvas.restore(); // remove clip

//// Edge stroke — draw the path outline on top
//            Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
//            edgePaint.setStyle(Paint.Style.STROKE);
//            edgePaint.setColor(Color.argb(80, 0, 0, 0));
//            edgePaint.setStrokeWidth(1.2f);
//            canvas.drawPath(nailPath, edgePaint);
            //remove the outline of coffin and pointy shape
            if (selectedShape != NailShape.COFFIN && selectedShape != NailShape.STILETTO) {
                Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                edgePaint.setStyle(Paint.Style.STROKE);
                edgePaint.setColor(Color.argb(80, 0, 0, 0));
                edgePaint.setStrokeWidth(1.2f);
                canvas.drawPath(nailPath, edgePaint);
            }

            canvas.restore(); // remove translate+rotate
        }

        return mutable;
    }

    /**
     * Builds the nail clip/fill path for the current shape.
     * All coordinates are in local canvas space centered at (0,0).
     * w = half-width, h = half-height of the nail rect.
     */
    private Path buildNailPath(RectF rect, float w, float h) {
        Path path = new Path();
        switch (selectedShape) {



            case ROUND:
                path.addOval(rect, Path.Direction.CW);
                break;



            case COFFIN:
                // Base is wide at +w (right), tapers inward toward -w (left/tip)
                // Tip is flat (blunt square end) — coffin's key characteristic
                float taperH = h * 0.45f; // how much the sides narrow at the tip
                path.moveTo( w,  -h);          // base top-right
                path.lineTo( w,   h);          // base bottom-right
                path.lineTo(-w,   taperH);     // tip bottom (tapered inward)
                path.lineTo(-w,  -taperH);     // tip top    (tapered inward)
                path.close();                  // flat line across tip = blunt end
                break;

            case STILETTO:
                // Base is wide at +w (right), sidewalls taper steeply to sharp point at -w (left)
                float baseH = h;               // full height at the base
                path.moveTo( w,  -baseH);      // base top-right
                path.lineTo( w,   baseH);      // base bottom-right
                // Steep curve inward to a sharp point — use quadratic bezier for smooth taper
                path.quadTo(0, h * 0.6f, -w, 0);   // bottom sidewall curves to point
                path.quadTo(0, -h * 0.6f, w, -baseH); // top sidewall curves back — close via base
                // Rebuild cleanly as explicit lines for sharp point
                path.reset();
                path.moveTo( w,  -baseH);
                path.lineTo(-w,   0);          // sharp tip point
                path.lineTo( w,   baseH);
                path.lineTo( w,  -baseH);
                path.close();
                break;
        }
        // 🔥 Rotate 180° around center (0,0)
        Matrix matrix = new Matrix();
        matrix.setRotate(180);
        path.transform(matrix);


        return path;
    }

    // ─── Snapshot ────────────────────────────────────────────────────────────────

    private void takeSnapshot() {
        if (lastOutputBitmap == null) {
            Toast.makeText(getContext(),
                    "No frame yet — point camera at your hand",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        flashOverlay.setVisibility(View.VISIBLE);
        flashOverlay.setAlpha(1f);
        flashOverlay.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> flashOverlay.setVisibility(View.GONE))
                .start();

        Bitmap toSave = stampColorName(lastOutputBitmap, selectedShadeName);
        Executors.newSingleThreadExecutor().execute(() -> {
            android.net.Uri uri = saveToGallery(toSave);
            requireActivity().runOnUiThread(() -> {
                if (uri != null) {
                    lastSavedUri = uri;   // store so gallery button can open it
                    Toast.makeText(getContext(), "Saved to gallery!", Toast.LENGTH_SHORT).show();
                    loadLatestThumbnail();
                } else {
                    Toast.makeText(getContext(), "Failed to save image", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private Bitmap stampColorName(Bitmap source, String name) {
        Bitmap stamped = source.copy(Bitmap.Config.ARGB_8888, true);
        if (name == null || name.isEmpty()) return stamped;

        Canvas canvas = new Canvas(stamped);
        int W = stamped.getWidth();
        int H = stamped.getHeight();

        float textSize = W * 0.045f;
        float padding  = W * 0.04f;

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(textSize);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.LEFT);

        Rect textBounds = new Rect();
        textPaint.getTextBounds(name, 0, name.length(), textBounds);
        float textW = textPaint.measureText(name);
        float textH = textBounds.height();

        float pillPadX = padding * 0.8f;
        float pillPadY = padding * 0.5f;
        float pillW    = textW + pillPadX * 2;
        float pillH    = textH + pillPadY * 2;

        float left   = padding;
        float bottom = H - padding;
        float top    = bottom - pillH;

        Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pillPaint.setColor(Color.argb(160, 0, 0, 0));
        RectF pillRect = new RectF(left, top, left + pillW, top + pillH);
        canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillPaint);

        canvas.drawText(name, left + pillPadX, top + pillPadY + textH, textPaint);

        return stamped;
    }

    private android.net.Uri saveToGallery(Bitmap bitmap) {
        String filename = "NailIt_" + System.currentTimeMillis() + ".jpg";
        java.io.OutputStream fos = null;
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename);
                values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + "/NailIt");
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 1);

                android.net.Uri uri = requireContext().getContentResolver()
                        .insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return null;

                fos = requireContext().getContentResolver().openOutputStream(uri);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);

                values.clear();
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0);
                requireContext().getContentResolver().update(uri, values, null, null);

                return uri;  // ← return Uri directly

            } else {
                java.io.File dir = new java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_PICTURES), "NailIt");
                if (!dir.exists()) dir.mkdirs();
                java.io.File file = new java.io.File(dir, filename);
                fos = new java.io.FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);

                android.media.MediaScannerConnection.scanFile(
                        requireContext(),
                        new String[]{file.getAbsolutePath()},
                        new String[]{"image/jpeg"},
                        null);

                return android.net.Uri.fromFile(file);  // ← return Uri
            }
        } catch (Exception e) {
            Log.e("SNAPSHOT", "Failed to save", e);
            return null;
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (Exception ignored) {}
            }
        }
    }
}