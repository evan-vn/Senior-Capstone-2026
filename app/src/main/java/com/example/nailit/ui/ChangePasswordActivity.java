package com.example.nailit.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.nailit.R;
import com.example.nailit.data.auth.AuthProvider;
import com.example.nailit.data.network.TokenStore;
import com.example.nailit.data.repo.AuthRepository;

public class ChangePasswordActivity extends AppCompatActivity {

    private EditText etCurrentPassword;
    private EditText etNewPassword;
    private EditText etConfirmNewPassword;
    private Button btnChangePassword;

    private AuthRepository authRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        etCurrentPassword    = findViewById(R.id.et_current_password);
        etNewPassword        = findViewById(R.id.et_new_password);
        etConfirmNewPassword = findViewById(R.id.et_confirm_new_password);
        btnChangePassword    = findViewById(R.id.btn_change_password);
        ImageView btnBack    = findViewById(R.id.btnBackChangePassword);
        TextView tvCancel    = findViewById(R.id.tv_cancel);

        TokenStore tokenStore = new TokenStore(this);
        authRepository = new AuthRepository(tokenStore);

        btnChangePassword.setOnClickListener(v -> handleChangePassword());
        btnBack.setOnClickListener(v -> finish());
        tvCancel.setOnClickListener(v -> finish());

        if (!authRepository.hasToken()) {
            Toast.makeText(this, "Please sign in to change your password", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void handleChangePassword() {
        if (!authRepository.hasToken()) {
            Toast.makeText(this, "Please sign in to change your password", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String currentPassword = etCurrentPassword.getText().toString().trim();
        if (TextUtils.isEmpty(currentPassword)) {
            Toast.makeText(this, "Current password required", Toast.LENGTH_SHORT).show();
            return;
        }

        String newPassword = etNewPassword.getText().toString();
        if (TextUtils.isEmpty(newPassword)) {
            Toast.makeText(this, "New password required", Toast.LENGTH_SHORT).show();
            return;
        }

        String confirm = etConfirmNewPassword.getText().toString();
        if (TextUtils.isEmpty(confirm)) {
            Toast.makeText(this, "Confirm password required", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newPassword.length() < 8) {
            Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newPassword.equals(currentPassword)) {
            Toast.makeText(this, "New password must differ from current password", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPassword.equals(confirm)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        btnChangePassword.setEnabled(false);
        btnChangePassword.setText("Updating…");

        authRepository.changePassword(currentPassword, newPassword, new AuthProvider.AuthCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    btnChangePassword.setEnabled(true);
                    btnChangePassword.setText("Change Password");
                    Toast.makeText(ChangePasswordActivity.this,
                            "Password changed successfully", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnChangePassword.setEnabled(true);
                    btnChangePassword.setText("Change Password");
                    Toast.makeText(ChangePasswordActivity.this,
                            message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}