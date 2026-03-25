package com.example.nailit;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nailit.data.model.Polish;
import com.example.nailit.data.network.TokenStore;
import com.example.nailit.data.repo.FavoritesRepository;
import com.example.nailit.data.repo.PolishesRepository;
import com.example.nailit.ui.FavoritePolishesActivity;
import com.example.nailit.ui.PolishGridAdapter;

import java.util.List;
import java.util.Set;

public class FragmentTryOn extends Fragment {
    Button tryOnBtn;
    private TextView empTxtTryOn;
    private RecyclerView colorsRecyclerTryOn;

    private PolishGridAdapter adapter;
    private FavoritesRepository favoritesRepo;
    private PolishesRepository polishesRepo;
    Polish selectedPolish;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_try_on, container, false);
        //turn on Camera
        tryOnBtn = view.findViewById(R.id.startTryOnBtn);
        empTxtTryOn = view.findViewById(R.id.empTxtTryOn);
        colorsRecyclerTryOn = view.findViewById(R.id.colorsRecyclerTryOn);
        TokenStore tokenStore = new TokenStore(getContext());
        favoritesRepo = new FavoritesRepository(tokenStore);
        polishesRepo = new PolishesRepository(tokenStore);

        colorsRecyclerTryOn.setLayoutManager(new GridLayoutManager(getContext(), 2));
        adapter = new PolishGridAdapter(favoritesRepo);
        colorsRecyclerTryOn.setAdapter(adapter);
        colorsRecyclerTryOn.setVisibility(View.VISIBLE);

        adapter.setOnPolishClickListener(polish -> {
            selectedPolish = polish;
            Log.d("FRAGMENT_CLICK", "Selected: " + polish.getShadeName());
            Toast.makeText(getContext(), "Selected: " + polish.getShadeName(), Toast.LENGTH_SHORT).show();

        });

        loadFavorites();



        tryOnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(selectedPolish == null){
                    Toast.makeText(getContext(), "Please  pick a color first!", Toast.LENGTH_SHORT).show();
                    return;
                }

                FragmentCamera cameraFragment = new FragmentCamera();
                Bundle bundle = new Bundle();
                bundle.putString("hex", selectedPolish.getHex());
                bundle.putString("thumbnail", selectedPolish.getThumbnailHex());
                cameraFragment.setArguments(bundle);

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
                //Log.d(TAG, "Favorite polish UIDs: " + polishUids.size());
                if (polishUids.isEmpty()) {
                    requireActivity().runOnUiThread(() -> showEmpty());
                    return;
                }
                adapter.setFavoriteUids(polishUids);
                fetchPolishes(polishUids);
            }

            @Override
            public void onError(String message) {
                //Log.e(TAG, "Failed to load favorite UIDs: " + message);
                requireActivity().runOnUiThread(() -> showEmpty());
            }
        });
    }

    private void fetchPolishes(Set<String> uids) {
        polishesRepo.getPolishesByUids(uids, new PolishesRepository.PolishesCallback() {
            @Override
            public void onSuccess(List<Polish> polishes) {
                //Log.d(TAG, "Fetched " + polishes.size() + " favorite polishes");
                requireActivity(). runOnUiThread(() -> showResults(polishes));
            }

            @Override
            public void onError(String message) {
                //Log.e(TAG, "Failed to fetch polishes: " + message);
                requireActivity().runOnUiThread(() -> {
                    showEmpty();
                    //Toast.makeText(FavoritePolishesActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showLoading() {

        empTxtTryOn.setVisibility(View.GONE);
        colorsRecyclerTryOn.setVisibility(View.GONE);
    }

    private void showResults(List<Polish> polishes) {

        if (polishes == null || polishes.isEmpty()) {
            showEmpty();
            return;
        }
        for (Polish p:polishes){
            Log.d("Polish", p.getHex());
            Log.d("Polish",p.getShadeName());
        }
        colorsRecyclerTryOn.setVisibility(View.VISIBLE);
        adapter.setItems(polishes);
    }

    private void showEmpty() {

        colorsRecyclerTryOn.setVisibility(View.GONE);
        empTxtTryOn.setVisibility(View.VISIBLE);
    }
}
