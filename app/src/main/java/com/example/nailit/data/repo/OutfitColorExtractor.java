package com.example.nailit.data.repo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
public final class OutfitColorExtractor {

    private OutfitColorExtractor() {
    }

    public static ColorSummary extract(Context context, Uri imageUri) throws IOException {
        Bitmap original = MediaStore.Images.Media.getBitmap(
                context.getContentResolver(),
                imageUri
        );

        Bitmap scaled = Bitmap.createScaledBitmap(original, 140, 140, true);

        Map<String, Integer> familyCount = new LinkedHashMap<>();
        Map<String, Integer> hexCount = new LinkedHashMap<>();

        int width = scaled.getWidth();
        int height = scaled.getHeight();

        // tighter center crop for clothing area
        int startX = (int) (width * 0.32f);
        int endX   = (int) (width * 0.68f);
        int startY = (int) (height * 0.24f);
        int endY   = (int) (height * 0.88f);

        int darkNeutralVotes = 0;
        int strongColorVotes = 0;
        int validPixels = 0;

        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                int pixel = scaled.getPixel(x, y);

                if (Color.alpha(pixel) < 180) continue;

                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                float[] hsv = new float[3];
                Color.RGBToHSV(r, g, b, hsv);

                float h = hsv[0];
                float s = hsv[1];
                float v = hsv[2];

                // skip only extreme unusable dark pixels
                if (v < 0.04f) continue;

                // keep white pixels
                boolean likelyWhite = (v >= 0.80f && s <= 0.20f)
                        || (r >= 205 && g >= 205 && b >= 205);

                // skip skin tones, but do not skip likely whites
                if (!likelyWhite && isLikelySkinTone(r, g, b, h, s, v)) continue;

                validPixels++;

                // truly dark neutral votes only
                if (v < 0.20f && s < 0.18f) {
                    darkNeutralVotes++;
                }

                // strong color votes
                if ((s > 0.30f && v > 0.20f) || (h >= 12f && h < 70f && v > 0.30f)) {
                    strongColorVotes++;
                }

                String family = classifyColorFamily(h, s, v, r, g, b);

                // black override only when truly very dark neutral
                if (v < 0.12f && s < 0.16f) {
                    family = PolishColorClassifier.BLACK;
                }

                // suppress weak neutrals, but do not suppress white
                if ((PolishColorClassifier.GRAY_SILVER.equals(family)
                        || PolishColorClassifier.NUDE.equals(family))
                        && s < 0.10f && v < 0.78f) {
                    continue;
                }

                String hex = toHex(r, g, b);
                int weight = weightPixel(family, s, v);

                // extra boost only for very dark true black
                if (PolishColorClassifier.BLACK.equals(family) && v < 0.10f) {
                    weight += 2;
                }

                familyCount.put(family, familyCount.getOrDefault(family, 0) + weight);
                hexCount.put(hex, hexCount.getOrDefault(hex, 0) + weight);
            }
        }

        List<String> topFamilies = topKeys(familyCount, 1);
        List<String> topHexes = topKeys(hexCount, 3);

        float darkNeutralRatio = validPixels > 0
                ? (float) darkNeutralVotes / (float) validPixels
                : 0f;

        float strongColorRatio = validPixels > 0
                ? (float) strongColorVotes / (float) validPixels
                : 0f;

        // stricter black override so orange does not become black too easily
        if (darkNeutralRatio >= 0.58f && strongColorRatio < 0.10f) {
            topFamilies.clear();
            topFamilies.add(PolishColorClassifier.BLACK);
        }

        if (topFamilies.isEmpty()) {
            topFamilies.add(PolishColorClassifier.NUDE);
        }

        return new ColorSummary(topFamilies, topHexes);
    }

    private static boolean isLikelySkinTone(int r, int g, int b, float h, float s, float v) {
        boolean rgbSkin =
                r > 95 && g > 40 && b > 20 &&
                        r > g && r > b &&
                        (r - g) > 12;

        boolean hsvSkin =
                h >= 5 && h <= 25 &&
                        s >= 0.18f && s <= 0.60f &&
                        v >= 0.35f && v <= 0.95f;

        boolean beigeLike =
                r > 155 && g > 120 && b > 90 &&
                        r > g && g > b &&
                        s >= 0.12f && s < 0.42f &&
                        h >= 8 && h <= 25;

        return (rgbSkin && hsvSkin) || beigeLike;
    }

    private static String classifyColorFamily(float h, float s, float v, int r, int g, int b) {
        // black
        if (v < 0.10f) return PolishColorClassifier.BLACK;

        // white should win early
        if (v >= 0.84f && s <= 0.12f && (h < 250 || h >= 345)) {
            return PolishColorClassifier.WHITE;
        }
        // light pink / pink rescue before gray
        if (h >= 315 && h < 345 && v >= 0.65f && s >= 0.06f) {
            return PolishColorClassifier.PINK;
        }
        // RGB white / off-white / ivory
        if (r >= 205 && g >= 205 && b >= 205) return PolishColorClassifier.WHITE;
        if (r >= 220 && g >= 210 && b >= 195 && s <= 0.18f && (h < 250 || h >= 345)) {
            return PolishColorClassifier.WHITE;
        }



        // gray / silver only for mid-bright neutrals
        if (s < 0.10f && v >= 0.22f && v < 0.80f) return PolishColorClassifier.GRAY_SILVER;

        // gold / bronze / yellow-like warm tones
        if (h >= 32 && h < 70) {
            return PolishColorClassifier.GOLD_BRONZE;
        }

        // orange rescue by RGB first
        if (r >= 150 && g >= 70 && g <= 200 && b <= 135 && r > g && g > b) {
            if (v >= 0.25f) return PolishColorClassifier.ORANGE_CORAL;
        }

        // orange
        if (h >= 14 && h < 42) {
            if (v < 0.30f) return PolishColorClassifier.BROWN;
            return PolishColorClassifier.ORANGE_CORAL;
        }

        // brown / nude
        if (h >= 8 && h < 14) {
            if (v < 0.55f) return PolishColorClassifier.BROWN;
            return PolishColorClassifier.NUDE;
        }

        // red / burgundy
        if ((h >= 0 && h < 8) || (h >= 350 && h <= 360)) {
            if (v < 0.50f) return PolishColorClassifier.BURGUNDY;
            return PolishColorClassifier.RED;
        }

        if (h >= 70 && h < 165) return PolishColorClassifier.GREEN;
        if (h >= 165 && h < 255) return PolishColorClassifier.BLUE;

        if (h >= 255 && h < 345) {
            if (h >= 315 && v > 0.60f) return PolishColorClassifier.PINK;
            return PolishColorClassifier.PURPLE;
        }

        // RGB fallback
        if (r >= 215 && g >= 215 && b >= 215 && (h < 250 || h >= 345)) {
            return PolishColorClassifier.WHITE;
        }
        if (r > 180 && g > 145 && b < 120) return PolishColorClassifier.GOLD_BRONZE;
        if (r > 170 && g > 90 && b < 130 && r > g && g > b) return PolishColorClassifier.ORANGE_CORAL;
        if (r > 120 && g > 70 && b < 80) return PolishColorClassifier.BROWN;
        if (g > r && g > b) return PolishColorClassifier.GREEN;
        if (b > r && b > g && s > 0.15f) return PolishColorClassifier.BLUE;
        if (r > g && r > b) return PolishColorClassifier.RED;

        return PolishColorClassifier.NUDE;
    }

    private static int weightPixel(String family, float s, float v) {
        int weight = 1;

        if (s > 0.25f) weight += 2;
        if (s > 0.45f) weight += 2;
        if (s > 0.65f) weight += 2;

        if (PolishColorClassifier.BLACK.equals(family)) {
            weight += 3;
        }

        if (PolishColorClassifier.RED.equals(family)
                || PolishColorClassifier.BURGUNDY.equals(family)
                || PolishColorClassifier.ORANGE_CORAL.equals(family)
                || PolishColorClassifier.GOLD_BRONZE.equals(family)
                || PolishColorClassifier.GREEN.equals(family)
                || PolishColorClassifier.BLUE.equals(family)
                || PolishColorClassifier.PURPLE.equals(family)
                || PolishColorClassifier.PINK.equals(family)
                || PolishColorClassifier.BROWN.equals(family)) {
            weight += 4;
        }

        if (PolishColorClassifier.GRAY_SILVER.equals(family)
                || PolishColorClassifier.NUDE.equals(family)) {
            weight -= 1;
        }

        // boost white instead of penalizing it
        if (PolishColorClassifier.WHITE.equals(family)) {
            if (v >= 0.78f) weight += 4;
            if (s <= 0.20f) weight += 2;
        }

        return Math.max(weight, 1);
    }

    private static List<String> topKeys(Map<String, Integer> counts, int limit) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<String> result = new ArrayList<>();
        for (int i = 0; i < entries.size() && i < limit; i++) {
            result.add(entries.get(i).getKey());
        }
        return result;
    }

    private static String toHex(int r, int g, int b) {
        return String.format("#%02X%02X%02X", r, g, b);
    }

    public static final class ColorSummary {
        private final List<String> topFamilies;
        private final List<String> topHexes;

        public ColorSummary(List<String> topFamilies, List<String> topHexes) {
            this.topFamilies = topFamilies != null ? topFamilies : new ArrayList<>();
            this.topHexes = topHexes != null ? topHexes : new ArrayList<>();
        }

        @NonNull
        public List<String> getTopFamilies() {
            return topFamilies;
        }

        @NonNull
        public List<String> getTopHexes() {
            return topHexes;
        }
    }
}