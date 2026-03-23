package com.example.nailit.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.nailit.R;
import com.example.nailit.data.auth.AuthProvider;
import com.example.nailit.data.network.TokenStore;
import com.example.nailit.data.repo.AuthRepository;

public class SignUpActivity extends AppCompatActivity {

    private EditText etName;
    private EditText etEmail;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private Button btnSignUp;

    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        etName = findViewById(R.id.et_name);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        btnSignUp = findViewById(R.id.btn_sign_up);
        TextView tvGoToLogin = findViewById(R.id.tv_go_to_login);

        TokenStore tokenStore = new TokenStore(this);
        authRepository = new AuthRepository(tokenStore);

        btnSignUp.setOnClickListener(v -> handleSignUp());
        tvGoToLogin.setOnClickListener(v -> goToLogin());
    }

    private void handleSignUp() {
        String name = etName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show();
            return;
        }

        String email = etEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Email required", Toast.LENGTH_SHORT).show();
            return;
        }

        String password = etPassword.getText().toString();
        if (TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Password required", Toast.LENGTH_SHORT).show();
            return;
        }

        String confirm = etConfirmPassword.getText().toString();
        if (!password.equals(confirm)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSignUp.setEnabled(false);
        btnSignUp.setText("Signing up…");

        authRepository.signUp(email, password, name, new AuthProvider.AuthCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    btnSignUp.setEnabled(true);
                    authRepository.logout();
                    Toast.makeText(SignUpActivity.this, "Account created. Please log in.",
                            Toast.LENGTH_SHORT).show();
                    goToLogin();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnSignUp.setEnabled(true);
                    btnSignUp.setText("Sign up");
                    Toast.makeText(SignUpActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
