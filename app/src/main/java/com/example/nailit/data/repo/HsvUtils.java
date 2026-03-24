package com.example.nailit.data.repo;

import java.util.Locale;

public final class HsvUtils {

    private HsvUtils() {
    }

    public static HsvColor fromHex(String hexValue) {
        String hex = normalizeHex(hexValue);
        if (hex.length() != 6) return null;
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            float rf = r / 255f;
            float gf = g / 255f;
            float bf = b / 255f;

            float max = Math.max(rf, Math.max(gf, bf));
            float min = Math.min(rf, Math.min(gf, bf));
            float delta = max - min;

            float h;
            if (delta == 0f) {
                h = 0f;
            } else if (max == rf) {
                h = 60f * (((gf - bf) / delta) % 6f);
            } else if (max == gf) {
                h = 60f * (((bf - rf) / delta) + 2f);
            } else {
                h = 60f * (((rf - gf) / delta) + 4f);
            }
            if (h < 0f) h += 360f;

            float s = max == 0f ? 0f : (delta / max);
            float v = max;
            return new HsvColor(h, s, v);
        } catch (Exception ignored) {
            return null;
        }
    }

    //Returns normalized distance in [0..1], lower means closer.
    public static double hsvDistance(HsvColor target, HsvColor candidate, boolean hueWeak) {
        if (target == null || candidate == null) return 1d;
        double hueDiff = Math.abs(target.h - candidate.h);
        hueDiff = Math.min(hueDiff, 360d - hueDiff) / 180d;
        double satDiff = Math.abs(target.s - candidate.s);
        double valDiff = Math.abs(target.v - candidate.v);

        double hueWeight = hueWeak ? 0.10 : 0.45;
        double satWeight = hueWeak ? 0.45 : 0.30;
        double valWeight = hueWeak ? 0.45 : 0.25;
        return (hueDiff * hueWeight) + (satDiff * satWeight) + (valDiff * valWeight);
    }

    private static String normalizeHex(String value) {
        if (value == null) return "";
        return value.trim().replace("#", "").toLowerCase(Locale.US);
    }

    public static final class HsvColor {
        public final double h;
        public final double s;
        public final double v;

        public HsvColor(double h, double s, double v) {
            this.h = h;
            this.s = s;
            this.v = v;
        }
    }
}
