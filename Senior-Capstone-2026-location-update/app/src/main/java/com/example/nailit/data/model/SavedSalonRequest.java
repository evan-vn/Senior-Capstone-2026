package com.example.nailit.data.model;

import com.google.gson.annotations.SerializedName;

public class SavedSalonRequest {

    @SerializedName("user_id")
    private String userId;

    @SerializedName("place_id")
    private String placeId;

    @SerializedName("salon_name")
    private String name;

    @SerializedName("address")
    private String address;

    @SerializedName("rating")
    private Double rating;

    public SavedSalonRequest(String userId, String placeId, String name, String address, Double rating) {
        this.userId = userId;
        this.placeId = placeId;
        this.name = name;
        this.address = address;
        this.rating = rating;
    }

    public String getUserId() {
        return userId;
    }

    public String getPlaceId() {
        return placeId;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public Double getRating() {
        return rating;
    }
}