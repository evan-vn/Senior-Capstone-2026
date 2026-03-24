package com.example.nailit;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.nailit.data.model.Polish;
import com.example.nailit.data.repo.FavoritesRepository;
import com.example.nailit.data.repo.PolishesRepository;
import com.example.nailit.ui.FavoritePolishesActivity;

import java.util.List;
import java.util.Set;

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



    private void loadFavorites() {
        showLoading();
        favoritesRepo.getMyFavoritePolishes(new FavoritesRepository.FavoritesListCallback() {
            @Override
            public void onSuccess(Set<String> polishUids) {
                Log.d(TAG, "Favorite polish UIDs: " + polishUids.size());
                if (polishUids.isEmpty()) {
                    runOnUiThread(() -> showEmpty());
                    return;
                }
                adapter.setFavoriteUids(polishUids);
                fetchPolishes(polishUids);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Failed to load favorite UIDs: " + message);
                runOnUiThread(() -> showEmpty());
            }
        });
    }

    private void fetchPolishes(Set<String> uids) {
        polishesRepo.getPolishesByUids(uids, new PolishesRepository.PolishesCallback() {
            @Override
            public void onSuccess(List<Polish> polishes) {
                Log.d(TAG, "Fetched " + polishes.size() + " favorite polishes");
                runOnUiThread(() -> showResults(polishes));
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Failed to fetch polishes: " + message);
                runOnUiThread(() -> {
                    showEmpty();
                    Toast.makeText(FavoritePolishesActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showLoading() {

        emptyText.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
    }

    private void showResults(List<Polish> polishes) {

        if (polishes == null || polishes.isEmpty()) {
            showEmpty();
            return;
        }

        recycler.setVisibility(View.VISIBLE);
        adapter.setItems(polishes);
    }

    private void showEmpty() {
        progress.setVisibility(View.GONE);
        recycler.setVisibility(View.GONE);
        emptyText.setVisibility(View.VISIBLE);
    }
}
