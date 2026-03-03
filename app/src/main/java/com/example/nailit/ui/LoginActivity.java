package com.example.nailit.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.nailit.MainActivity;
import com.example.nailit.R;
import com.example.nailit.data.auth.AuthProvider;
import com.example.nailit.data.network.TokenStore;
import com.example.nailit.data.repo.AuthRepository;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail;
    private EditText etPassword;
    private Button btnSendCode;

    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnSendCode = findViewById(R.id.btn_send_code);

        TokenStore tokenStore = new TokenStore(this);
        authRepository = new AuthRepository(tokenStore);

        btnSendCode.setOnClickListener(v -> handleSendCode());
    }

    private void handleSendCode() {
        String email = etEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Email required", Toast.LENGTH_SHORT).show();
            return;
        }

        String password = etPassword.getText().toString().trim();
        if (TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Password required", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSendCode.setEnabled(false);
        btnSendCode.setText("Logging in…");

        authRepository.signInStart(email, password, new AuthProvider.AuthCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    btnSendCode.setEnabled(true);
                    Toast.makeText(LoginActivity.this, "Login successful",
                            Toast.LENGTH_SHORT).show();
                    navigateToMain();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnSendCode.setEnabled(true);
                            btnSendCode.setText("Login");
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
