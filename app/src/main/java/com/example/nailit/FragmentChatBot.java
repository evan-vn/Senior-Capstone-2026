package com.example.nailit;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nailit.data.model.AiChatResult;
import com.example.nailit.data.model.AiMatchedOption;
import com.example.nailit.data.model.Polish;
import com.example.nailit.data.repo.AiRepository;
import com.example.nailit.data.repo.OutfitColorExtractor;
import com.example.nailit.ui.ChatPolishAdapter;

import java.util.ArrayList;
import java.util.List;

public class FragmentChatBot extends Fragment {

    private LinearLayout chatContainer;
    private EditText chatInput;
    private ImageButton sendBtn;
    private ScrollView scrollRoot;
    private View uploadButton;

    private AiRepository aiRepository;
    private TextView loadingMessageView;

    private OutfitColorExtractor.ColorSummary selectedColorSummary;
    private ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat_bot, container, false);

        scrollRoot = (ScrollView) view;
        chatContainer = view.findViewById(R.id.chatContainer);
        chatInput = view.findViewById(R.id.chatInput);
        sendBtn = view.findViewById(R.id.sendBtn);
        uploadButton = view.findViewById(R.id.uploadButton);

        aiRepository = new AiRepository(requireContext());

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (!isAdded()) return;

                    if (uri == null) {
                        Toast.makeText(requireContext(), "No image selected", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        selectedColorSummary = OutfitColorExtractor.extract(requireContext(), uri);
                        addImageBubble(uri);
                        List<String> previewFamilies = firstColorOnly(selectedColorSummary.getTopFamilies());
                        String colorPreview = prettyColors(previewFamilies);

                        addChatBubble("Outfit uploaded. Colors found: " + colorPreview, false);

                        if (TextUtils.isEmpty(chatInput.getText().toString().trim())) {
                            chatInput.setText("Match my outfit");
                        }

                    } catch (Exception e) {
                        selectedColorSummary = null;
                        Toast.makeText(requireContext(),
                                "Could not analyze image colors",
                                Toast.LENGTH_LONG).show();
                    }
                }
        );

        uploadButton.setOnClickListener(v ->
                pickImageLauncher.launch(
                        new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build()
                )
        );

        sendBtn.setOnClickListener(v -> handleSend());

        return view;
    }

    private void handleSend() {
        String userText = chatInput.getText().toString().trim();

        if (TextUtils.isEmpty(userText) && selectedColorSummary == null) {
            Toast.makeText(requireContext(),
                    "Type a style prompt or upload an outfit first",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String finalPrompt = buildPrompt(userText, selectedColorSummary);

        addChatBubble(TextUtils.isEmpty(userText) ? "Match my outfit" : userText, true);
        chatInput.setText("");
        setLoading(true);

        aiRepository.askStyleSuggestions(finalPrompt, new AiRepository.AiCallback() {
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

    private String buildPrompt(String userText, OutfitColorExtractor.ColorSummary summary) {
        String cleanUserText = userText == null ? "" : userText.trim();

        if (summary == null || summary.getTopFamilies() == null || summary.getTopFamilies().isEmpty()) {
            return cleanUserText;
        }

        List<String> topFamilies = firstColorOnly(summary.getTopFamilies());
        String dominantColor = prettyColor(topFamilies.get(0));
        String hexes = TextUtils.join(", ", summary.getTopHexes());

        StringBuilder sb = new StringBuilder();

        if (!TextUtils.isEmpty(cleanUserText)) {
            sb.append("User request: ").append(cleanUserText).append(". ");
        } else {
            sb.append("User request: match my outfit. ");
        }

        sb.append("The dominant outfit color is ").append(dominantColor).append(". ");

        if (!TextUtils.isEmpty(hexes)) {
            sb.append("Detected dominant hex colors: ").append(hexes).append(". ");
        }

        sb.append("Prioritize this dominant outfit color in the first recommendation. ");
        sb.append("Suggest nail polish colors that match this outfit color. ");
        sb.append("Return database-friendly color keywords.");

        return sb.toString();
    }

    private List<String> firstColorOnly(List<String> families) {
        List<String> result = new ArrayList<>();
        if (families == null || families.isEmpty()) return result;

        result.add(families.get(0));
        return result;
    }

    private String prettyColor(String family) {
        if (family == null) return "";

        switch (family) {
            case "GRAY_SILVER":
                return "silver gray";
            case "GOLD_BRONZE":
                return "gold bronze";
            case "ORANGE_CORAL":
                return "coral";
            default:
                return family.toLowerCase().replace("_", " ");
        }
    }

    private String prettyColors(List<String> families) {
        if (families == null || families.isEmpty()) return "";

        List<String> pretty = new ArrayList<>();

        for (String family : families) {
            if (family == null) continue;
            pretty.add(prettyColor(family));
        }

        return TextUtils.join(", ", pretty);
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
        recycler.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        );

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
        heading.setTypeface(heading.getTypeface(), Typeface.BOLD);

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

    private void addImageBubble(Uri imageUri) {
        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(requireContext());
        card.setRadius(dp(14));
        card.setCardElevation(dp(2));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                dp(180),
                dp(220)
        );
        cardParams.topMargin = dp(8);
        cardParams.gravity = Gravity.END;
        card.setLayoutParams(cardParams);

        android.widget.ImageView imageView = new android.widget.ImageView(requireContext());
        imageView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        imageView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        imageView.setImageURI(imageUri);

        card.addView(imageView);
        chatContainer.addView(card);
        scrollToBottom();
    }
}