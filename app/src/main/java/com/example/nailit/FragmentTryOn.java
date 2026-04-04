package com.example.nailit;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nailit.data.model.Polish;
import com.example.nailit.data.model.TryOnViewModel;
import com.example.nailit.data.network.TokenStore;
import com.example.nailit.data.repo.FavoritesRepository;
import com.example.nailit.data.repo.PolishesRepository;
import com.example.nailit.ui.PolishGridAdapter;

import java.util.List;
import java.util.Set;

public class FragmentTryOn extends Fragment {

    private Button tryOnBtn;
    private TextView empTxtTryOn;
    private ProgressBar progress;
    private RecyclerView colorsRecyclerTryOn;

    private PolishGridAdapter adapter;
    private FavoritesRepository favoritesRepo;
    private PolishesRepository polishesRepo;
    private TryOnViewModel viewModel;

    Polish selectedPolish;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_try_on, container, false);

        tryOnBtn           = view.findViewById(R.id.startTryOnBtn);
        empTxtTryOn        = view.findViewById(R.id.empTxtTryOn);
        colorsRecyclerTryOn = view.findViewById(R.id.colorsRecyclerTryOn);
        progress           = view.findViewById(R.id.tryOnProgress);

        TokenStore tokenStore = new TokenStore(getContext());
        favoritesRepo = new FavoritesRepository(tokenStore);
        polishesRepo  = new PolishesRepository(tokenStore);

        // Scope ViewModel to Activity so FragmentCamera can share it
        viewModel = new ViewModelProvider(requireActivity()).get(TryOnViewModel.class);

        colorsRecyclerTryOn.setLayoutManager(new GridLayoutManager(getContext(), 2));
        adapter = new PolishGridAdapter(favoritesRepo);
        colorsRecyclerTryOn.setAdapter(adapter);

        if (viewModel.cachedUids != null) {
            adapter.setFavoriteUids(viewModel.cachedUids);
        }

        adapter.setOnPolishClickListener(polish -> {
            selectedPolish = polish;
            Log.d("FRAGMENT_CLICK", "Selected: " + polish.getShadeName());
            Toast.makeText(getContext(), "Selected: " + polish.getShadeName(), Toast.LENGTH_SHORT).show();
        });

        loadFavorites();

        tryOnBtn.setOnClickListener(v -> {
            FragmentCamera cameraFragment = new FragmentCamera();
            Bundle bundle = new Bundle();

            // Pass selected polish if user picked one, otherwise pass nulls
            // FragmentCamera will handle the case where nothing is selected
            if (selectedPolish != null) {
                bundle.putString("hex",       selectedPolish.getHex());
                bundle.putString("thumbnail", selectedPolish.getThumbnailHex());
                bundle.putString("image_url", selectedPolish.getSwatchUrl());
                bundle.putString("shade_name", selectedPolish.getShadeName());
                Log.d("SEND_DEBUG", "url = " + selectedPolish.getSwatchUrl());
            }

            cameraFragment.setArguments(bundle);

            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.middleLayout, cameraFragment)
                    .addToBackStack(null)
                    .commit();
        });

        return view;
    }

    // ─── Data loading ────────────────────────────────────────────────────────────

    private void loadFavorites() {
        if (viewModel.cachedPolishes != null) {
            showResults(viewModel.cachedPolishes);
            return;
        }

        showLoading();
        favoritesRepo.getMyFavoritePolishes(new FavoritesRepository.FavoritesListCallback() {
            @Override
            public void onSuccess(Set<String> polishUids) {
                if (polishUids.isEmpty()) {
                    requireActivity().runOnUiThread(() -> showEmpty());
                    return;
                }
                viewModel.cachedUids = polishUids;
                adapter.setFavoriteUids(polishUids);
                fetchPolishes(polishUids);
            }

            @Override
            public void onError(String message) {
                requireActivity().runOnUiThread(() -> showEmpty());
            }
        });
    }

    private void fetchPolishes(Set<String> uids) {
        polishesRepo.getPolishesByUids(uids, new PolishesRepository.PolishesCallback() {
            @Override
            public void onSuccess(List<Polish> polishes) {
                viewModel.cachedPolishes = polishes;
                requireActivity().runOnUiThread(() -> showResults(polishes));
            }

            @Override
            public void onError(String message) {
                requireActivity().runOnUiThread(() -> showEmpty());
            }
        });
    }

    // ─── UI states ───────────────────────────────────────────────────────────────

    private void showLoading() {
        progress.setVisibility(View.VISIBLE);
        empTxtTryOn.setVisibility(View.GONE);
        colorsRecyclerTryOn.setVisibility(View.GONE);
    }

    private void showResults(List<Polish> polishes) {
        progress.setVisibility(View.GONE);
        if (polishes == null || polishes.isEmpty()) {
            showEmpty();
            return;
        }
        colorsRecyclerTryOn.setVisibility(View.VISIBLE);
        adapter.setItems(polishes);
    }

    private void showEmpty() {
        progress.setVisibility(View.GONE);
        colorsRecyclerTryOn.setVisibility(View.GONE);
        empTxtTryOn.setVisibility(View.VISIBLE);
    }
}