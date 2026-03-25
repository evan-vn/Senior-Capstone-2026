package com.example.nailit.data.repo;

import com.example.nailit.data.model.Polish;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PolishColorClassifier {

    public static final String BLACK = "BLACK";
    public static final String WHITE = "WHITE";
    public static final String GRAY_SILVER = "GRAY_SILVER";
    public static final String GOLD_BRONZE = "GOLD_BRONZE";
    public static final String BURGUNDY = "BURGUNDY";
    public static final String RED = "RED";
    public static final String PURPLE = "PURPLE";
    public static final String PINK = "PINK";
    public static final String NUDE = "NUDE";
    public static final String BLUE = "BLUE";
    public static final String GREEN = "GREEN";
    public static final String ORANGE_CORAL = "ORANGE_CORAL";
    public static final String BROWN = "BROWN";
    public static final String MULTI_GLITTER = "MULTI_GLITTER";

    private final Map<String, List<String>> colorKeywords = buildColorKeywords();
    private final List<String> multiGlitterKeywords = Arrays.asList(
            "glitter", "confetti", "holographic", "holo", "multicolor", "rainbow", "iridescent"
    );

    public String classify(Polish polish) {
        String text = normalize(safe(polish.getDescription()) + " " + safe(polish.getShadeName()));
        return classifyFromTextAndHex(text, polish.getHex());
    }

    public String explainClassification(Polish polish) {
        String text = normalize(safe(polish.getDescription()) + " " + safe(polish.getShadeName()));
        String family = classifyFromTextAndHex(text, polish.getHex());
        StringBuilder hits = new StringBuilder();
        List<String> keywords = colorKeywords.get(family);
        if (keywords != null) {
            for (String keyword : keywords) {
                String k = normalize(keyword);
                if (k.contains(" ")) {
                    if (text.contains(k)) {
                        if (hits.length() > 0) hits.append(",");
                        hits.append(k);
                    }
                } else if (containsWholeWord(text, k)) {
                    if (hits.length() > 0) hits.append(",");
                    hits.append(k);
                }
            }
        }
        return "family=" + family + " keyword_hits=[" + hits + "] hex=" + normalizeHex(polish.getHex());
    }

    public String classifyOptionIntent(String label, List<String> colorKeywords) {
        StringBuilder sb = new StringBuilder(normalize(label));

        if (colorKeywords != null) {
            for (String keyword : colorKeywords) {
                sb.append(" ").append(normalize(keyword));
            }
        }

        String optionText = sb.toString();

        if (containsWholeWord(optionText, "burgundy")
                || containsWholeWord(optionText, "wine")
                || containsWholeWord(optionText, "oxblood")
                || containsWholeWord(optionText, "maroon")) {
            return BURGUNDY;
        }

        if (containsWholeWord(optionText, "pink")
                || containsWholeWord(optionText, "light pink")
                || containsWholeWord(optionText, "soft pink")
                || containsWholeWord(optionText, "baby pink")
                || containsWholeWord(optionText, "blush pink")
                || containsWholeWord(optionText, "rose")) {
            return PINK;
        }

        if (containsWholeWord(optionText, "blush")
                || containsWholeWord(optionText, "nude")
                || containsWholeWord(optionText, "beige")
                || containsWholeWord(optionText, "taupe")) {
            return NUDE;
        }

        if (containsWholeWord(optionText, "gold")
                || containsWholeWord(optionText, "bronze")
                || containsWholeWord(optionText, "champagne")) {
            return GOLD_BRONZE;
        }

        if (containsWholeWord(optionText, "silver")
                || containsWholeWord(optionText, "gray")
                || containsWholeWord(optionText, "grey")
                || containsWholeWord(optionText, "chrome")) {
            return GRAY_SILVER;
        }

        if (containsWholeWord(optionText, "orange")
                || containsWholeWord(optionText, "coral")
                || containsWholeWord(optionText, "peach")
                || containsWholeWord(optionText, "apricot")
                || containsWholeWord(optionText, "tangerine")) {
            return ORANGE_CORAL;
        }


        return classifyFromTextAndHex(optionText, null);
    }

    private String classifyFromTextAndHex(String text, String hex) {
        String normalized = normalize(text);
        Map<String, Integer> scores = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : colorKeywords.entrySet()) {
            int score = scoreKeywords(normalized, entry.getValue());
            scores.put(entry.getKey(), score);
        }

        String bestFamily = "";
        int bestScore = 0;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestFamily = entry.getKey();
                bestScore = entry.getValue();
            }
        }

        if (bestScore > 0) {
            return bestFamily;
        }

        if (containsAny(normalized, multiGlitterKeywords)) {
            return MULTI_GLITTER;
        }

        return classifyByHex(hex);
    }

    private int scoreKeywords(String text, List<String> keywords) {
        int score = 0;
        for (String keyword : keywords) {
            String key = normalize(keyword);
            if (key.contains(" ")) {
                if (text.contains(key)) score += 6;
                continue;
            }
            if (containsWholeWord(text, key)) score += 4;
        }
        return score;
    }

    private String classifyByHex(String hexValue) {
        String hex = normalizeHex(hexValue);
        if (hex.length() != 6) {
            return NUDE;
        }

        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);

            int lum = (int) (0.2126 * r + 0.7152 * g + 0.0722 * b);
            boolean neutral = Math.abs(r - g) < 18 && Math.abs(g - b) < 18;

            if (lum < 45) return BLACK;

            if (r >= 230 && g >= 230 && b >= 230) return WHITE;
            if (r >= 220 && g >= 220 && b >= 210) return WHITE;

            if (neutral && lum >= 165 && lum < 220) return GRAY_SILVER;
            if (neutral && lum >= 115 && lum < 165) return BROWN;

            // orange
            if (r >= 170 && g >= 85 && g <= 190 && b <= 125 && r > g && g > b) {
                return ORANGE_CORAL;
            }

            // red / burgundy
            if (r > g + 35 && r > b + 35) {
                if (r < 140 && b < 90) return BURGUNDY;
                if (g > 100 && b < 120) return ORANGE_CORAL;
                if (b > 95) return PURPLE;
                return RED;
            }

            // blue
            if (b > r + 30 && b > g + 20) {
                if (r > 145 && b > 145) return PURPLE;
                return BLUE;
            }

            // green
            if (g > r + 20 && g > b + 20) return GREEN;

            // white / nude / brown fallback
            if (lum > 220 && sApprox(r, g, b) < 0.12f) return WHITE;
            if (r > 185 && g > 165 && b > 145) return NUDE;
            if (neutral && lum >= 95 && lum < 160) return BROWN;

            return BROWN;

        } catch (Exception ignored) {
            return NUDE;
        }
    }

    private float sApprox(int r, int g, int b) {
        float max = Math.max(r, Math.max(g, b)) / 255f;
        float min = Math.min(r, Math.min(g, b)) / 255f;
        if (max == 0f) return 0f;
        return (max - min) / max;
    }

    private boolean containsWholeWord(String text, String keyword) {
        String padded = " " + text + " ";
        return padded.contains(" " + keyword + " ");
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            String key = normalize(keyword);
            if (key.contains(" ")) {
                if (text.contains(key)) return true;
            } else if (containsWholeWord(text, key)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeHex(String value) {
        if (value == null) return "";
        return value.trim().replace("#", "").toLowerCase(Locale.US);
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.trim()
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Map<String, List<String>> buildColorKeywords() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put(BLACK, asList("black", "noir", "ink", "jet", "ebony", "midnight", "obsidian", "raven", "charcoal"));
        map.put(WHITE, asList("white", "ivory", "snow", "pearl", "milky", "milk"));
        map.put(GRAY_SILVER, asList("silver", "chrome", "steel", "platinum", "gunmetal", "metallic gray", "metallic grey", "gray", "grey"));
        map.put(GOLD_BRONZE, asList("gold", "golden", "champagne", "bronze", "gilded", "amber", "rose gold", "antique gold", "warm metallic"));
        map.put(BURGUNDY, asList("burgundy", "wine", "oxblood", "maroon", "deep burgundy"));
        map.put(RED, asList("red", "wine", "burgundy", "cherry", "crimson", "ruby", "scarlet", "maroon"));
        map.put(PURPLE, asList("purple", "plum", "violet", "berry", "mauve", "orchid", "eggplant", "lavender"));
        map.put(PINK, asList("pink", "light pink", "soft pink", "baby pink", "blush pink", "rose", "bubblegum", "fuchsia", "magenta"));
        map.put(NUDE, asList("nude", "beige", "taupe", "peach nude", "blush", "neutral", "skin tone"));
        map.put(BLUE, asList("blue", "teal", "navy", "cobalt", "azure"));
        map.put(GREEN, asList("green", "emerald", "olive", "mint", "sage"));
        map.put(ORANGE_CORAL, asList("orange", "coral", "apricot", "tangerine", "peach"));
        map.put(BROWN, asList("brown", "chocolate", "mocha", "caramel", "cocoa"));
        return map;
    }

    private List<String> asList(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }
}