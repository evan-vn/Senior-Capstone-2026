package com.example.nailit.data.model;

import com.google.gson.annotations.SerializedName;

public class UserIdRow {

    @SerializedName("id")
    private String id;

    @SerializedName("email")
    private String email;

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }
}
