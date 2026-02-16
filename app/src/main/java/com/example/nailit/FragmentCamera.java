package com.example.nailit;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.Manifest;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.camera.view.PreviewView;

import com.google.common.util.concurrent.ListenableFuture;

public class FragmentCamera extends Fragment {
    private PreviewView previewView;
    private static final int  CAMERA_REQUEST_CODE= 100;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
       View v = inflater.inflate(R.layout.fragment_camera, container, false);
       previewView = v.findViewById(R.id.cameraPreview);
       if(ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
           startCamera();
       }else{
           ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
       }


       return v;
    }

    private  void startCamera(){
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try{
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview);


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
}
