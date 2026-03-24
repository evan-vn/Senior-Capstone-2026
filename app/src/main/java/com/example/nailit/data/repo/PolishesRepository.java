package com.example.nailit.data.repo;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.nailit.data.api.PolishesApi;
import com.example.nailit.data.model.AiMatchedOption;
import com.example.nailit.data.model.AiSuggestionOption;
import com.example.nailit.data.model.AiSuggestionPayload;
import com.example.nailit.data.model.Polish;
import com.example.nailit.data.network.ApiClient;
import com.example.nailit.data.network.RetrofitUtil;
import com.example.nailit.data.network.TokenStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PolishesRepository {

    private static final String TAG = "PolishesRepository";
    private static final String FETCH_DEBUG_TAG = "AI_FETCH_DEBUG";
    private static final int MIN_MATCH_SCORE = 6;
    private static final int MIN_FALLBACK_SCORE = 5;

    private static final String SELECT_LIST_BASE =
            "uid,brand,collection,shade_name,shade_code,description,hex,swatch_images,thumbnail_data";
    private static final String SELECT_LIST_TRENDING =
            "uid,brand,collection,shade_name,shade_code,description,hex,favorite_count,swatch_images";
    private static final String SELECT_LIST_AI =
            "uid,brand,collection,shade_name,shade_code,description,hex,swatch_images,thumbnail_data,season_labels,occasion_labels";
    private static final String SELECT_LIST_AI_LIGHTWEIGHT =
            "uid,brand,collection,shade_name,shade_code,description,hex,season_labels,occasion_labels";
    private static final int AI_PAGE_SIZE = 100;
    private static final int AI_MAX_ROWS = 300;

    private final PolishesApi polishesApi;
    private final PolishColorClassifier colorClassifier;
    private final TokenStore tokenStore;

    public interface PolishesCallback {
        void onSuccess(List<Polish> polishes);
        void onError(String message);
    }

    public interface AiPolishesCallback {
        void onSuccess(List<AiMatchedOption> options);
        void onError(String message);
    }

    public PolishesRepository(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
        this.polishesApi = ApiClient.getInstance(tokenStore).create(PolishesApi.class);
        this.colorClassifier = new PolishColorClassifier();
    }

    public void getTrendingPolishes(PolishesCallback callback) {
        polishesApi.getTrendingPolishes(SELECT_LIST_TRENDING, "favorite_count.desc", "30")
                .enqueue(new Callback<List<Polish>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Polish>> call,
                                           @NonNull Response<List<Polish>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            callback.onError(RetrofitUtil.extractError("Trending", response));
                            return;
                        }
                        callback.onSuccess(response.body());
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Polish>> call, @NonNull Throwable t) {
                        callback.onError("Trending network error: " + t.getMessage());
                    }
                });
    }

    public void getPolishesByUids(Collection<String> uids, PolishesCallback callback) {
        if (uids == null || uids.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }
        StringBuilder sb = new StringBuilder("in.(");
        boolean first = true;
        for (String uid : uids) {
            if (!first) sb.append(",");
            sb.append(uid);
            first = false;
        }
        sb.append(")");

        polishesApi.getPolishesByUids(SELECT_LIST_BASE, sb.toString())
                .enqueue(new Callback<List<Polish>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Polish>> call,
                                           @NonNull Response<List<Polish>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            callback.onError(RetrofitUtil.extractError("Polishes by UIDs", response));
                            return;
                        }
                        callback.onSuccess(response.body());
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Polish>> call, @NonNull Throwable t) {
                        callback.onError("Polishes network error: " + t.getMessage());
                    }
                });
    }

    public void getPolishesByAiSuggestions(AiSuggestionPayload payload, AiPolishesCallback callback) {
        Log.d(FETCH_DEBUG_TAG, "ENTER getPolishesByAiSuggestions");
        List<AiSuggestionOption> options = payload != null ? payload.getOptions() : new ArrayList<>();
        if (options.isEmpty()) {
            Log.d(FETCH_DEBUG_TAG, "EXIT getPolishesByAiSuggestions options=0");
            callback.onSuccess(new ArrayList<>());
            return;
        }
        getAllPolishesForAiMatchingLightweight(new PolishesCallback() {
            @Override
            public void onSuccess(List<Polish> lightweightPolishes) {
                List<AiMatchedOption> ranked = rankByOption(lightweightPolishes, options);
                hydrateMatchedOptions(ranked, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void getPolishesBySeason(String seasonTag, PolishesCallback callback) {
        String filter = "cs.[\"" + seasonTag + "\"]";
        polishesApi.getPolishesBySeason(SELECT_LIST_BASE, filter, "30")
                .enqueue(new Callback<List<Polish>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Polish>> call,
                                           @NonNull Response<List<Polish>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            callback.onError(RetrofitUtil.extractError("Season/" + seasonTag, response));
                            return;
                        }
                        callback.onSuccess(response.body());
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Polish>> call, @NonNull Throwable t) {
                        callback.onError("Season network error: " + t.getMessage());
                    }
                });
    }

    public void getAllPolishesForAiMatching(PolishesCallback callback) {
        Log.d(FETCH_DEBUG_TAG, "ENTER getAllPolishesForAiMatching");
        fetchAiDataset(SELECT_LIST_AI, callback, true);
    }

    public void getAllPolishesForAiMatchingLightweight(PolishesCallback callback) {
        Log.d(FETCH_DEBUG_TAG, "ENTER getAllPolishesForAiMatchingLightweight");
        fetchAiDatasetLightweightPaged(0, 1, new ArrayList<>(), callback);
    }

    //Temporary debug control fetch to isolate Neon fetch from AI scoring pipeline.
    public void debugControlFetch(PolishesCallback callback) {
        String token = tokenStore.getAccessToken();
        boolean hasToken = token != null && !token.trim().isEmpty();
        Log.d(FETCH_DEBUG_TAG, "ENTER debugControlFetch");
        Log.d(FETCH_DEBUG_TAG, "request=/polishes");
        Log.d(FETCH_DEBUG_TAG, "limit=20");
        Log.d(FETCH_DEBUG_TAG, "select=<none>");
        Log.d(FETCH_DEBUG_TAG, "hasToken=" + hasToken);
        polishesApi.getPolishesSimple("20").enqueue(new Callback<List<Polish>>() {
            @Override
            public void onResponse(@NonNull Call<List<Polish>> call,
                                   @NonNull Response<List<Polish>> response) {
                if (!response.isSuccessful()) {
                    String errorBody = "";
                    try {
                        if (response.errorBody() != null) {
                            errorBody = response.errorBody().string();
                        }
                    } catch (Exception ignored) {
                    }
                    Log.e(FETCH_DEBUG_TAG, "control failure code=" + response.code()
                            + " responseNull=" + (response == null)
                            + " bodyNull=" + (response.body() == null)
                            + " errorBody=" + errorBody);
                    callback.onError("Control fetch failed: HTTP " + response.code());
                    return;
                }

                List<Polish> rows = response.body() != null ? response.body() : new ArrayList<>();
                Log.d(FETCH_DEBUG_TAG, "control success code=" + response.code() + " count=" + rows.size());
                Log.d(FETCH_DEBUG_TAG, "control firstNames=" + firstNames(rows, 5));
                callback.onSuccess(rows);
            }

            @Override
            public void onFailure(@NonNull Call<List<Polish>> call, @NonNull Throwable t) {
                Log.e(FETCH_DEBUG_TAG, "control onFailure message=" + t.getMessage(), t);
                callback.onError("Control fetch onFailure: " + t.getMessage());
            }
        });
    }

    private void fetchAiDataset(String selectList,
                                PolishesCallback callback,
                                boolean allowBaseSelectFallback) {
        String token = tokenStore.getAccessToken();
        boolean hasToken = token != null && !token.trim().isEmpty();
        Log.d(FETCH_DEBUG_TAG, "request=/polishes");
        Log.d(FETCH_DEBUG_TAG, "limit=500");
        Log.d(FETCH_DEBUG_TAG, "select=" + selectList);
        Log.d(FETCH_DEBUG_TAG, "hasToken=" + hasToken);
        polishesApi.getPolishesForAi(selectList, "500")
                .enqueue(new Callback<List<Polish>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Polish>> call,
                                           @NonNull Response<List<Polish>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            String error = RetrofitUtil.extractError("AI polish lookup", response);
                            String errorBody = "";
                            try {
                                if (response.errorBody() != null) {
                                    errorBody = response.errorBody().string();
                                }
                            } catch (Exception ignored) {
                            }
                            Log.e(FETCH_DEBUG_TAG, "failure code=" + response.code()
                                    + " responseNull=" + (response == null)
                                    + " bodyNull=" + (response.body() == null)
                                    + " allowBaseSelectFallback=" + allowBaseSelectFallback
                                    + " errorBody=" + errorBody);
                            if (allowBaseSelectFallback && response.code() == 400) {
                                Log.w(FETCH_DEBUG_TAG, "Retrying AI fetch with SELECT_LIST_BASE after HTTP 400");
                                fetchAiDataset(SELECT_LIST_BASE, callback, false);
                                return;
                            }
                            callback.onError(error);
                            return;
                        }
                        List<Polish> rows = response.body();
                        Log.d(FETCH_DEBUG_TAG, "success code=" + response.code() + " count=" + rows.size());
                        Log.d(FETCH_DEBUG_TAG, "firstNames=" + firstNames(rows, 5));
                        logCandidateFetch("AI_FULL_DATASET", response.body());
                        callback.onSuccess(response.body());
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Polish>> call, @NonNull Throwable t) {
                        Log.e(FETCH_DEBUG_TAG, "onFailure allowBaseSelectFallback=" + allowBaseSelectFallback
                                + " message=" + t.getMessage(), t);
                        if (allowBaseSelectFallback) {
                            Log.w(FETCH_DEBUG_TAG, "Retrying AI fetch with SELECT_LIST_BASE after onFailure");
                            fetchAiDataset(SELECT_LIST_BASE, callback, false);
                            return;
                        }
                        callback.onError("AI polish lookup failed: " + t.getMessage());
                    }
                });
    }

    private void fetchAiDatasetLightweightPaged(int offset,
                                                int pageNumber,
                                                List<Polish> accumulated,
                                                PolishesCallback callback) {
        String token = tokenStore.getAccessToken();
        boolean hasToken = token != null && !token.trim().isEmpty();
        Log.d(FETCH_DEBUG_TAG, "request=/polishes");
        Log.d(FETCH_DEBUG_TAG, "select=" + SELECT_LIST_AI_LIGHTWEIGHT);
        Log.d(FETCH_DEBUG_TAG, "limit=" + AI_PAGE_SIZE);
        Log.d(FETCH_DEBUG_TAG, "offset=" + offset);
        Log.d(FETCH_DEBUG_TAG, "hasToken=" + hasToken);

        polishesApi.getPolishesForAiPaged(
                        SELECT_LIST_AI_LIGHTWEIGHT,
                        String.valueOf(AI_PAGE_SIZE),
                        String.valueOf(offset),
                        "uid.asc")
                .enqueue(new Callback<List<Polish>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Polish>> call,
                                           @NonNull Response<List<Polish>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            String errorBody = "";
                            try {
                                if (response.errorBody() != null) {
                                    errorBody = response.errorBody().string();
                                }
                            } catch (Exception ignored) {
                            }
                            Log.e(FETCH_DEBUG_TAG, "lightweight failure code=" + response.code()
                                    + " page=" + pageNumber
                                    + " errorBody=" + errorBody);
                            callback.onError(RetrofitUtil.extractError("AI lightweight fetch", response));
                            return;
                        }

                        List<Polish> pageRows = response.body();
                        int pageCount = pageRows.size();
                        accumulated.addAll(pageRows);
                        Log.d(FETCH_DEBUG_TAG, "page=" + pageNumber + " count=" + pageCount);
                        Log.d(FETCH_DEBUG_TAG, "accumulated=" + accumulated.size());

                        boolean pageExhausted = pageCount < AI_PAGE_SIZE;
                        boolean hitMax = accumulated.size() >= AI_MAX_ROWS;
                        if (pageExhausted || hitMax) {
                            List<Polish> finalRows = accumulated;
                            if (accumulated.size() > AI_MAX_ROWS) {
                                finalRows = new ArrayList<>(accumulated.subList(0, AI_MAX_ROWS));
                            }
                            Log.d(FETCH_DEBUG_TAG, "lightweight fetch success total=" + finalRows.size());
                            logCandidateFetch("AI_FULL_DATASET_LIGHT", finalRows);
                            callback.onSuccess(finalRows);
                            return;
                        }

                        fetchAiDatasetLightweightPaged(
                                offset + AI_PAGE_SIZE,
                                pageNumber + 1,
                                accumulated,
                                callback
                        );
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Polish>> call, @NonNull Throwable t) {
                        Log.e(FETCH_DEBUG_TAG, "lightweight onFailure page=" + pageNumber
                                + " message=" + t.getMessage(), t);
                        callback.onError("AI lightweight fetch failed: " + t.getMessage());
                    }
                });
    }

    private void hydrateMatchedOptions(List<AiMatchedOption> ranked,
                                       AiPolishesCallback callback) {
        Set<String> selectedUids = new HashSet<>();
        for (AiMatchedOption option : ranked) {
            if (option == null || option.getMatches() == null) continue;
            for (Polish polish : option.getMatches()) {
                if (polish != null && polish.getUid() != null && !polish.getUid().trim().isEmpty()) {
                    selectedUids.add(polish.getUid());
                }
            }
        }
        Log.d(FETCH_DEBUG_TAG, "selectedUIDs=" + selectedUids);

        if (selectedUids.isEmpty()) {
            callback.onSuccess(ranked);
            return;
        }

        getPolishesByUids(selectedUids, new PolishesCallback() {
            @Override
            public void onSuccess(List<Polish> detailedPolishes) {
                Map<String, Polish> byUid = new LinkedHashMap<>();
                for (Polish polish : detailedPolishes) {
                    if (polish != null && polish.getUid() != null) {
                        byUid.put(polish.getUid(), polish);
                    }
                }

                List<AiMatchedOption> hydrated = new ArrayList<>();
                for (AiMatchedOption option : ranked) {
                    List<Polish> upgraded = new ArrayList<>();
                    if (option.getMatches() != null) {
                        for (Polish p : option.getMatches()) {
                            if (p == null || p.getUid() == null) continue;
                            Polish full = byUid.get(p.getUid());
                            upgraded.add(full != null ? full : p);
                        }
                    }
                    hydrated.add(new AiMatchedOption(
                            option.getLabel(),
                            option.getColorKeywords(),
                            option.getSeasonTags(),
                            option.getOccasionTags(),
                            upgraded
                    ));
                }
                callback.onSuccess(hydrated);
            }

            @Override
            public void onError(String message) {
                Log.w(FETCH_DEBUG_TAG, "detail hydration failed, using lightweight matches: " + message);
                callback.onSuccess(ranked);
            }
        });
    }

    private List<AiMatchedOption> rankByOption(List<Polish> polishes, List<AiSuggestionOption> options) {
        List<AiMatchedOption> grouped = new ArrayList<>();
        Set<String> usedAcrossOptions = new HashSet<>();

        for (AiSuggestionOption option : options) {
            OptionIntent intent = parseIntent(option);
            Log.d(TAG, "AI option='" + intent.label + "' requestedColor=" + intent.requestedColorFamily
                    + " requestedFinish=" + intent.finish
                    + " requestedDepth=" + intent.depth
                    + " targetHSV=" + formatHsv(intent.targetHsv));

            List<ScoredPolish> strictCandidates = new ArrayList<>();
            List<ScoredPolish> fallbackCandidates = new ArrayList<>();
            for (Polish polish : polishes) {
                String polishColor = colorClassifier.classify(polish);
                String shade = safe(polish.getShadeName());
                boolean strictCompatible = isColorCompatible(intent.requestedColorFamily, polishColor);
                Log.d(TAG, "AI_COLOR_GATE requested=" + intent.requestedColorFamily
                        + " polish=" + polishColor
                        + " result=" + (strictCompatible ? "PASS" : "REJECT")
                        + " shade='" + shade + "'");
                if (normalize(shade).contains("swing the sax")) {
                    Log.d(TAG, "POLISH_SWING_CLASSIFIER " + colorClassifier.explainClassification(polish));
                }

                if (!strictCompatible) {
                    boolean relaxedCompatible = isRelaxedNeighborCompatible(intent.requestedColorFamily, polishColor, polish);
                    if (relaxedCompatible) {
                        Log.d(TAG, "AI_COLOR_GATE_FALLBACK requested=" + intent.requestedColorFamily
                                + " polish=" + polishColor + " result=PASS_RELAXED");
                    } else {
                        continue;
                    }
                }

                ScoreBreakdown b = scoreWithinFamily(intent, polish);
                int total = b.totalScore();
                Log.d(TAG, "AI_SCORE option='" + intent.label
                        + "' polish='" + shade
                        + "' family=" + polishColor
                        + " desc=" + b.descriptionScore
                        + " shade=" + b.shadeScore
                        + " finish=" + b.finishScore
                        + " labels=" + b.labelScore
                        + " hex=" + b.hexScore
                        + " hsv=" + b.hsvScore
                        + " total=" + total);

                if (strictCompatible && total >= MIN_MATCH_SCORE) {
                    strictCandidates.add(new ScoredPolish(polish, total));
                }
                if (!strictCompatible && total >= MIN_FALLBACK_SCORE) {
                    fallbackCandidates.add(new ScoredPolish(polish, total));
                }
            }

            Collections.sort(strictCandidates, Comparator.comparingInt((ScoredPolish p) -> p.score).reversed());
            Collections.sort(fallbackCandidates, Comparator.comparingInt((ScoredPolish p) -> p.score).reversed());

            Log.d(TAG, "AI_COUNTS option='" + intent.label
                    + "' requestedFamily=" + intent.requestedColorFamily
                    + " strictCount=" + strictCandidates.size()
                    + " familyFallbackCount=" + fallbackCandidates.size());

            List<Polish> matches = new ArrayList<>();
            for (ScoredPolish s : strictCandidates) {
                if (s.polish == null || s.polish.getUid() == null) continue;
                if (usedAcrossOptions.contains(s.polish.getUid())) continue;
                matches.add(s.polish);
                usedAcrossOptions.add(s.polish.getUid());
                if (matches.size() >= 3) break;
            }

            //Level 2 fallback: family-compatible only (no global relaxed fallback).
            if (matches.isEmpty()) {
                Log.d(TAG, "AI_FALLBACK option='" + intent.label + "' using family-compatible fallback only");
                for (ScoredPolish s : fallbackCandidates) {
                    if (s.polish == null || s.polish.getUid() == null) continue;
                    if (usedAcrossOptions.contains(s.polish.getUid())) continue;
                    matches.add(s.polish);
                    usedAcrossOptions.add(s.polish.getUid());
                    if (matches.size() >= 2) break;
                }
            }

            //Level 3: empty result (no cross-family fallback).
            Log.d(TAG, "AI_FINAL option='" + intent.label + "' selectedCount=" + matches.size());

            grouped.add(new AiMatchedOption(
                    intent.label,
                    option.getColorKeywords(),
                    option.getSeasonTags(),
                    option.getOccasionTags(),
                    matches
            ));
        }

        return grouped;
    }

    private OptionIntent parseIntent(AiSuggestionOption option) {
        String label = normalize(option.getLabel());
        String baseColor = normalize(option.getBaseColor());
        String finish = normalize(option.getFinish());
        String depth = normalize(option.getDepth());
        List<String> colorKeywords = normalizeList(option.getColorKeywords());
        List<String> styleKeywords = normalizeList(option.getStyleKeywords());
        List<String> seasonTags = normalizeList(option.getSeasonTags());
        List<String> occasionTags = normalizeList(option.getOccasionTags());

        StringBuilder raw = new StringBuilder(label);
        for (String keyword : colorKeywords) {
            raw.append(" ").append(keyword);
        }
        for (String keyword : styleKeywords) {
            raw.append(" ").append(keyword);
        }
        if (!finish.isEmpty()) {
            raw.append(" ").append(finish);
        }
        if (!depth.isEmpty()) {
            raw.append(" ").append(depth);
        }
        String requestedColor = !baseColor.isEmpty()
                ? colorClassifier.classifyOptionIntent(baseColor, colorKeywords)
                : colorClassifier.classifyOptionIntent(label, colorKeywords);
        String hueFamily = normalize(option.getTargetHsvHint().getHueFamily());
        String saturationBucket = normalize(option.getTargetHsvHint().getSaturation());
        String valueBucket = normalize(option.getTargetHsvHint().getValue());

        Set<String> keywordTokens = tokenize(raw.toString());
        Set<String> finishTokens = extractFinishTokens(keywordTokens);
        if (!finish.isEmpty()) {
            finishTokens.add(finish);
        }
        StringBuilder styleJoined = new StringBuilder();
        for (String keyword : styleKeywords) {
            if (styleJoined.length() > 0) styleJoined.append(" ");
            styleJoined.append(keyword);
        }
        finishTokens.addAll(tokenize(styleJoined.toString()));

        OptionIntentSeed seed = new OptionIntentSeed(
                label.isEmpty() ? "Suggested style" : label,
                requestedColor,
                baseColor,
                depth,
                hueFamily,
                saturationBucket,
                valueBucket
        );
        HsvUtils.HsvColor targetHsv = resolveTargetHsv(seed);

        return new OptionIntent(
                label.isEmpty() ? "Suggested style" : label,
                requestedColor,
                finish,
                depth,
                keywordTokens,
                finishTokens,
                new HashSet<>(seasonTags),
                new HashSet<>(occasionTags),
                targetHsv
        );
    }

    private ScoreBreakdown scoreWithinFamily(OptionIntent intent, Polish polish) {
        String description = normalize(polish.getDescription());
        String shade = normalize(polish.getShadeName());
        String collection = normalize(polish.getCollection());
        Set<String> descriptionTokens = tokenize(description);
        Set<String> shadeTokens = tokenize(shade);
        Set<String> collectionTokens = tokenize(collection);

        int descriptionScore = overlap(intent.keywordTokens, descriptionTokens) * 8;
        int shadeScore = overlap(intent.keywordTokens, shadeTokens) * 7;
        int finishScore = (overlap(intent.finishTokens, descriptionTokens)
                + overlap(intent.finishTokens, shadeTokens)) * 5;
        int labelScore = overlap(intent.seasonTags, new HashSet<>(normalizeList(polish.getSeasonLabels()))) * 2
                + overlap(intent.occasionTags, new HashSet<>(normalizeList(polish.getOccasionLabels()))) * 2
                + overlap(intent.keywordTokens, collectionTokens) * 1;
        int hexScore = weakHexSupport(intent.requestedColorFamily, polish.getHex());
        int hsvScore = scoreHsvBonus(intent, polish);

        return new ScoreBreakdown(descriptionScore, shadeScore, finishScore, labelScore, hexScore, hsvScore);
    }

    private int scoreHsvBonus(OptionIntent intent, Polish polish) {
        HsvUtils.HsvColor candidate = HsvUtils.fromHex(polish.getHex());
        if (intent.targetHsv == null || candidate == null) {
            return 0;
        }
        boolean hueWeak = isHueWeakFamily(intent.requestedColorFamily);
        double distance = HsvUtils.hsvDistance(intent.targetHsv, candidate, hueWeak);
        int bonus;
        if (distance <= 0.12d) {
            bonus = 8;
        } else if (distance <= 0.24d) {
            bonus = 5;
        } else if (distance <= 0.36d) {
            bonus = 2;
        } else if (distance >= 0.60d) {
            bonus = -2;
        } else {
            bonus = 0;
        }
        Log.d(TAG, "AI_HSV_SCORE option='" + intent.label
                + "' polish='" + safe(polish.getShadeName())
                + "' candidateHSV=" + formatHsv(candidate)
                + " hsvDistance=" + String.format(Locale.US, "%.2f", distance)
                + " hsvBonus=" + bonus);
        return bonus;
    }

    private boolean isColorCompatible(String requestedFamily, String polishFamily) {
        if (requestedFamily == null || requestedFamily.isEmpty()) return false;
        if (polishFamily == null || polishFamily.isEmpty()) return false;
        if (requestedFamily.equals(polishFamily)) return true;

        if (requestedFamily.equals(PolishColorClassifier.BURGUNDY)) {
            return polishFamily.equals(PolishColorClassifier.RED)
                    || polishFamily.equals(PolishColorClassifier.PURPLE)
                    || polishFamily.equals(PolishColorClassifier.BURGUNDY);
        }
        if (requestedFamily.equals(PolishColorClassifier.GOLD_BRONZE)) {
            return polishFamily.equals(PolishColorClassifier.GOLD_BRONZE);
        }
        if (requestedFamily.equals(PolishColorClassifier.BLACK)) {
            return polishFamily.equals(PolishColorClassifier.BLACK)
                    || polishFamily.equals(PolishColorClassifier.GRAY_SILVER);
        }
        if (requestedFamily.equals(PolishColorClassifier.NUDE)) {
            return polishFamily.equals(PolishColorClassifier.NUDE)
                    || polishFamily.equals(PolishColorClassifier.PINK);
        }
        return requestedFamily.equals(polishFamily);
    }

    private boolean isRelaxedNeighborCompatible(String requestedFamily, String polishFamily, Polish polish) {
        if (requestedFamily == null || requestedFamily.isEmpty()) return false;
        if (polishFamily == null || polishFamily.isEmpty()) return false;
        if (requestedFamily.equals(PolishColorClassifier.BLACK)) {
            return polishFamily.equals(PolishColorClassifier.GRAY_SILVER) && hasDarkCue(polish);
        }
        if (requestedFamily.equals(PolishColorClassifier.BURGUNDY)
                || requestedFamily.equals(PolishColorClassifier.RED)) {
            return polishFamily.equals(PolishColorClassifier.PURPLE) && hasAnyCue(polish,
                    "burgundy", "wine", "maroon", "deep red", "oxblood");
        }
        if (requestedFamily.equals(PolishColorClassifier.PURPLE)) {
            return polishFamily.equals(PolishColorClassifier.RED) && hasAnyCue(polish,
                    "plum", "berry", "violet", "eggplant", "mauve");
        }
        if (requestedFamily.equals(PolishColorClassifier.NUDE)) {
            return polishFamily.equals(PolishColorClassifier.PINK) && hasAnyCue(polish,
                    "nude", "beige", "taupe", "blush nude", "neutral");
        }
        return false;
    }

    private int weakHexSupport(String requestedColorFamily, String hex) {
        String hexFamily = classifyHexFamily(hex);
        if (hexFamily.isEmpty()) return 0;
        if (hexFamily.equals(requestedColorFamily)) return 2;
        if ((requestedColorFamily.equals(PolishColorClassifier.GRAY_SILVER)
                && hexFamily.equals(PolishColorClassifier.WHITE))
                || (requestedColorFamily.equals(PolishColorClassifier.WHITE)
                && hexFamily.equals(PolishColorClassifier.GRAY_SILVER))) {
            return 1;
        }
        return 0;
    }

    private String classifyHexFamily(String hexValue) {
        String hex = normalizeHex(hexValue);
        if (hex.length() != 6) return "";
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            int lum = (int) (0.2126 * r + 0.7152 * g + 0.0722 * b);
            boolean neutral = Math.abs(r - g) < 20 && Math.abs(g - b) < 20;

            if (lum < 45) return PolishColorClassifier.BLACK;
            if (lum > 235) return PolishColorClassifier.WHITE;
            if (neutral && lum > 165) return PolishColorClassifier.GRAY_SILVER;
            if (r > g + 35 && r > b + 35) return PolishColorClassifier.RED;
            if (b > r + 20 && b > g + 20) return PolishColorClassifier.PURPLE;
            if (r > 190 && g > 145 && b < 120) return PolishColorClassifier.GOLD_BRONZE;
            return "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private Set<String> extractFinishTokens(Set<String> tokens) {
        Set<String> out = new HashSet<>();
        String[] styles = new String[]{
                "glossy", "metallic", "shimmer", "glitter", "chrome",
                "matte", "sheer", "elegant", "chic", "deep", "bold", "soft", "dramatic"
        };
        for (String style : styles) {
            if (tokens.contains(style)) out.add(style);
        }
        if (tokens.contains("glamorous") || tokens.contains("glam")) {
            out.add("glitter");
            out.add("shimmer");
            out.add("glossy");
        }
        return out;
    }

    private boolean hasDarkCue(Polish polish) {
        String text = normalize(safe(polish.getDescription()) + " " + safe(polish.getShadeName()));
        return containsWord(text, "dark")
                || containsWord(text, "charcoal")
                || containsWord(text, "graphite")
                || containsWord(text, "smoky")
                || containsWord(text, "midnight");
    }

    private boolean hasGoldCue(Polish polish) {
        String text = normalize(safe(polish.getDescription()) + " "
                + safe(polish.getShadeName()) + " "
                + safe(polish.getCollection()));
        return containsWord(text, "gold")
                || containsWord(text, "golden")
                || containsWord(text, "champagne")
                || containsWord(text, "bronze")
                || containsWord(text, "gilded")
                || (containsWord(text, "warm") && containsWord(text, "metallic"));
    }

    private boolean hasAnyCue(Polish polish, String... cues) {
        String text = normalize(safe(polish.getDescription()) + " " + safe(polish.getShadeName()));
        for (String cue : cues) {
            if (containsWord(text, normalize(cue))) return true;
        }
        return false;
    }

    private boolean containsWord(String text, String word) {
        String padded = " " + normalize(text) + " ";
        String w = " " + normalize(word) + " ";
        return padded.contains(w);
    }

    private int overlap(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) return 0;
        int score = 0;
        for (String token : left) {
            if (right.contains(token)) score++;
        }
        return score;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.US).replaceAll("[^a-z0-9\\s]+", " ").replaceAll("\\s+", " ").trim();
    }

    private List<String> normalizeList(List<String> values) {
        List<String> out = new ArrayList<>();
        if (values == null) return out;
        for (String value : values) {
            String clean = normalize(value);
            if (!clean.isEmpty()) out.add(clean);
        }
        return out;
    }

    private Set<String> tokenize(String text) {
        Set<String> out = new HashSet<>();
        if (text == null || text.trim().isEmpty()) return out;
        String[] parts = normalize(text).split("\\s+");
        for (String part : parts) {
            if (part.length() >= 2) out.add(part);
        }
        return out;
    }

    private String normalizeHex(String value) {
        if (value == null) return "";
        return value.trim().replace("#", "").toLowerCase(Locale.US);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static class ScoredPolish {
        final Polish polish;
        final int score;

        ScoredPolish(Polish polish, int score) {
            this.polish = polish;
            this.score = score;
        }
    }

    private static class OptionIntent {
        final String label;
        final String requestedColorFamily;
        final String finish;
        final String depth;
        final Set<String> keywordTokens;
        final Set<String> finishTokens;
        final Set<String> seasonTags;
        final Set<String> occasionTags;
        final HsvUtils.HsvColor targetHsv;

        OptionIntent(String label,
                     String requestedColorFamily,
                     String finish,
                     String depth,
                     Set<String> keywordTokens,
                     Set<String> finishTokens,
                     Set<String> seasonTags,
                     Set<String> occasionTags,
                     HsvUtils.HsvColor targetHsv) {
            this.label = label;
            this.requestedColorFamily = requestedColorFamily;
            this.finish = finish;
            this.depth = depth;
            this.keywordTokens = keywordTokens;
            this.finishTokens = finishTokens;
            this.seasonTags = seasonTags;
            this.occasionTags = occasionTags;
            this.targetHsv = targetHsv;
        }
    }

    private void logCandidateFetch(String source, List<Polish> candidates) {
        int total = candidates != null ? candidates.size() : 0;
        boolean hasSwing = false;
        if (candidates != null) {
            for (Polish p : candidates) {
                String shade = normalize(safe(p.getShadeName()));
                if (shade.contains("swing the sax")) {
                    hasSwing = true;
                    Log.d(TAG, "CANDIDATE_SWING source=" + source
                            + " shade=" + safe(p.getShadeName())
                            + " desc=" + safe(p.getDescription())
                            + " hex=" + safe(p.getHex())
                            + " seasons=" + p.getSeasonLabels()
                            + " occasions=" + p.getOccasionLabels());
                    break;
                }
            }
        }
        Log.d(TAG, "CANDIDATE_FETCH source=" + source + " total=" + total + " containsSwingTheSax=" + hasSwing);
    }

    private static class ScoreBreakdown {
        final int descriptionScore;
        final int shadeScore;
        final int finishScore;
        final int labelScore;
        final int hexScore;
        final int hsvScore;

        ScoreBreakdown(int descriptionScore,
                       int shadeScore,
                       int finishScore,
                       int labelScore,
                       int hexScore,
                       int hsvScore) {
            this.descriptionScore = descriptionScore;
            this.shadeScore = shadeScore;
            this.finishScore = finishScore;
            this.labelScore = labelScore;
            this.hexScore = hexScore;
            this.hsvScore = hsvScore;
        }

        int totalScore() {
            return descriptionScore + shadeScore + finishScore + labelScore + hexScore + hsvScore;
        }
    }

    private boolean isHueWeakFamily(String requestedFamily) {
        return PolishColorClassifier.BLACK.equals(requestedFamily)
                || PolishColorClassifier.GRAY_SILVER.equals(requestedFamily)
                || PolishColorClassifier.WHITE.equals(requestedFamily);
    }

    private HsvUtils.HsvColor resolveTargetHsv(OptionIntentSeed seed) {
        String hueFamily = normalize(seed.hueFamily);
        String base = normalize(seed.baseColor);
        String depth = normalize(seed.depth);
        String saturationBucket = normalize(seed.saturationBucket);
        String valueBucket = normalize(seed.valueBucket);

        double hue = hueFromFamily(hueFamily, base);
        double saturation = bucketToNumber(saturationBucket, "s");
        double value = bucketToNumber(valueBucket, "v");

        if (depth.equals("deep")) value = Math.min(value, 0.40d);
        if (depth.equals("light")) value = Math.max(value, 0.75d);

        if (PolishColorClassifier.BLACK.equals(seed.requestedFamily)) {
            saturation = Math.min(saturation, 0.35d);
            value = Math.min(value, 0.22d);
        } else if (PolishColorClassifier.GRAY_SILVER.equals(seed.requestedFamily)) {
            saturation = Math.min(saturation, 0.20d);
            value = Math.max(value, 0.72d);
        } else if (PolishColorClassifier.GOLD_BRONZE.equals(seed.requestedFamily)) {
            saturation = Math.max(0.45d, saturation);
            value = Math.max(0.62d, value);
        } else if (PolishColorClassifier.NUDE.equals(seed.requestedFamily)) {
            saturation = Math.min(saturation, 0.38d);
            value = Math.max(value, 0.68d);
        } else if (PolishColorClassifier.BURGUNDY.equals(seed.requestedFamily)) {
            saturation = Math.max(0.55d, saturation);
            value = Math.min(value, 0.45d);
            hue = 340d;
        }

        HsvUtils.HsvColor target = new HsvUtils.HsvColor(hue, clamp01(saturation), clamp01(value));
        Log.d(TAG, "AI_HSV target option='" + seed.label + "' h=" + target.h + " s=" + target.s + " v=" + target.v);
        return target;
    }

    private String formatHsv(HsvUtils.HsvColor c) {
        if (c == null) return "null";
        return String.format(Locale.US, "(h=%.0f,s=%.2f,v=%.2f)", c.h, c.s, c.v);
    }

    private double hueFromFamily(String hueFamily, String baseColor) {
        String key = !hueFamily.isEmpty() ? hueFamily : baseColor;
        if (key.contains("red-purple") || key.contains("burgundy") || key.contains("wine")) return 340d;
        if (key.contains("red")) return 0d;
        if (key.contains("purple") || key.contains("plum")) return 290d;
        if (key.contains("pink")) return 330d;
        if (key.contains("gold") || key.contains("yellow") || key.contains("champagne")) return 46d;
        if (key.contains("orange") || key.contains("coral")) return 20d;
        if (key.contains("nude") || key.contains("beige")) return 30d;
        if (key.contains("green")) return 125d;
        if (key.contains("blue")) return 220d;
        if (key.contains("silver") || key.contains("gray") || key.contains("grey")) return 0d;
        if (key.contains("black")) return 0d;
        return 0d;
    }

    private double bucketToNumber(String bucket, String channel) {
        if (bucket.contains("low-medium")) return 0.40d;
        if (bucket.contains("medium-high")) return 0.70d;
        if (bucket.contains("high")) return 0.82d;
        if (bucket.contains("medium")) return 0.55d;
        if (bucket.contains("low")) return "s".equals(channel) ? 0.22d : 0.30d;
        return "s".equals(channel) ? 0.55d : 0.55d;
    }

    private double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private static class OptionIntentSeed {
        final String label;
        final String requestedFamily;
        final String baseColor;
        final String depth;
        final String hueFamily;
        final String saturationBucket;
        final String valueBucket;

        OptionIntentSeed(String label,
                         String requestedFamily,
                         String baseColor,
                         String depth,
                         String hueFamily,
                         String saturationBucket,
                         String valueBucket) {
            this.label = label;
            this.requestedFamily = requestedFamily;
            this.baseColor = baseColor;
            this.depth = depth;
            this.hueFamily = hueFamily;
            this.saturationBucket = saturationBucket;
            this.valueBucket = valueBucket;
        }
    }

    private String firstNames(List<Polish> polishes, int max) {
        if (polishes == null || polishes.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        int limit = Math.min(max, polishes.size());
        for (int i = 0; i < limit; i++) {
            Polish p = polishes.get(i);
            if (i > 0) sb.append(", ");
            sb.append(safe(p != null ? p.getShadeName() : ""));
        }
        sb.append("]");
        return sb.toString();
    }
}

