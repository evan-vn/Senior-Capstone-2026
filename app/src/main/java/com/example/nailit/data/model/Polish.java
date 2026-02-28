package com.example.nailit.data.model;

import com.google.gson.annotations.SerializedName;

public class Polish {

    @SerializedName("uid")
    private String uid;

    @SerializedName("brand")
    private String brand;

    @SerializedName("collection")
    private String collection;

    @SerializedName("shade_name")
    private String shadeName;

    @SerializedName("shade_code")
    private int shadeCode;

    @SerializedName("description")
    private String description;

    @SerializedName("hex")
    private String hex;

    @SerializedName("favorite_count")
    private int favoriteCount;

    public String getUid() { return uid; }
    public String getBrand() { return brand; }
    public String getCollection() { return collection; }
    public String getShadeName() { return shadeName; }
    public int getShadeCode() { return shadeCode; }
    public String getDescription() { return description; }
    public String getHex() { return hex; }
    public int getFavoriteCount() { return favoriteCount; }

    public static Polish create(String uid, String shadeName, String brand,
                                String hex) {
        Polish p = new Polish();
        p.uid = uid;
        p.shadeName = shadeName;
        p.brand = brand;
        p.hex = hex;
        return p;
    }
}
