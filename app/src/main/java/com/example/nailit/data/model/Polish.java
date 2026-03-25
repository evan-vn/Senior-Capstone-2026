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

    @SerializedName("thumbnail_data")
    private String thumbnailHex;

    @SerializedName("season_labels")
    private List<String> seasonLabels;

    @SerializedName("occasion_labels")
    private List<String> occasionLabels;

    public String getUid() { return uid; }
    public String getBrand() { return brand; }
    public String getCollection() { return collection; }
    public String getShadeName() { return shadeName; }
    public int getShadeCode() { return shadeCode; }
    public String getDescription() { return description; }
    public String getHex() { return hex; }
    public int getFavoriteCount() { return favoriteCount; }
    public List<String> getSwatchImages() { return swatchImages; }
    public List<String> getSeasonLabels() { return seasonLabels; }
    public List<String> getOccasionLabels() { return occasionLabels; }

    //PostgREST sends bytea as hex string like \"\\\\xFFD8...\"; decode to bytes
    public byte[] getThumbnailBytes() {
        if (thumbnailHex == null || thumbnailHex.length() < 4) {
            return null;
        }
        String hex = thumbnailHex;
        if (hex.startsWith("\\\\x") || hex.startsWith("\\\\X")) {
            hex = hex.substring(2);
        }
        int len = hex.length();
        if (len % 2 != 0) {
            return null;
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                return null;
            }
            data[i / 2] = (byte) ((hi << 4) + lo);
        }
        return data;
    }

    //Returns the first swatch image URL, or null if none available
    public String getSwatchUrl() {
        if (swatchImages != null && !swatchImages.isEmpty()) {
            return swatchImages.get(0);
        }
        return null;
    }
    public String getThumbnailHex(){
        return thumbnailHex;
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
