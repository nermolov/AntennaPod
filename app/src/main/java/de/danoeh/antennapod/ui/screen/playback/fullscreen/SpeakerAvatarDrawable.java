package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SpeakerAvatarDrawable extends Drawable {
    private static final float DESIGN_DIAMETER = 80f;
    private static final float DESIGN_GLYPH_SIZE = 65f;
    private static final int SHADOW_COLOR = 0x401D1818;

    private static final String[] GLYPHS = {"~", "¤", "*", "×", "÷"};
    private static final int[] COLORS = {0xFF6B8440, 0xFF5D89B8, 0xFF5E8F86, 0xFF9B5230, 0xFF9653A7};
    private static final float[] GLYPH_DX = {0f, 0f, 0f, 0f, 0f};
    private static final float[] GLYPH_DY = {2f, 0f, 6.5f, 0f, 0f};

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ShadowedRoundRectDrawable circle;
    private final int index;

    public SpeakerAvatarDrawable(int speakerIndex, Typeface typeface, float density) {
        index = Math.floorMod(speakerIndex, GLYPHS.length);
        float diameter = DESIGN_DIAMETER * density;
        circle = new ShadowedRoundRectDrawable(COLORS[index], diameter / 2f, density, 4f * density, 3f * density,
                SHADOW_COLOR);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTypeface(typeface);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public static int colorFor(int speakerIndex) {
        return COLORS[Math.floorMod(speakerIndex, COLORS.length)];
    }

    public static int variantCount() {
        return GLYPHS.length;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        circle.setBounds(bounds);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        circle.draw(canvas);
        float unit = Math.min(bounds.width(), bounds.height()) / DESIGN_DIAMETER;
        textPaint.setTextSize(DESIGN_GLYPH_SIZE * unit);
        float x = bounds.exactCenterX() + GLYPH_DX[index] * unit;
        float baseline = bounds.exactCenterY() + GLYPH_DY[index] * unit
                - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.drawText(GLYPHS[index], x, baseline, textPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        circle.setAlpha(alpha);
        textPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        circle.setColorFilter(colorFilter);
        textPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
