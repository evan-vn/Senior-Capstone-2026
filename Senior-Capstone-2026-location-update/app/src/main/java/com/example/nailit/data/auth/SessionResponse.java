package com.example.nailit.data.auth;

import com.google.gson.annotations.SerializedName;

public class SessionResponse {

    @SerializedName("user")
    private User user;

    public User getUser() {
        return user;
    }

    public static class User {
        @SerializedName("name")
        private String name;

        @SerializedName("email")
        private String email;

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }
    }
}
