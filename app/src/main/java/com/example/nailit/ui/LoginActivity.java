package com.example.nailit.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.nailit.BuildConfig;
import com.example.nailit.MainActivity;
import com.example.nailit.R;
import com.example.nailit.data.network.TokenStore;
import com.example.nailit.data.repo.AuthRepository;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail;
    private EditText etPassword;
    private EditText etCode;
    private Button btnSendCode;
    private Button btnVerify;
    private LinearLayout passwordRow;
    private LinearLayout codeRow;

    private AuthRepository authRepository;
    private boolean isPasswordMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etCode = findViewById(R.id.et_code);
        btnSendCode = findViewById(R.id.btn_send_code);
        btnVerify = findViewById(R.id.btn_verify);
        passwordRow = findViewById(R.id.password_row);
        codeRow = findViewById(R.id.code_row);

        TokenStore tokenStore = new TokenStore(this);
        authRepository = new AuthRepository(tokenStore);

        isPasswordMode = "password".equals(BuildConfig.NEON_AUTH_MODE);
        configureUiForMode();

        btnSendCode.setOnClickListener(v -> handleSendCode());
        btnVerify.setOnClickListener(v -> handleVerify());

        //Attempt OpenID discovery in the background on launch
        authRepository.fetchOpenIdConfig(config -> {});
    }

    private void configureUiForMode() {
        if (isPasswordMode) {
            passwordRow.setVisibility(View.VISIBLE);
            codeRow.setVisibility(View.GONE);
            btnSendCode.setText("Login");
            btnVerify.setVisibility(View.GONE);
        } else {
            passwordRow.setVisibility(View.GONE);
            codeRow.setVisibility(View.GONE);
            btnSendCode.setText("Send Sign-in Code");
            btnVerify.setVisibility(View.GONE);
        }
    }

    private void handleSendCode() {
        String email = etEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Email required", Toast.LENGTH_SHORT).show();
            return;
        }

        String password = isPasswordMode ? etPassword.getText().toString().trim() : null;
        if (isPasswordMode && TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Password required", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSendCode.setEnabled(false);
        btnSendCode.setText(isPasswordMode ? "Logging in…" : "Sending…");

        authRepository.signInStart(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    btnSendCode.setEnabled(true);

                    if (isPasswordMode) {
                        //Password flow completes in one step
                        Toast.makeText(LoginActivity.this, "Login successful",
                                Toast.LENGTH_SHORT).show();
                        navigateToMain();
                    } else {
                        //Passwordless: show code entry field
                        Toast.makeText(LoginActivity.this,
                                "Check your email for a sign-in code",
                                Toast.LENGTH_LONG).show();
                        btnSendCode.setText("Resend Code");
                        codeRow.setVisibility(View.VISIBLE);
                        btnVerify.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnSendCode.setEnabled(true);
                    btnSendCode.setText(isPasswordMode ? "Login" : "Send Sign-in Code");
                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void handleVerify() {
        String code = etCode.getText().toString().trim();
        if (TextUtils.isEmpty(code)) {
            Toast.makeText(this, "Enter the code from your email", Toast.LENGTH_SHORT).show();
            return;
        }

        btnVerify.setEnabled(false);
        btnVerify.setText("Verifying…");

        authRepository.signInComplete(code, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(LoginActivity.this, "Login successful",
                            Toast.LENGTH_SHORT).show();
                    navigateToMain();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnVerify.setEnabled(true);
                    btnVerify.setText("Verify");
                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void navigateToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
