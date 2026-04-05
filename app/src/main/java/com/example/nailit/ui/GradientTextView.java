package com.example.nailit.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
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

    private int startColor;
    private int endColor;

    @Nullable
    private LinearGradient gradient;

    public GradientTextView(Context context) {
        super(context);
        initColors(context);
    }

    public GradientTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initColors(context);
    }

    public GradientTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initColors(context);
    }

    private void initColors(Context context) {
        startColor = ContextCompat.getColor(context, R.color.header_brand_gradient_start);
        endColor = ContextCompat.getColor(context, R.color.header_brand_gradient_end);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildGradient(w);
    }

    private void rebuildGradient(int w) {
        if (w <= 0) {
            gradient = null;
            return;
        }
        gradient = new LinearGradient(
                0f,
                0f,
                w,
                0f,
                startColor,
                endColor,
                Shader.TileMode.CLAMP
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        //TextView may refresh TextPaint before drawing; set shader each frame.
        if (gradient != null) {
            getPaint().setShader(gradient);
        }
        super.onDraw(canvas);
    }
}
