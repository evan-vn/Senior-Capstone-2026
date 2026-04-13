package com.example.nailit;

import android.os.Bundle;
import android.graphics.Color;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FragmentTryOn extends Fragment {

    public static final String ARG_SELECTED_POLISH_UID = "selected_polish_uid";
    public static final String ARG_SELECTED_COLOR_HEX = "selected_color_hex";
    public static final String ARG_SELECTED_SHADE_NAME = "selected_shade_name";
    public static final String ARG_SELECTED_IMAGE_URL = "selected_image_url";

    private Button tryOnBtn;
    private TextView empTxtTryOn;
    private ProgressBar progress;
    private RecyclerView colorsRecyclerTryOn;

    private PolishGridAdapter adapter;
    private FavoritesRepository favoritesRepo;
    private PolishesRepository polishesRepo;
    private TryOnViewModel viewModel;

    Polish selectedPolish;
    private String incomingPolishUid;
    private String incomingHex;
    private String incomingShadeName;
    private String incomingImageUrl;

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
        readIncomingSelectionArgs();

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
            } else if (incomingHex != null && isValidHex(incomingHex)) {
                bundle.putString("hex", incomingHex);
                bundle.putString("image_url", incomingImageUrl);
                bundle.putString("shade_name", incomingShadeName);
                Log.d("FragmentTryOn", "Using incoming preselected color directly in camera: " + incomingHex);
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

    @Override
    public void onResume() {
        super.onResume();
        if (!isAdded()) return;
        TokenStore ts = new TokenStore(requireContext());
        String sub = ts.getSubFromJwt();
        if (viewModel.lastAuthSub != null && sub != null && !sub.equals(viewModel.lastAuthSub)) {
            Log.d("FragmentTryOn", "JWT subject changed; clearing try-on favorites cache");
            viewModel.cachedPolishes = null;
            viewModel.cachedUids = null;
            viewModel.lastAuthSub = null;
            adapter.setFavoriteUids(new HashSet<>());
            loadFavorites();
        }
    }

    // ─── Data loading ────────────────────────────────────────────────────────────

    private void loadFavorites() {
        if (viewModel.cachedPolishes != null) {
            showResults(viewModel.cachedPolishes);
            applyIncomingSelection(viewModel.cachedPolishes);
            return;
        }

        showLoading();
        favoritesRepo.getMyFavoritePolishes(new FavoritesRepository.FavoritesListCallback() {
            @Override
            public void onSuccess(Set<String> polishUids) {
                viewModel.lastAuthSub = new TokenStore(requireContext()).getSubFromJwt();
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
                requireActivity().runOnUiThread(() -> {
                    showResults(polishes);
                    applyIncomingSelection(polishes);
                });
            }

            @Override
            public void onError(String message) {
                requireActivity().runOnUiThread(() -> showEmpty());
            }
        });
    }

    private void readIncomingSelectionArgs() {
        Bundle args = getArguments();
        if (args == null) {
            Log.d("FragmentTryOn", "No incoming selection args; normal Try-On flow");
            return;
        }
        incomingPolishUid = args.getString(ARG_SELECTED_POLISH_UID);
        incomingHex = args.getString(ARG_SELECTED_COLOR_HEX);
        incomingShadeName = args.getString(ARG_SELECTED_SHADE_NAME);
        incomingImageUrl = args.getString(ARG_SELECTED_IMAGE_URL);

        if (incomingHex == null || incomingHex.trim().isEmpty()) {
            Log.d("FragmentTryOn", "No selected_color_hex passed; fallback to manual selection");
        } else if (!isValidHex(incomingHex)) {
            Log.w("FragmentTryOn", "Invalid selected_color_hex=" + incomingHex + " fallback enabled");
            incomingHex = null;
        } else {
            Log.d("FragmentTryOn", "Received preselected color hex=" + incomingHex
                    + " uid=" + incomingPolishUid
                    + " shade=" + incomingShadeName);
        }
    }

    private void applyIncomingSelection(List<Polish> polishes) {
        if ((incomingPolishUid == null || incomingPolishUid.isEmpty())
                && (incomingHex == null || incomingHex.isEmpty())) {
            return;
        }
        if (polishes == null || polishes.isEmpty()) return;

        Polish match = null;
        if (incomingPolishUid != null && !incomingPolishUid.isEmpty()) {
            for (Polish p : polishes) {
                if (p != null && incomingPolishUid.equals(p.getUid())) {
                    match = p;
                    break;
                }
            }
        }
        if (match == null && incomingHex != null && !incomingHex.isEmpty()) {
            for (Polish p : polishes) {
                if (p != null && p.getHex() != null && incomingHex.equalsIgnoreCase(p.getHex())) {
                    match = p;
                    break;
                }
            }
        }
        if (match == null) {
            Log.d("FragmentTryOn", "Incoming polish not found in favorites list; keeping manual flow");
            return;
        }

        selectedPolish = match;
        adapter.setSelectedUid(match.getUid());
        Log.d("FragmentTryOn", "Preselected polish applied: uid=" + match.getUid()
                + " shade=" + match.getShadeName()
                + " hex=" + match.getHex());
        Toast.makeText(getContext(), "Selected: " + match.getShadeName(), Toast.LENGTH_SHORT).show();

        incomingPolishUid = null;
        incomingHex = null;
        incomingShadeName = null;
        incomingImageUrl = null;
    }

    private boolean isValidHex(String hex) {
        try {
            Color.parseColor(hex);
            return true;
        } catch (Exception e) {
            return false;
        }
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