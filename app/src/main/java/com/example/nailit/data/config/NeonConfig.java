package com.example.nailit.data.config;

public final class NeonConfig {

    private NeonConfig() {}

    public static final String REST_BASE_URL =
            "https://ep-quiet-mode-ait1x9ni.apirest.c-4.us-east-1.aws.neon.tech/neondb/rest/v1/";

    //Neon Auth (Better Auth) base — trailing slash required for Retrofit
    public static final String AUTH_BASE_URL =
            "https://ep-quiet-mode-ait1x9ni.neonauth.c-4.us-east-1.aws.neon.tech/neondb/auth/";
}
