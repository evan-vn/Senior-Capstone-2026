package com.example.nailit.data.repo;

import androidx.annotation.NonNull;

import com.example.nailit.data.api.DesignPolishesApi;
import com.example.nailit.data.api.NailDesignsApi;
import com.example.nailit.data.model.DesignPolishRow;
import com.example.nailit.data.model.NailDesign;
import com.example.nailit.data.network.ApiClient;
import com.example.nailit.data.network.RetrofitUtil;
import com.example.nailit.data.network.TokenStore;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class DesignsRepository {

    private static final String SELECT_DESIGN_POLISH = "design_id";
    private static final String SELECT_NAIL_DESIGNS = "id,image_url,created_at";

    private final DesignPolishesApi designPolishesApi;
    private final NailDesignsApi nailDesignsApi;

    public interface DesignsCallback {
        void onSuccess(List<NailDesign> designs);
        void onError(String message);
    }

    public DesignsRepository(TokenStore tokenStore) {
        Retrofit retrofit = ApiClient.getInstance(tokenStore);
        this.designPolishesApi = retrofit.create(DesignPolishesApi.class);
        this.nailDesignsApi = retrofit.create(NailDesignsApi.class);
    }

    public void getDesignsForPolish(String polishUid, DesignsCallback callback) {
        String polishEq = "eq." + polishUid;
        designPolishesApi.getDesignIdsForPolish(SELECT_DESIGN_POLISH, polishEq)
                .enqueue(new Callback<List<DesignPolishRow>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<DesignPolishRow>> call,
                                           @NonNull Response<List<DesignPolishRow>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            callback.onError(RetrofitUtil.extractError("Design lookup", response));
                            return;
                        }

                        List<Long> ids = new ArrayList<>();
                        for (DesignPolishRow row : response.body()) {
                            ids.add(row.getDesignId());
                        }

                        if (ids.isEmpty()) {
                            callback.onSuccess(new ArrayList<>());
                            return;
                        }

                        String idIn = buildInFilter(ids);
                        nailDesignsApi.getDesignsByIds(SELECT_NAIL_DESIGNS, idIn)
                                .enqueue(new Callback<List<NailDesign>>() {
                                    @Override
                                    public void onResponse(@NonNull Call<List<NailDesign>> call,
                                                           @NonNull Response<List<NailDesign>> response) {
                                        if (!response.isSuccessful() || response.body() == null) {
                                            callback.onError(RetrofitUtil.extractError("Designs", response));
                                            return;
                                        }
                                        callback.onSuccess(response.body());
                                    }

                                    @Override
                                    public void onFailure(@NonNull Call<List<NailDesign>> call,
                                                          @NonNull Throwable t) {
                                        callback.onError("Designs network error: " + t.getMessage());
                                    }
                                });
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<DesignPolishRow>> call,
                                          @NonNull Throwable t) {
                        callback.onError("Design lookup network error: " + t.getMessage());
                    }
                });
    }

    public void getDesignsByIds(java.util.Collection<Long> ids, DesignsCallback callback) {
        if (ids == null || ids.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }
        List<Long> idList = new ArrayList<>(ids);
        String idIn = buildInFilter(idList);
        nailDesignsApi.getDesignsByIds(SELECT_NAIL_DESIGNS, idIn)
                .enqueue(new Callback<List<NailDesign>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<NailDesign>> call,
                                           @NonNull Response<List<NailDesign>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            callback.onError(RetrofitUtil.extractError("Designs", response));
                            return;
                        }
                        callback.onSuccess(response.body());
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<NailDesign>> call,
                                          @NonNull Throwable t) {
                        callback.onError("Designs network error: " + t.getMessage());
                    }
                });
    }

    private String buildInFilter(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        sb.append("in.(");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(ids.get(i));
        }
        sb.append(")");
        return sb.toString();
    }
}

