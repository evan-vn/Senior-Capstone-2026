package com.example.nailit;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nailit.data.model.AiChatResult;
import com.example.nailit.data.model.AiMatchedOption;
import com.example.nailit.data.model.Polish;
import com.example.nailit.data.repo.AiRepository;
import com.example.nailit.ui.ChatPolishAdapter;

import java.util.List;

public class FragmentChatBot extends Fragment {

    private LinearLayout chatContainer;
    private EditText chatInput;
    private ImageButton sendBtn;
    private ScrollView scrollRoot;
    private AiRepository aiRepository;
    private TextView loadingMessageView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat_bot, container, false);
        scrollRoot = (ScrollView) view;
        chatContainer = view.findViewById(R.id.chatContainer);
        chatInput = view.findViewById(R.id.chatInput);
        sendBtn = view.findViewById(R.id.sendBtn);

        aiRepository = new AiRepository(requireContext());
        aiRepository.debugFetchAiCandidates();

        sendBtn.setOnClickListener(v -> handleSend());
        return view;
    }

    private void handleSend() {
        String prompt = chatInput.getText().toString().trim();
        if (TextUtils.isEmpty(prompt)) {
            Toast.makeText(requireContext(), "Type a style prompt first", Toast.LENGTH_SHORT).show();
            return;
        }

        addChatBubble(prompt, true);
        chatInput.setText("");
        setLoading(true);

        aiRepository.askStyleSuggestions(prompt, new AiRepository.AiCallback() {
            @Override
            public void onSuccess(AiChatResult result) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    setLoading(false);
                    addChatBubble(result.getAssistantMessage(), false);
                    addGroupedRecommendations(result.getOptions());
                });
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                    addChatBubble("Sorry, I couldn't get suggestions right now.", false);
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        sendBtn.setEnabled(!loading);

        if (loading) {
            loadingMessageView = buildBubble("NailIt AI is typing...", false);
            loadingMessageView.setAlpha(0.75f);
            chatContainer.addView(loadingMessageView);
            scrollToBottom();
            return;
        }

        if (loadingMessageView != null) {
            chatContainer.removeView(loadingMessageView);
            loadingMessageView = null;
        }
    }

    private void addChatBubble(String text, boolean isUser) {
        chatContainer.addView(buildBubble(text, isUser));
        scrollToBottom();
    }

    private void addGroupedRecommendations(List<AiMatchedOption> options) {
        if (options == null || options.isEmpty()) {
            TextView empty = buildBubble("No matching polishes found.", false);
            empty.setAlpha(0.7f);
            chatContainer.addView(empty);
            scrollToBottom();
            return;
        }

        for (AiMatchedOption option : options) {
            addOptionHeading(option.getLabel());
            addPolishRecommendations(option.getMatches());
        }
        scrollToBottom();
    }

    private void addPolishRecommendations(List<Polish> polishes) {
        if (polishes == null || polishes.isEmpty()) {
            TextView empty = buildBubble("No matching polishes found for this option.", false);
            empty.setAlpha(0.7f);
            chatContainer.addView(empty);
            return;
        }

        RecyclerView recycler = new RecyclerView(requireContext());
        recycler.setLayoutManager(new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false));
        ChatPolishAdapter adapter = new ChatPolishAdapter();
        adapter.setItems(polishes);
        recycler.setAdapter(adapter);
        recycler.setNestedScrollingEnabled(false);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(8);
        recycler.setLayoutParams(params);

        chatContainer.addView(recycler);
    }

    private void addOptionHeading(String label) {
        TextView heading = new TextView(requireContext());
        heading.setText((label == null || label.trim().isEmpty()) ? "Suggested Option" : label);
        heading.setTextSize(13f);
        heading.setTextColor(Color.parseColor("#444444"));
        heading.setTypeface(heading.getTypeface(), android.graphics.Typeface.BOLD);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(10);
        params.bottomMargin = dp(6);
        params.gravity = Gravity.START;
        heading.setLayoutParams(params);
        chatContainer.addView(heading);
    }

    private TextView buildBubble(String text, boolean isUser) {
        TextView bubble = new TextView(requireContext());
        bubble.setText(text);
        bubble.setTextSize(14f);
        bubble.setTextColor(Color.parseColor("#222222"));
        bubble.setPadding(dp(12), dp(10), dp(12), dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(isUser ? Color.parseColor("#F8BBD0") : Color.parseColor("#FFFFFF"));
        bubble.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(8);
        params.gravity = isUser ? Gravity.END : Gravity.START;
        bubble.setLayoutParams(params);
        return bubble;
    }

    private int dp(int value) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return (int) (value * density);
    }

    private void scrollToBottom() {
        scrollRoot.post(() -> scrollRoot.fullScroll(View.FOCUS_DOWN));
    }
}
