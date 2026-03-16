package com.example.nailit.data.model;

import com.google.gson.annotations.SerializedName;

public class DesignFavoriteRow {

    @SerializedName("design_id")
    private long designId;

    public long getDesignId() {
        return designId;
    }
}

