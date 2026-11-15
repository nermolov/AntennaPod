package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

public class NoiseBackgroundDrawable extends Drawable {
    private static final int TILE_SIZE = 256;
    private static final float NOISE_OPACITY = 0.9f;
    private static final long SEED = 1L;

    private final Paint fillPaint = new Paint();
    private final Paint noisePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Bitmap tile;

    public NoiseBackgroundDrawable(int backgroundColor, int noiseColor) {
        fillPaint.setColor(backgroundColor);
        tile = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888);
        Random random = new Random(SEED);
        int[] pixels = new int[TILE_SIZE * TILE_SIZE];
        int red = Color.red(noiseColor);
        int green = Color.green(noiseColor);
        int blue = Color.blue(noiseColor);
        for (int i = 0; i < pixels.length; i++) {
            int alpha = Math.round(random.nextFloat() * NOISE_OPACITY * 255f);
            pixels[i] = Color.argb(alpha, red, green, blue);
        }
        tile.setPixels(pixels, 0, TILE_SIZE, 0, 0, TILE_SIZE, TILE_SIZE);
        noisePaint.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.drawRect(getBounds(), fillPaint);
        canvas.drawRect(getBounds(), noisePaint);
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
        noisePaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        noisePaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
