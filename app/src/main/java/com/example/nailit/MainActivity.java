package com.example.nailit;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

public class MainActivity extends AppCompatActivity {
    ImageView tryOnBtn, collectionBtn, aiBtn, locationBtn, profileBtn;
    TextView describeFeature;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        tryOnBtn = findViewById(R.id.cameraIcon);
        collectionBtn = findViewById(R.id.collectionIcon);
        aiBtn = findViewById(R.id.botIcon);
        locationBtn = findViewById(R.id.locationIcon);
        profileBtn = findViewById(R.id.imageProfile);
        describeFeature = findViewById(R.id.describeFeature);
        describeFeature.setText("Collection Feature");

        loadFragment(new FragmentCollections());

        tryOnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                describeFeature.setText("TryOn Feature");
                loadFragment(new FragmentTryOn());
            }
        });
        collectionBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                describeFeature.setText("Collection Feature");
                loadFragment(new FragmentCollections());
            }
        });

        locationBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                describeFeature.setText("Salons Location Feature");
                loadFragment(new FragmentLocations());
            }
        });
        aiBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                describeFeature.setText("Style AI Feature");
                loadFragment(new FragmentChatBot());
            }
        });

        profileBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                describeFeature.setText("Profile Feature");
                loadFragment(new FragmentProfile());
            }
        });


    }

    public void loadFragment(Fragment fragment){
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.middleLayout, fragment);
        ft.commit();
    }
}