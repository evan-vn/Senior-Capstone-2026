package com.example.nailit.data.model;

import com.google.gson.annotations.SerializedName;

public class NailDesign {

    @SerializedName("id")
    private long id;

    @SerializedName("image_url")
    private String imageUrl;

    @SerializedName("created_at")
    private String createdAt;

    private boolean isFavorited;

    public long getId() {
        return id;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public boolean isFavorited() {
        return isFavorited;
    }

    public void setFavorited(boolean favorited) {
        isFavorited = favorited;
    }
}

