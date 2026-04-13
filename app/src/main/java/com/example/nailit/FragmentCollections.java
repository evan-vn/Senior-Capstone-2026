package com.example.nailit;

import android.os.Bundle;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nailit.data.model.Polish;
import com.example.nailit.data.network.TokenStore;
import com.example.nailit.data.repo.FavoritesRepository;
import com.example.nailit.data.repo.PolishesRepository;
import com.example.nailit.ui.DesignsByPolishActivity;
import com.example.nailit.ui.PolishGridAdapter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

public class FragmentCollections extends Fragment {

    private CardView[] tabCards;
    private int selectedIndex = -1;

    private ImageView seasonIcon;
    private TextView seasonNameLabel;
    private TextView describeSeasonLabel;
    private RecyclerView colorsRecycler;
    private ProgressBar trendingProgress;
    private TextView trendingError;
    private View seasonPolishesCard;

    private EditText brandSearchInput;
    private ImageView browseClearButton;
    private ChipGroup brandFilterChipGroup;
    private ChipGroup collectionsChipGroup;
    private TextView browseEmptyText;
    private View browseResultsCard;
    private TextView browseResultsTitle;
    private ProgressBar browseResultsProgress;
    private TextView browseResultsEmpty;
    private RecyclerView browseResultsRecycler;
    private Button browseLoadMoreButton;
    private ProgressBar browseLoadMoreProgress;
    private Button seasonLoadMoreButton;
    private ProgressBar seasonLoadMoreProgress;
    private View seasonTabsGrid;
    private View stylingTipsCard;

    private PolishesRepository repo;
    private FavoritesRepository favoritesRepo;
    private PolishGridAdapter adapter;
    private PolishGridAdapter browseResultsAdapter;

    private final Handler brandSearchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingBrandFilter;
    private List<String> allBrands = new ArrayList<>();

    private String selectedBrand;
    private String selectedCollection;

    private static final String SEARCH_LOG_TAG = "SEARCH_FETCH";
    private static final int SEARCH_PAGE_SIZE = 20;
    private static final int SEASON_PAGE_SIZE = 20;

    private final List<Polish> seasonOrTrendingResults = new ArrayList<>();
    private boolean seasonTrendingHasMore = true;
    private boolean seasonTrendingLoading = false;
    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean hasMore = true;
    private final List<Polish> currentResults = new ArrayList<>();
    private final Set<String> hydratedUids = new HashSet<>();
    private final Set<String> selectedBrands = new HashSet<>();
    private final Set<String> selectedCollections = new HashSet<>();
    private boolean isBuildingBrandChips = false;
    private boolean isBuildingCollectionChips = false;
    private boolean isRestoringFilters = false;

    private static final int IDX_TRENDING = 4;
    private static final String STATE_SELECTED_BRAND = "state_selected_brand";
    private static final String STATE_SELECTED_COLLECTION = "state_selected_collection";
    private static final String STATE_BRAND_QUERY = "state_brand_query";
    private static final String STATE_SELECTED_BRANDS = "state_selected_brands";
    private static final String STATE_SELECTED_COLLECTIONS = "state_selected_collections";
    private static final String STATE_SEARCH_QUERY = "state_search_query";

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
        seasonPolishesCard = view.findViewById(R.id.seasonPolishesCard);

        brandSearchInput = view.findViewById(R.id.brandSearchInput);
        browseClearButton = view.findViewById(R.id.browseClearButton);
        brandFilterChipGroup = view.findViewById(R.id.brandFilterChipGroup);
        collectionsChipGroup = view.findViewById(R.id.collectionsChipGroup);
        browseEmptyText = view.findViewById(R.id.browseEmptyText);
        browseResultsCard = view.findViewById(R.id.browseResultsCard);
        browseResultsTitle = view.findViewById(R.id.browseResultsTitle);
        browseResultsProgress = view.findViewById(R.id.browseResultsProgress);
        browseResultsEmpty = view.findViewById(R.id.browseResultsEmpty);
        browseResultsRecycler = view.findViewById(R.id.browseResultsRecycler);
        browseLoadMoreButton = view.findViewById(R.id.browseLoadMoreButton);
        browseLoadMoreProgress = view.findViewById(R.id.browseLoadMoreProgress);
        seasonLoadMoreButton = view.findViewById(R.id.seasonLoadMoreButton);
        seasonLoadMoreProgress = view.findViewById(R.id.seasonLoadMoreProgress);
        seasonTabsGrid = view.findViewById(R.id.seasonTabsGrid);
        stylingTipsCard = view.findViewById(R.id.stylingTipsCard);

        colorsRecycler.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        colorsRecycler.setNestedScrollingEnabled(false);

        browseResultsRecycler.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        browseResultsRecycler.setNestedScrollingEnabled(false);
        browseResultsRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) return;
                maybeLoadMore();
                hydrateVisible();
            }
        });

        browseLoadMoreButton.setOnClickListener(v -> loadNextPage());
        seasonLoadMoreButton.setOnClickListener(v -> fetchSeasonTrending(true));

        TokenStore tokenStore = new TokenStore(requireContext());
        repo = new PolishesRepository(tokenStore);
        favoritesRepo = new FavoritesRepository(tokenStore);
        adapter = new PolishGridAdapter(favoritesRepo, polish -> openDesigns(polish));
        colorsRecycler.setAdapter(adapter);
        browseResultsAdapter = new PolishGridAdapter(favoritesRepo, polish -> openDesigns(polish));
        browseResultsRecycler.setAdapter(browseResultsAdapter);

        tabCards = new CardView[]{
                view.findViewById(R.id.springCardView),
                view.findViewById(R.id.summerCardView),
                view.findViewById(R.id.autumnCardView),
                view.findViewById(R.id.winterCardView),
                view.findViewById(R.id.tredingCardView)
        };
        for (int i = 0; i < tabCards.length; i++) {
            int index = i;
            tabCards[i].setOnClickListener(v -> {
                if (isSearchActive()) return;
                selectTab(index);
            });
        }

        setupBrowseUi(savedInstanceState);
        selectTab(IDX_TRENDING);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (favoritesRepo != null) {
            loadFavoritesIntoAdapters();
        }
    }

    private boolean isSearchActive() {
        String query = brandSearchInput.getText() != null ? brandSearchInput.getText().toString().trim() : "";
        return !query.isEmpty()
                || !selectedBrands.isEmpty()
                || !selectedCollections.isEmpty();
    }

    private void updateSearchUiState() {
        boolean active = isSearchActive();

        browseResultsCard.setVisibility(active ? View.VISIBLE : View.GONE);

        seasonTabsGrid.setVisibility(active ? View.GONE : View.VISIBLE);
        seasonPolishesCard.setVisibility(active ? View.GONE : View.VISIBLE);
        stylingTipsCard.setVisibility(active ? View.GONE : View.VISIBLE);

        if (!active) {
            hideBrowseResultsInline();
        }
        updateLoadMoreUi();
        updateSeasonLoadMoreUi();
    }

    private void hideBrowseResultsInline() {
        browseResultsProgress.setVisibility(View.GONE);
        browseResultsEmpty.setVisibility(View.GONE);
        browseResultsAdapter.setItems(new ArrayList<>());
        browseLoadMoreButton.setVisibility(View.GONE);
        browseLoadMoreProgress.setVisibility(View.GONE);
    }

    private void updateLoadMoreUi() {
        boolean active = isSearchActive();
        boolean canShow = active
                && currentResults.size() > 0
                && hasMore
                && !isLoading;
        browseLoadMoreButton.setVisibility(canShow ? View.VISIBLE : View.GONE);
        browseLoadMoreButton.setEnabled(!isLoading);
        browseLoadMoreProgress.setVisibility(active && isLoading ? View.VISIBLE : View.GONE);
    }

    private void updateSeasonLoadMoreUi() {
        if (isSearchActive()) {
            seasonLoadMoreButton.setVisibility(View.GONE);
            seasonLoadMoreProgress.setVisibility(View.GONE);
            return;
        }
        boolean hasRows = !seasonOrTrendingResults.isEmpty();
        boolean showButton = hasRows && seasonTrendingHasMore && !seasonTrendingLoading;
        boolean showAppendProgress = seasonTrendingLoading && hasRows;
        seasonLoadMoreButton.setVisibility(showButton ? View.VISIBLE : View.GONE);
        seasonLoadMoreButton.setEnabled(!seasonTrendingLoading);
        seasonLoadMoreProgress.setVisibility(showAppendProgress ? View.VISIBLE : View.GONE);
    }

    private void setupBrowseUi(@Nullable Bundle savedInstanceState) {
        isRestoringFilters = true;
        try {
            if (savedInstanceState != null) {
                ArrayList<String> restoredBrands = savedInstanceState.getStringArrayList(STATE_SELECTED_BRANDS);
                if (restoredBrands != null) selectedBrands.addAll(restoredBrands);

                ArrayList<String> restoredCollections = savedInstanceState.getStringArrayList(STATE_SELECTED_COLLECTIONS);
                if (restoredCollections != null) selectedCollections.addAll(restoredCollections);

                String query = savedInstanceState.getString(STATE_SEARCH_QUERY);
                if (query != null) {
                    brandSearchInput.setText(query);
                    brandSearchInput.setSelection(query.length());
                }

                selectedBrand = selectedBrands.isEmpty() ? null : selectedBrands.iterator().next();
                selectedCollection = selectedCollections.isEmpty() ? null : selectedCollections.iterator().next();
            } else {
                selectedBrand = null;
                selectedCollection = null;
            }

            if (selectedBrands.isEmpty()) {
                selectedCollections.clear();
            }
        } finally {
            isRestoringFilters = false;
        }

        browseClearButton.setOnClickListener(v -> restoreDefaultMode());

        brandSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isRestoringFilters) return;
                updateClearButtonVisibility();
                updateSearchUiState();
                scheduleBrandFilter(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        repo.getDistinctBrands(new PolishesRepository.StringsCallback() {
            @Override
            public void onSuccess(List<String> values) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    allBrands = values != null ? values : new ArrayList<>();
                    buildBrandFilterChips();
                    refreshCollectionsChipGroup();

                    updateClearButtonVisibility();
                    updateSearchUiState();
                    if (isSearchActive()) {
                        resetPagination();
                        loadNextPage();
                    }
                });
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void scheduleBrandFilter(String query) {
        if (pendingBrandFilter != null) {
            brandSearchHandler.removeCallbacks(pendingBrandFilter);
        }
        pendingBrandFilter = () -> {
            if (!isAdded()) return;
            if (!isSearchActive()) {
                hideBrowseResultsInline();
                updateSearchUiState();
                return;
            }
            resetPagination();
            loadNextPage();
        };
        brandSearchHandler.postDelayed(pendingBrandFilter, 250);
    }

    private void buildBrandFilterChips() {
        brandFilterChipGroup.removeAllViews();
        isBuildingBrandChips = true;
        try {
            for (String brand : allBrands) {
                if (brand == null) continue;
                String value = brand.trim();
                if (value.isEmpty()) continue;

                Chip chip = buildChip(value, false);
                chip.setChecked(selectedBrands.contains(value));

                chip.setOnClickListener(v -> {
                    if (isBuildingBrandChips || isRestoringFilters) return;

                    boolean checked = chip.isChecked();
                    if (checked) {
                        selectedBrands.add(value);
                    } else {
                        selectedBrands.remove(value);
                    }

                    selectedCollections.clear();
                    collectionsChipGroup.removeAllViews();
                    collectionsChipGroup.setVisibility(selectedBrands.isEmpty() ? View.GONE : View.VISIBLE);

                    if (!selectedBrands.isEmpty()) {
                        refreshCollectionsChipGroup();
                    } else {
                        updateSearchUiState();
                    }

                    onFiltersChanged();
                });

                brandFilterChipGroup.addView(chip);
            }
        } finally {
            isBuildingBrandChips = false;
        }
    }

    private void refreshCollectionsChipGroup() {
        if (selectedBrands.isEmpty()) {
            selectedCollections.clear();
            collectionsChipGroup.removeAllViews();
            collectionsChipGroup.setVisibility(View.GONE);
            return;
        }

        showBrowseMessage("Loading collections...");
        collectionsChipGroup.setVisibility(View.VISIBLE);

        repo.getCollectionsByBrands(new ArrayList<>(selectedBrands), new PolishesRepository.StringsCallback() {
            @Override
            public void onSuccess(List<String> values) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    isBuildingCollectionChips = true;
                    try {
                        collectionsChipGroup.removeAllViews();

                        if (values == null || values.isEmpty()) {
                            selectedCollections.clear();
                            collectionsChipGroup.setVisibility(View.GONE);
                            return;
                        }

                        collectionsChipGroup.setVisibility(View.VISIBLE);
                        for (String collection : values) {
                            if (collection == null) continue;
                            String val = collection.trim();
                            if (val.isEmpty()) continue;

                            Chip chip = buildChip(val, false);
                            chip.setChecked(selectedCollections.contains(val));

                            chip.setOnClickListener(v -> {
                                if (isBuildingCollectionChips || isRestoringFilters) return;

                                boolean checked = chip.isChecked();
                                if (checked) {
                                    selectedCollections.add(val);
                                } else {
                                    selectedCollections.remove(val);
                                }
                                onFiltersChanged();
                            });

                            collectionsChipGroup.addView(chip);
                        }
                    } finally {
                        isBuildingCollectionChips = false;
                    }

                    updateClearButtonVisibility();
                    updateSearchUiState();
                });
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    selectedCollections.clear();
                    collectionsChipGroup.removeAllViews();
                    collectionsChipGroup.setVisibility(View.GONE);
                    showBrowseMessage(message != null ? message : "No collections found");
                    updateClearButtonVisibility();
                    updateSearchUiState();
                });
            }
        });
    }

    private void onFiltersChanged() {
        if (!isAdded()) return;
        if (!isSearchActive()) {
            updateSearchUiState();
            return;
        }
        updateClearButtonVisibility();
        updateSearchUiState();
        resetPagination();
        loadNextPage();
    }

    private void showBrowseMessage(String message) {
        browseEmptyText.setText(message);
        browseEmptyText.setVisibility(View.VISIBLE);
    }

    private void updateClearButtonVisibility() {
        String query = brandSearchInput.getText() != null ? brandSearchInput.getText().toString().trim() : "";
        boolean show = !query.isEmpty() || !selectedBrands.isEmpty() || !selectedCollections.isEmpty();
        browseClearButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void resetPagination() {
        currentPage = 0;
        isLoading = false;
        hasMore = true;
        currentResults.clear();
        hydratedUids.clear();
        browseResultsAdapter.setItems(new ArrayList<>());
        updateLoadMoreUi();
    }

    private String buildBrowseResultsTitle(String query, List<String> brands, List<String> collections) {
        String cleanQuery = query != null ? query.trim() : "";
        boolean hasBrands = brands != null && !brands.isEmpty();
        boolean hasCollections = collections != null && !collections.isEmpty();

        if (!cleanQuery.isEmpty()) {
            return "Results for \"" + cleanQuery + "\"";
        }

        if (hasBrands && !hasCollections) {
            return "Results for " + joinFirstN(brands, 2);
        }

        if (hasBrands) {
            return "Results for " + joinFirstN(brands, 1) + " / " + joinFirstN(collections, 2);
        }

        return "Browse Results";
    }

    private String joinFirstN(List<String> values, int max) {
        if (values == null || values.isEmpty()) return "";
        int limit = Math.min(max, values.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(", ");
            sb.append(values.get(i));
        }
        if (values.size() > limit) sb.append("...");
        return sb.toString();
    }

    private void maybeLoadMore() {
        if (!isSearchActive()) return;
        if (isLoading || !hasMore) return;

        GridLayoutManager lm = (GridLayoutManager) browseResultsRecycler.getLayoutManager();
        if (lm == null) return;
        int lastVisible = lm.findLastVisibleItemPosition();
        if (lastVisible >= currentResults.size() - 4) {
            loadNextPage();
        }
    }

    private void loadNextPage() {
        if (isLoading) return;
        if (!isSearchActive()) return;
        if (!hasMore) return;

        String query = brandSearchInput.getText() != null ? brandSearchInput.getText().toString().trim() : "";
        List<String> brands = new ArrayList<>(selectedBrands);
        List<String> collections = selectedBrands.isEmpty() ? new ArrayList<>() : new ArrayList<>(selectedCollections);

        int offset = currentPage * SEARCH_PAGE_SIZE;
        isLoading = true;
        Log.d(SEARCH_LOG_TAG, "SEARCH_LOAD_MORE page=" + currentPage + " offset=" + offset);
        updateLoadMoreUi();
        Log.d(SEARCH_LOG_TAG, "SEARCH_PAGE page=" + currentPage + " offset=" + offset);

        browseResultsProgress.setVisibility(View.VISIBLE);
        browseResultsEmpty.setVisibility(View.GONE);

        browseResultsTitle.setText(buildBrowseResultsTitle(query, brands, collections));

        repo.searchPolishesLightweightByNameAndFilters(
                query,
                brands,
                collections,
                SEARCH_PAGE_SIZE,
                offset,
                new PolishesRepository.PolishesCallback() {
                    @Override
                    public void onSuccess(List<Polish> polishes) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            isLoading = false;
                            browseResultsProgress.setVisibility(View.GONE);

                            int count = polishes != null ? polishes.size() : 0;
                            Log.d(SEARCH_LOG_TAG, "SEARCH_FETCH lightweight count=" + count);

                            if (count == 0 && currentResults.isEmpty()) {
                                browseResultsEmpty.setVisibility(View.VISIBLE);
                                browseResultsEmpty.setText("No polishes found");
                            }

                            if (polishes != null) {
                                currentResults.addAll(polishes);
                            }

                            browseResultsAdapter.setItems(new ArrayList<>(currentResults));
                            loadFavoritesIntoAdapters();

                            hasMore = count >= SEARCH_PAGE_SIZE;
                            Log.d(SEARCH_LOG_TAG, "SEARCH_HAS_MORE " + hasMore);
                            if (hasMore) {
                                currentPage++;
                            }

                            hydrateVisible();
                            updateLoadMoreUi();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            isLoading = false;
                            browseResultsProgress.setVisibility(View.GONE);
                            browseResultsEmpty.setVisibility(View.VISIBLE);
                            browseResultsEmpty.setText(message != null ? message : "Failed to load polishes");
                            Log.d(SEARCH_LOG_TAG, "SEARCH_ERROR " + message);
                            updateLoadMoreUi();
                        });
                    }
                }
        );
    }

    private void hydrateVisible() {
        if (!isSearchActive()) return;
        GridLayoutManager lm = (GridLayoutManager) browseResultsRecycler.getLayoutManager();
        if (lm == null) return;
        int first = lm.findFirstVisibleItemPosition();
        int last = lm.findLastVisibleItemPosition();
        if (first < 0 || last < 0) return;

        LinkedHashSet<String> toHydrate = new LinkedHashSet<>();
        for (int i = first; i <= last && i < currentResults.size(); i++) {
            Polish p = currentResults.get(i);
            if (p == null || p.getUid() == null) continue;
            String uid = p.getUid();
            if (hydratedUids.contains(uid)) continue;
            toHydrate.add(uid);
        }

        if (toHydrate.isEmpty()) return;
        Log.d(SEARCH_LOG_TAG, "SEARCH_FETCH uids=" + toHydrate);

        repo.hydratePolishesByUids(toHydrate, new PolishesRepository.PolishesCallback() {
            @Override
            public void onSuccess(List<Polish> polishes) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (polishes == null || polishes.isEmpty()) return;

                    Map<String, Polish> byUid = new HashMap<>();
                    for (Polish p : polishes) {
                        if (p != null && p.getUid() != null) {
                            byUid.put(p.getUid(), p);
                        }
                    }

                    int hydratedCount = 0;
                    for (int i = 0; i < currentResults.size(); i++) {
                        Polish cur = currentResults.get(i);
                        if (cur == null || cur.getUid() == null) continue;
                        Polish full = byUid.get(cur.getUid());
                        if (full != null) {
                            currentResults.set(i, full);
                            hydratedUids.add(full.getUid());
                            hydratedCount++;
                        }
                    }

                    Log.d(SEARCH_LOG_TAG, "SEARCH_HYDRATE count=" + hydratedCount);
                    browseResultsAdapter.setItems(new ArrayList<>(currentResults));
                    loadFavoritesIntoAdapters();
                });
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void selectTab(int index) {
        selectedIndex = index;

        int accent = ContextCompat.getColor(requireContext(), R.color.light_purple);
        int normal = ContextCompat.getColor(requireContext(), R.color.white);
        for (int i = 0; i < tabCards.length; i++) {
            tabCards[i].setCardBackgroundColor(i == index ? accent : normal);
        }

        seasonIcon.setImageResource(TAB_ICONS[index]);
        seasonNameLabel.setText(TAB_NAMES[index]);
        describeSeasonLabel.setText(TAB_DESCRIPTIONS[index]);

        fetchSeasonTrending(false);
    }

    private void fetchSeasonTrending(boolean append) {
        if (seasonTrendingLoading) return;
        if (append && !seasonTrendingHasMore) return;

        seasonTrendingLoading = true;
        if (!append) {
            seasonOrTrendingResults.clear();
            seasonTrendingHasMore = true;
            showLoading();
        } else {
            seasonLoadMoreProgress.setVisibility(View.VISIBLE);
        }
        updateSeasonLoadMoreUi();

        final int offset = append ? seasonOrTrendingResults.size() : 0;

        PolishesRepository.PolishesCallback callback = new PolishesRepository.PolishesCallback() {
            @Override
            public void onSuccess(List<Polish> polishes) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    seasonTrendingLoading = false;
                    trendingProgress.setVisibility(View.GONE);
                    seasonLoadMoreProgress.setVisibility(View.GONE);

                    int count = polishes != null ? polishes.size() : 0;
                    if (!append && count == 0) {
                        trendingError.setText("No polishes found");
                        trendingError.setVisibility(View.VISIBLE);
                        adapter.setItems(new ArrayList<>());
                        seasonOrTrendingResults.clear();
                        seasonTrendingHasMore = false;
                        updateSeasonLoadMoreUi();
                        return;
                    }

                    trendingError.setVisibility(View.GONE);
                    if (polishes != null) {
                        seasonOrTrendingResults.addAll(polishes);
                    }
                    seasonTrendingHasMore = count >= SEASON_PAGE_SIZE;
                    adapter.setItems(new ArrayList<>(seasonOrTrendingResults));
                    loadFavoritesIntoAdapters();
                    updateSeasonLoadMoreUi();
                });
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    seasonTrendingLoading = false;
                    trendingProgress.setVisibility(View.GONE);
                    seasonLoadMoreProgress.setVisibility(View.GONE);
                    trendingError.setText(message != null ? message : "Failed to load");
                    trendingError.setVisibility(View.VISIBLE);
                    if (!append) {
                        adapter.setItems(new ArrayList<>());
                        seasonOrTrendingResults.clear();
                    }
                    updateSeasonLoadMoreUi();
                });
            }
        };

        if (selectedIndex == IDX_TRENDING) {
            repo.getTrendingPolishes(SEASON_PAGE_SIZE, offset, callback);
        } else {
            repo.getPolishesBySeason(SEASON_TAGS[selectedIndex], SEASON_PAGE_SIZE, offset, callback);
        }
    }

    private void showLoading() {
        trendingError.setVisibility(View.GONE);
        trendingProgress.setVisibility(View.VISIBLE);
        adapter.setItems(new ArrayList<>());
    }

    private void openDesigns(Polish polish) {
        if (polish == null || polish.getUid() == null) return;
        Intent intent = new Intent(requireContext(), DesignsByPolishActivity.class);
        intent.putExtra(DesignsByPolishActivity.EXTRA_POLISH_UID, polish.getUid());
        intent.putExtra(DesignsByPolishActivity.EXTRA_POLISH_NAME,
                polish.getShadeName() != null ? polish.getShadeName() : "");
        startActivity(intent);
    }

    private void loadFavoritesIntoAdapters() {
        favoritesRepo.getMyFavoritePolishes(new FavoritesRepository.FavoritesListCallback() {
            @Override
            public void onSuccess(Set<String> polishUids) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    adapter.setFavoriteUids(polishUids);
                    browseResultsAdapter.setFavoriteUids(polishUids);
                });
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private Chip buildChip(String text, boolean closable) {
        Chip chip = new Chip(requireContext());
        chip.setText(text);
        chip.setCheckable(!closable);
        chip.setClickable(true);
        if (closable) {
            chip.setCloseIconVisible(true);
        }
        return chip;
    }

    private void restoreDefaultMode() {
        isRestoringFilters = true;
        try {
            brandSearchInput.setText("");
            selectedBrands.clear();
            selectedCollections.clear();

            for (int i = 0; i < brandFilterChipGroup.getChildCount(); i++) {
                View child = brandFilterChipGroup.getChildAt(i);
                if (child instanceof Chip) {
                    ((Chip) child).setChecked(false);
                }
            }

            collectionsChipGroup.removeAllViews();
            collectionsChipGroup.setVisibility(View.GONE);
            browseEmptyText.setVisibility(View.GONE);

            hideBrowseResultsInline();
            updateClearButtonVisibility();
            updateSearchUiState();
        } finally {
            isRestoringFilters = false;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(STATE_SELECTED_BRANDS, new ArrayList<>(selectedBrands));
        outState.putStringArrayList(STATE_SELECTED_COLLECTIONS, new ArrayList<>(selectedCollections));
        outState.putString(STATE_SEARCH_QUERY,
                brandSearchInput.getText() != null ? brandSearchInput.getText().toString() : "");
    }
}