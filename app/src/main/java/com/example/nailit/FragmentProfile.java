package com.example.nailit;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.nailit.data.network.TokenStore;
import com.example.nailit.data.repo.AuthRepository;
import com.example.nailit.ui.FavoriteDesignsActivity;
import com.example.nailit.ui.FavoritePolishesActivity;
import com.example.nailit.ui.LoginActivity;

public class FragmentProfile extends Fragment {

    private TextView profileName;
    private TextView profileEmail;
    private AuthRepository authRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        profileName = view.findViewById(R.id.profileName);
        profileEmail = view.findViewById(R.id.profileEmail);
        View logoutBtn = view.findViewById(R.id.logout);
        View savedColors = view.findViewById(R.id.savedColors);
        View savedDesigns = view.findViewById(R.id.savedDesigns);

        TokenStore tokenStore = new TokenStore(requireContext());
        authRepository = new AuthRepository(tokenStore);

        logoutBtn.setOnClickListener(v -> handleLogout());
        savedColors.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), FavoritePolishesActivity.class)));
        savedDesigns.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), FavoriteDesignsActivity.class)));
        loadProfile();
        return view;
    }

    private void loadProfile() {
        if (!authRepository.hasToken()) {
            profileName.setText(getString(R.string.guest_name));
            profileEmail.setText("");
            return;
        }
        authRepository.fetchCurrentUser(new AuthRepository.ProfileCallback() {
            @Override
            public void onSuccess(String name, String email) {
                if (getContext() == null) return;
                requireActivity().runOnUiThread(() -> {
                    profileName.setText(name.isEmpty() ? getString(R.string.guest_name) : name);
                    profileEmail.setText(email);
                });
            }

            @Override
            public void onError(String message) {
                if (getContext() == null) return;
                requireActivity().runOnUiThread(() -> {
                    profileName.setText(getString(R.string.guest_name));
                    profileEmail.setText("");
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void handleLogout() {
        authRepository.logout();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
