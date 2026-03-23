package com.example.nailit.data.model;

import com.google.gson.annotations.SerializedName;

public class SavedSalonResponse {

    @SerializedName("saved_salon_id")
    private int savedSalonId;

    @SerializedName("place_id")
    private String placeId;

    @SerializedName("salon_name")
    private String salonName;

    @SerializedName("address")
    private String address;

    @SerializedName("rating")
    private Double rating;

    public int getSavedSalonId() {
        return savedSalonId;
    }

    public String getPlaceId() {
        return placeId;
    }

    public String getSalonName() {
        return salonName;
    }

    public String getAddress() {
        return address;
    }

    public Double getRating() {
        return rating;
    }
}
