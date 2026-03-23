package com.example.nailit.data.model;

import com.google.gson.annotations.SerializedName;

public class FavoriteRow {

    @SerializedName("polish_uid")
    private String polishUid;

    public String getPolishUid() {
        return polishUid;
    }
}
