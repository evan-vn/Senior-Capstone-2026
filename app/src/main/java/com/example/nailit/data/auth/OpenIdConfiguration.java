package com.example.nailit.data.auth;

import com.google.gson.annotations.SerializedName;

public class OpenIdConfiguration {

    @SerializedName("issuer")
    private String issuer;

    @SerializedName("token_endpoint")
    private String tokenEndpoint;

    @SerializedName("authorization_endpoint")
    private String authorizationEndpoint;

    @SerializedName("userinfo_endpoint")
    private String userinfoEndpoint;

    @SerializedName("jwks_uri")
    private String jwksUri;

    @SerializedName("device_authorization_endpoint")
    private String deviceAuthorizationEndpoint;

    public String getIssuer() { return issuer; }
    public String getTokenEndpoint() { return tokenEndpoint; }
    public String getAuthorizationEndpoint() { return authorizationEndpoint; }
    public String getUserinfoEndpoint() { return userinfoEndpoint; }
    public String getJwksUri() { return jwksUri; }
    public String getDeviceAuthorizationEndpoint() { return deviceAuthorizationEndpoint; }
}
