package com.example.nailit;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nailit.data.model.Polish;
import com.example.nailit.data.network.TokenStore;
import com.example.nailit.data.repo.PolishesRepository;
import com.example.nailit.ui.TrendingPolishesAdapter;

import java.util.ArrayList;
import java.util.List;

public class FragmentCollections extends Fragment {

    private CardView[] tabCards;
    private int selectedIndex = -1;

    private ImageView seasonIcon;
    private TextView seasonNameLabel;
    private TextView describeSeasonLabel;
    private RecyclerView colorsRecycler;
    private ProgressBar trendingProgress;
    private TextView trendingError;

    private PolishesRepository repo;
    private TrendingPolishesAdapter adapter;

    private static final int IDX_TRENDING = 4;

    private static final String[] TAB_NAMES = {
            "Spring", "Summer", "Autumn", "Winter", "Trending"
    };

    private static final String[] TAB_DESCRIPTIONS = {
            "Soft pastels & fresh florals",
            "Bold brights & sunny tones",
            "Warm rusts & earthy hues",
            "Deep jewels & icy shimmer",
            "Most favorited this week"
    };

    private static final String[] SEASON_TAGS = {
            "spring", "summer", "fall", "winter"
    };

    private static final int[] TAB_ICONS = {
            R.drawable.spring, R.drawable.summer, R.drawable.autumn,
            R.drawable.winter, R.drawable.trending
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_collection, container, false);

        seasonIcon = view.findViewById(R.id.seasonIcon);
        seasonNameLabel = view.findViewById(R.id.seasonNameLabel);
        describeSeasonLabel = view.findViewById(R.id.describeSeasonLabel);
        colorsRecycler = view.findViewById(R.id.colorsRecycler);
        trendingProgress = view.findViewById(R.id.trendingProgress);
        trendingError = view.findViewById(R.id.trendingError);

        colorsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        colorsRecycler.setNestedScrollingEnabled(false);

        adapter = new TrendingPolishesAdapter(new ArrayList<>());
        colorsRecycler.setAdapter(adapter);

        TokenStore tokenStore = new TokenStore(requireContext());
        repo = new PolishesRepository(tokenStore);

        tabCards = new CardView[]{
                view.findViewById(R.id.springCardView),
                view.findViewById(R.id.summerCardView),
                view.findViewById(R.id.autumnCardView),
                view.findViewById(R.id.winterCardView),
                view.findViewById(R.id.tredingCardView)
        };

        for (int i = 0; i < tabCards.length; i++) {
            int index = i;
            tabCards[i].setOnClickListener(v -> selectTab(index));
        }

        selectTab(IDX_TRENDING);

        return view;
    }

    private void selectTab(int index) {
        if (index == selectedIndex) return;
        selectedIndex = index;

        int accent = ContextCompat.getColor(requireContext(), R.color.light_purple);
        int normal = ContextCompat.getColor(requireContext(), R.color.white);

        for (int i = 0; i < tabCards.length; i++) {
            tabCards[i].setCardBackgroundColor(i == index ? accent : normal);
        }

        seasonIcon.setImageResource(TAB_ICONS[index]);
        seasonNameLabel.setText(TAB_NAMES[index]);
        describeSeasonLabel.setText(TAB_DESCRIPTIONS[index]);

        showLoading();

        if (index == IDX_TRENDING) {
            repo.getTrendingPolishes(createCallback());
        } else {
            repo.getPolishesBySeason(SEASON_TAGS[index], createCallback());
        }
    }

    private PolishesRepository.PolishesCallback createCallback() {
        return new PolishesRepository.PolishesCallback() {
            @Override
            public void onSuccess(List<Polish> polishes) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    trendingProgress.setVisibility(View.GONE);
                    if (polishes.isEmpty()) {
                        trendingError.setText("No polishes found");
                        trendingError.setVisibility(View.VISIBLE);
                        adapter.setItems(new ArrayList<>());
                        return;
                    }
                    adapter.setItems(polishes);
                });
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    trendingProgress.setVisibility(View.GONE);
                    trendingError.setText(message);
                    trendingError.setVisibility(View.VISIBLE);
                    adapter.setItems(new ArrayList<>());
                });
            }
        };
    }

    private void showLoading() {
        trendingError.setVisibility(View.GONE);
        trendingProgress.setVisibility(View.VISIBLE);
        adapter.setItems(new ArrayList<>());
    }
}
