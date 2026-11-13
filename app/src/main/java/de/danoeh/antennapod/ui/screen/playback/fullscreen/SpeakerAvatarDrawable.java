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
    private static final int[] PALETTE = {
        0xFF4F8EF7, 0xFFE8735A, 0xFF4CB782, 0xFFB57BEE,
        0xFFE5A93C, 0xFF3CBFC9, 0xFFE0629A, 0xFF8FB339,
    };

    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String label;

    public SpeakerAvatarDrawable(int speakerIndex) {
        circlePaint.setColor(PALETTE[Math.floorMod(speakerIndex, PALETTE.length)]);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        label = String.valueOf(speakerIndex + 1);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        float radius = Math.min(bounds.width(), bounds.height()) / 2f;
        canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, circlePaint);
        textPaint.setTextSize(radius * 1.0f);
        float baseline = bounds.exactCenterY() - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.drawText(label, bounds.exactCenterX(), baseline, textPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        circlePaint.setAlpha(alpha);
        textPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        circlePaint.setColorFilter(colorFilter);
        textPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
