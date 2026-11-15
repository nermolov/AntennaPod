package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ShadowedRoundRectDrawable extends Drawable {
    private static final float BLUR_TO_SHADOW_RADIUS = 0.75f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final float cornerRadius;
    private final float shadowDx;
    private final float shadowDy;
    private final float shadowRadius;
    private final int shadowColor;
    private final int margin;
    private Bitmap cache;
    private int cachedWidth = -1;
    private int cachedHeight = -1;

    public ShadowedRoundRectDrawable(int color, float cornerRadius, float shadowDx, float shadowDy, float shadowBlur,
                                     int shadowColor) {
        this.cornerRadius = cornerRadius;
        this.shadowDx = shadowDx;
        this.shadowDy = shadowDy;
        this.shadowRadius = shadowBlur * BLUR_TO_SHADOW_RADIUS;
        this.shadowColor = shadowColor;
        this.margin = (int) Math.ceil(shadowRadius * 2 + Math.max(Math.abs(shadowDx), Math.abs(shadowDy)));
        paint.setColor(color);
    }

    private void renderCache(int width, int height) {
        if (cache != null) {
            cache.recycle();
            cache = null;
        }
        cachedWidth = width;
        cachedHeight = height;
        if (width <= 0 || height <= 0) {
            return;
        }
        cache = Bitmap.createBitmap(width + 2 * margin, height + 2 * margin, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(cache);
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0xFF000000);
        shadowPaint.setShadowLayer(shadowRadius, shadowDx, shadowDy, shadowColor);
        RectF rect = new RectF(margin, margin, margin + width, margin + height);
        float radius = Math.min(cornerRadius, Math.min(width, height) / 2f);
        canvas.drawRoundRect(rect, radius, radius, shadowPaint);
        Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        clear.setColor(0xFF000000);
        canvas.drawRoundRect(rect, radius, radius, clear);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (cache == null || cachedWidth != bounds.width() || cachedHeight != bounds.height()) {
            renderCache(bounds.width(), bounds.height());
        }
        if (cache != null) {
            canvas.drawBitmap(cache, bounds.left - margin, bounds.top - margin, bitmapPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        bitmapPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        bitmapPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
