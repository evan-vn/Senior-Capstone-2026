package com.example.nailit;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class FragmentTryOn extends Fragment {
    Button tryOnBtn;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_try_on, container, false);
        //turn on Camera
        tryOnBtn = view.findViewById(R.id.startTryOnBtn);
        tryOnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Fragment cameraFragment = new FragmentCamera();
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.middleLayout, cameraFragment)
                        .addToBackStack(null)
                        .commit();
            }
        });


        return view;
    }
}
