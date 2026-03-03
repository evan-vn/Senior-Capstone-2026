package com.example.nailit.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

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

    @SerializedName("swatch_images")
    private List<String> swatchImages;

    public String getUid() { return uid; }
    public String getBrand() { return brand; }
    public String getCollection() { return collection; }
    public String getShadeName() { return shadeName; }
    public int getShadeCode() { return shadeCode; }
    public String getDescription() { return description; }
    public String getHex() { return hex; }
    public int getFavoriteCount() { return favoriteCount; }
    public List<String> getSwatchImages() { return swatchImages; }

    //Returns the first swatch image URL, or null if none available
    public String getSwatchUrl() {
        if (swatchImages != null && !swatchImages.isEmpty()) {
            return swatchImages.get(0);
        }
        return null;
    }

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
