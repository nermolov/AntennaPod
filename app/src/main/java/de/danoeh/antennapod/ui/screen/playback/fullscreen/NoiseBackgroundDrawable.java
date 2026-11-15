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
    private static final float NOISE_OPACITY = 1f;
    private static final long SEED = 1L;
    private static final float BLUR_SIGMA_PER_DENSITY = 0.75f;

    private final Paint fillPaint = new Paint();
    private final Paint noisePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Bitmap tile;

    public NoiseBackgroundDrawable(int backgroundColor, int noiseColor, float density) {
        fillPaint.setColor(backgroundColor);
        tile = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888);
        Random random = new Random(SEED);
        float[] alphas = new float[TILE_SIZE * TILE_SIZE];
        for (int i = 0; i < alphas.length; i++) {
            alphas[i] = random.nextFloat();
        }
        blur(alphas, Math.max(0.75f, density * BLUR_SIGMA_PER_DENSITY));
        int[] pixels = new int[alphas.length];
        int red = Color.red(noiseColor);
        int green = Color.green(noiseColor);
        int blue = Color.blue(noiseColor);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = Color.argb(Math.round(alphas[i] * NOISE_OPACITY * 255f), red, green, blue);
        }
        tile.setPixels(pixels, 0, TILE_SIZE, 0, 0, TILE_SIZE, TILE_SIZE);
        noisePaint.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    private static void blur(float[] values, float sigma) {
        int radius = (int) Math.ceil(sigma * 3f);
        float[] kernel = new float[radius * 2 + 1];
        float sum = 0f;
        for (int i = -radius; i <= radius; i++) {
            kernel[i + radius] = (float) Math.exp(-(i * i) / (2f * sigma * sigma));
            sum += kernel[i + radius];
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= sum;
        }
        float[] scratch = new float[values.length];
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                float acc = 0f;
                for (int k = -radius; k <= radius; k++) {
                    acc += kernel[k + radius] * values[y * TILE_SIZE + Math.floorMod(x + k, TILE_SIZE)];
                }
                scratch[y * TILE_SIZE + x] = acc;
            }
        }
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                float acc = 0f;
                for (int k = -radius; k <= radius; k++) {
                    acc += kernel[k + radius] * scratch[Math.floorMod(y + k, TILE_SIZE) * TILE_SIZE + x];
                }
                values[y * TILE_SIZE + x] = acc;
            }
        }
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
