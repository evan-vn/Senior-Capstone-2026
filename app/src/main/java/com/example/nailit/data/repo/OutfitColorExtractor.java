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

        // lower center crop to avoid face/skin for long dresses
        int startX = (int) (width * 0.40f);
        int endX   = (int) (width * 0.60f);
        int startY = (int) (height * 0.18f);
        int endY   = (int) (height * 0.82f);

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

                if (v < 0.04f) continue;

                boolean likelyWhite = (v >= 0.96f && s <= 0.06f)
                        || (r >= 245 && g >= 245 && b >= 245
                        && Math.abs(r - g) <= 10
                        && Math.abs(g - b) <= 10);

                if (!likelyWhite && isLikelySkinTone(r, g, b, h, s, v)) continue;

                boolean skinLikeWarm =
                        h >= 0f && h < 20f &&
                                s >= 0.18f && s <= 0.45f &&
                                v >= 0.45f && v <= 0.95f &&
                                r > g && g > b;

                if (skinLikeWarm && y < (int) (height * 0.58f)) {
                    continue;
                }

                validPixels++;

                if (v < 0.20f && s < 0.18f) {
                    darkNeutralVotes++;
                }

                if ((s > 0.30f && v > 0.20f) || (h >= 12f && h < 70f && v > 0.30f)) {
                    strongColorVotes++;
                }

                String family = classifyColorFamily(h, s, v, r, g, b);

                // yellow-only safeguard so nude/brown do not get promoted to gold bronze
                if (PolishColorClassifier.GOLD_BRONZE.equals(family)) {
                    boolean realGoldOrYellow =
                            (h >= 32f && h < 72f && s >= 0.12f && v >= 0.24f) ||
                                    (r >= 145 && g >= 115 && b < 150 && r > b && g > b);

                    if (!realGoldOrYellow) {
                        family = PolishColorClassifier.BROWN;
                    }
                }

                if (v < 0.06f && s < 0.08f) {
                    family = PolishColorClassifier.BLACK;
                }

                if ((PolishColorClassifier.GRAY_SILVER.equals(family)
                        || PolishColorClassifier.BROWN.equals(family))
                        && s < 0.10f && v < 0.78f) {
                    continue;
                }

                String hex = toHex(r, g, b);
                int weight = weightPixel(family, s, v);

            // center bonus: favor the dress center over side background
                float centerX = (startX + endX) / 2f;
                float halfWidth = Math.max((endX - startX) / 2f, 1f);
                float dx = Math.abs(x - centerX) / halfWidth;

                float centerFactor = 1.30f - 0.40f * Math.min(dx, 1f);
                int finalWeight = Math.max(1, Math.round(weight * centerFactor));

            // extra boost only for very dark true black
                if (PolishColorClassifier.BLACK.equals(family) && v < 0.10f) {
                    finalWeight += 2;
                }

                familyCount.put(family, familyCount.getOrDefault(family, 0) + finalWeight);
                hexCount.put(hex, hexCount.getOrDefault(hex, 0) + finalWeight);
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

// yellow/gold rescue at the final vote stage
        int brownScore = familyCount.getOrDefault(PolishColorClassifier.BROWN, 0);
        int goldScore = familyCount.getOrDefault(PolishColorClassifier.GOLD_BRONZE, 0);

        if (!topFamilies.isEmpty()
                && PolishColorClassifier.BROWN.equals(topFamilies.get(0))
                && strongColorRatio >= 0.06f
                && goldScore > 0
                && goldScore >= brownScore * 0.30f) {
            topFamilies.clear();
            topFamilies.add(PolishColorClassifier.GOLD_BRONZE);
        }

        return new ColorSummary(topFamilies, topHexes);
    }

    private static boolean isLikelySkinTone(int r, int g, int b, float h, float s, float v) {
        boolean rgbSkin =
                r > 95 && g > 40 && b > 20 &&
                        r > g && r > b &&
                        (r - g) > 12;

        boolean hsvSkin =
                h >= 5f && h <= 25f &&
                        s >= 0.20f && s <= 0.62f &&
                        v >= 0.35f && v <= 0.95f;

        boolean likelyNeutralFabric =
                (v >= 0.62f && s <= 0.26f) ||
                        (r >= 175 && g >= 155 && b >= 130 && r >= g && g >= b);

        boolean likelyGreenFabric =
                h >= 70f && h < 190f && s >= 0.12f;

        if (likelyNeutralFabric || likelyGreenFabric) return false;

        return rgbSkin && hsvSkin;
    }

    private static String classifyColorFamily(float h, float s, float v, int r, int g, int b) {
        // black
        if (v < 0.08f && s < 0.18f) {
            return PolishColorClassifier.BLACK;
        }

        // pastel rescue before white
        if (h >= 315f && h < 345f && v >= 0.70f && s >= 0.10f) {
            return PolishColorClassifier.PINK;
        }

        if (h >= 255f && h < 315f && v >= 0.70f && s >= 0.10f) {
            return PolishColorClassifier.LAVENDER;
        }

        if (h >= 200f && h < 255f && v >= 0.45f && s >= 0.10f) {
            return PolishColorClassifier.BLUE;
        }

        // neutral block: white / cream / brown-like nude
        if (v >= 0.84f && s <= 0.08f) {
            return PolishColorClassifier.WHITE;
        }

        if (r >= 225 && g >= 225 && b >= 220
                && Math.abs(r - g) <= 12
                && Math.abs(g - b) <= 12
                && s <= 0.10f) {
            return PolishColorClassifier.WHITE;
        }

        // white rescue before cream
        if (v >= 0.74f && s <= 0.14f
                && r >= 195 && g >= 195 && b >= 190
                && Math.abs(r - g) <= 30
                && Math.abs(g - b) <= 30) {
            return PolishColorClassifier.WHITE;
        }

        if (v >= 0.68f && v < 0.78f && s >= 0.05f && s <= 0.18f && h >= 18f && h < 40f) {
            return PolishColorClassifier.CREAM;
        }

        // nude / beige / champagne
        if (v >= 0.58f && s <= 0.22f && h >= 10f && h < 30f) {
            if (v >= 0.82f && s <= 0.12f) {
                return PolishColorClassifier.WHITE;
            }
            if (v >= 0.72f && s <= 0.18f) {
                return PolishColorClassifier.CREAM;
            }
            return PolishColorClassifier.NUDE;
        }

        if (r >= 175 && g >= 150 && b >= 125 && r >= g && g >= b && s <= 0.24f) {
            if (r >= 225 && g >= 220 && b >= 210) {
                return PolishColorClassifier.WHITE;
            }
            if (r >= 205 && g >= 190 && b >= 170) {
                return PolishColorClassifier.CREAM;
            }
            return PolishColorClassifier.NUDE;
        }

        if (s < 0.08f && v >= 0.22f && v < 0.72f) {
            return PolishColorClassifier.GRAY_SILVER;
        }

        // dark block: black / dark green / dark blue / dark brown
        if (v < 0.18f && s < 0.20f) {
            return PolishColorClassifier.BLACK;
        }
        if (v < 0.22f && s < 0.22f) {
            return PolishColorClassifier.BLACK;
        }

        if (g > r + 10 && g > b + 10 && s > 0.14f) {
            return PolishColorClassifier.GREEN;
        }


        if (b > r + 30 && b > g + 30 && s > 0.28f && v > 0.26f) {
            return PolishColorClassifier.BLUE;
        }

        // white
        if (v >= 0.86f && s <= 0.08f) {
            return PolishColorClassifier.WHITE;
        }
        if (r >= 230 && g >= 230 && b >= 225 && s <= 0.10f
                && Math.abs(r - g) <= 12
                && Math.abs(g - b) <= 12) {
            return PolishColorClassifier.WHITE;
        }


        // gray / silver
        if (s < 0.08f && v >= 0.22f && v < 0.72f) {
            return PolishColorClassifier.GRAY_SILVER;
        }

        // mint
        if (h >= 92f && h < 140f && v >= 0.72f && s >= 0.12f && s <= 0.38f) {
            return PolishColorClassifier.MINT;
        }

        // green
        if (h >= 70f && h < 165f && s >= 0.16f) {
            return PolishColorClassifier.GREEN;
        }

        if (v < 0.28f && s < 0.26f) {
            return PolishColorClassifier.BLACK;
        }

        // teal
        if (h >= 165f && h < 200f && s >= 0.18f && v >= 0.16f) {
            return PolishColorClassifier.TEAL;
        }

        // lavender
        if (h >= 240f && h < 285f && v >= 0.62f && s >= 0.08f && s <= 0.45f) {
            return PolishColorClassifier.LAVENDER;
        }

        // blue
        if (h >= 200f && h < 255f) {
            if (v < 0.22f || s < 0.22f) {
                return PolishColorClassifier.BLACK;
            }
            return PolishColorClassifier.BLUE;
        }


        if (h >= 255f && h < 315f) {
            return PolishColorClassifier.PURPLE;
        }

        // pink
        if (h >= 315f && h < 345f && v >= 0.60f && s >= 0.14f) {
            return PolishColorClassifier.PINK;
        }

        // dark green / olive rescue before warm-color rules
        if (g >= r + 8 && g > b + 8 && h >= 55f && h < 165f && s >= 0.18f) {
            return PolishColorClassifier.GREEN;
        }


        if (r > g + 10 && g > b + 4 && h >= 8f && h < 24f && v < 0.55f) {
            return PolishColorClassifier.BROWN;
        }

        // red / burgundy
        if (((h >= 0 && h < 8) || (h >= 350 && h <= 360)) && s >= 0.38f) {
            if (v < 0.50f) return PolishColorClassifier.BURGUNDY;
            return PolishColorClassifier.RED;
        }

        // gold / bronze / yellow-like warm tones
        if (h >= 42f && h < 65f && s >= 0.38f && v >= 0.55f) {
            return PolishColorClassifier.GOLD_BRONZE;
        }
        if (r >= 190 && g >= 160 && b < 115 && s >= 0.35f && v >= 0.50f) {
            return PolishColorClassifier.GOLD_BRONZE;
        }

        if (r > g + 22 && g > b + 8 && h < 28f && r > 120 && g > 70 && b < 80 && s >= 0.20f) {
            return PolishColorClassifier.BROWN;
        }

        // orange / coral
        if (h >= 18f && h < 42f) {
            if (v < 0.58f) return PolishColorClassifier.BROWN;
            if (s < 0.34f) return PolishColorClassifier.BROWN;
            return PolishColorClassifier.ORANGE_CORAL;
        }



        if (r >= 150 && g >= 70 && g <= 200 && b <= 135 && r > g && g > b) {
            if (v >= 0.56f && s <= 0.34f) {
                if (v >= 0.82f && s <= 0.12f) return PolishColorClassifier.WHITE;
                if (v >= 0.70f && s <= 0.18f) return PolishColorClassifier.CREAM;
                return PolishColorClassifier.NUDE;
            }

            if (s >= 0.42f && h >= 14f && h < 28f && v >= 0.45f) {
                return PolishColorClassifier.ORANGE_CORAL;
            }
        }

        // brown (includes nude-like tones)
        if (h >= 8f && h < 14f) {
            return PolishColorClassifier.BROWN;
        }
        if (r > g && g > b && v < 0.52f && h >= 8f && h < 28f) {
            return PolishColorClassifier.BROWN;
        }


        if (r > 90 && g > 55 && b < 55 && r > g && g > b && h < 24f && v < 0.50f) {
            return PolishColorClassifier.BROWN;
        }

        if (r > 190 && g > 165 && b > 140 && s >= 0.08f && s <= 0.28f) {
            if (r >= 225 && g >= 220 && b >= 210) return PolishColorClassifier.WHITE;
            if (r >= 205 && g >= 190 && b >= 170) return PolishColorClassifier.CREAM;
            return PolishColorClassifier.NUDE;
        }


        // RGB fallbacks
        if (r > g + 12 && g > b + 6 && r > 120 && g > 70 && b < 80) {
            return PolishColorClassifier.BROWN;
        }
        if (g > r && g > b) return PolishColorClassifier.GREEN;

        if (b > r + 30 && b > g + 30 && s > 0.32f && v > 0.30f) {
            return PolishColorClassifier.BLUE;
        }

        if (r > g + 24 && r > b + 24 && s >= 0.32f && h < 12f) {
            return PolishColorClassifier.RED;
        }
        if (v >= 0.84f && s <= 0.14f) {
            return PolishColorClassifier.WHITE;
        }

        if (v >= 0.68f && v < 0.78f && s <= 0.18f && h >= 18f && h < 40f) {
            return PolishColorClassifier.CREAM;
        }
        if (v >= 0.58f && s <= 0.26f && h >= 10f && h < 35f) {
            return PolishColorClassifier.NUDE;
        }

        if (s < 0.10f && v >= 0.22f && v < 0.72f) {
            return PolishColorClassifier.GRAY_SILVER;
        }

        return PolishColorClassifier.BROWN;
    }

    private static int weightPixel(String family, float s, float v) {
        int weight = 1;

        if (s > 0.25f) weight += 2;
        if (s > 0.45f) weight += 2;
        if (s > 0.65f) weight += 2;

        if (PolishColorClassifier.BLACK.equals(family)) {
            weight += 6;
        }

        if (PolishColorClassifier.RED.equals(family)
                || PolishColorClassifier.BURGUNDY.equals(family)
                || PolishColorClassifier.ORANGE_CORAL.equals(family)
                || PolishColorClassifier.GREEN.equals(family)
                || PolishColorClassifier.PURPLE.equals(family)
                || PolishColorClassifier.PINK.equals(family)) {
            weight += 4;
        }

        if (PolishColorClassifier.GOLD_BRONZE.equals(family)) {
            weight += 4;
        }

        if (PolishColorClassifier.BROWN.equals(family)) {
            weight += 2;
        }

        if (PolishColorClassifier.BLUE.equals(family)) {
            weight += 2;
        }


        if (PolishColorClassifier.GRAY_SILVER.equals(family)) {
            weight -= 1;
        }

        if (PolishColorClassifier.NUDE.equals(family)
                || PolishColorClassifier.CREAM.equals(family)) {
            weight += 3;
        }

        if (PolishColorClassifier.WHITE.equals(family)) {
            if (v >= 0.97f) weight += 2;
            if (s <= 0.05f) weight += 1;
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