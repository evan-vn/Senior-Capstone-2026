package com.example.nailit.ui;

import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;

import com.example.nailit.R;

/**
 * Applies a horizontal {@link LinearGradient} to a {@link TextView}'s paint.
 * Uses {@link TextView#post(Runnable)} so width is valid, and re-applies on size changes.
 */
public final class TextGradientHelper {

    private TextGradientHelper() {
    }

    /**
     * Same gradient as the header brand ({@code header_brand_gradient_start} → {@code header_brand_gradient_end}).
     */
    public static void applyHeaderBrandGradient(TextView tv) {
        Context c = tv.getContext();
        applyHorizontalGradient(
                tv,
                ContextCompat.getColor(c, R.color.header_brand_gradient_start),
                ContextCompat.getColor(c, R.color.header_brand_gradient_end)
        );
    }

    /**
     * Applies a left-to-right gradient to the text paint. Safe to call from onCreate after
     * setContentView; uses post() when width is still zero.
     */
    public static void applyHorizontalGradient(TextView tv, @ColorInt int startColor, @ColorInt int endColor) {
        if (tv == null) return;

        Runnable apply = () -> applyInternal(tv, startColor, endColor);

        tv.post(apply);

        tv.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int w = right - left;
            int oldW = oldRight - oldLeft;
            if (w != oldW && w > 0) {
                applyInternal(tv, startColor, endColor);
            }
        });
    }

    private static void applyInternal(TextView tv, int startColor, int endColor) {
        int w = tv.getWidth();
        if (w <= 0) return;

        LinearGradient shader = new LinearGradient(
                0f,
                0f,
                w,
                0f,
                startColor,
                endColor,
                Shader.TileMode.CLAMP
        );
        tv.getPaint().setShader(shader);
        tv.invalidate();
    }
}
