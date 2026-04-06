package com.example.nailit.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import com.example.nailit.R;

/**
 * Header-style text that draws with a horizontal pink → purple gradient.
 * Shader width matches the view width so it scales with layout; subtitle TextViews stay unchanged.
 */
public class GradientTextView extends AppCompatTextView {

    public GradientTextView(Context context) {
        super(context);
    }

    public GradientTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public GradientTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (w > 0) {
            Paint paint = getPaint();
            Shader shader = new LinearGradient(
                    0, 0, w, 0,
                    new int[]{
                            0xFFFF7EB3,   // pink
                            0xFF8E54E9    // purple
                    },
                    null,
                    Shader.TileMode.CLAMP
            );
            paint.setShader(shader);
        }
    }
}
