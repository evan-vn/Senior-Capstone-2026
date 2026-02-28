package com.example.nailit.data.auth;

public interface AuthProvider {

    interface AuthCallback {
        void onSuccess();
        void onError(String message);
    }

    //Step 1: initiate sign-in (send email link/code, or store credentials)
    void signInStart(String email, String passwordOrNull, AuthCallback callback);

    //Step 2: complete sign-in (verify code/link payload, or no-op if password flow)
    void signInComplete(String codeOrPayload, AuthCallback callback);
}
