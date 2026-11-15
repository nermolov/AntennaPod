package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import android.graphics.Color;
import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;

public final class PlayerColorScheme {
    public static final int DEFAULT_BACKGROUND = 0xFF2F3473;
    static final double MIN_CONTRAST_WITH_WHITE = 10.0;
    static final float MIN_PRIMARY_SATURATION = 0.3f;
    static final float MIN_PRIMARY_VALUE = 0.4f;
    static final float NOISE_SATURATION_RATIO = 0.68f;
    static final float NOISE_LIGHTNESS_OFFSET = 0.125f;
    static final float BUBBLE_SATURATION_RATIO = 0.26f;
    static final float BUBBLE_VALUE = 1f;
    static final int BUBBLE_ALPHA = 0xE8;

    private PlayerColorScheme() {
    }

    public static int primary(Palette palette) {
        Palette.Swatch best = null;
        float[] hsv = new float[3];
        for (Palette.Swatch swatch : palette.getSwatches()) {
            Color.colorToHSV(swatch.getRgb(), hsv);
            boolean coloured = hsv[1] >= MIN_PRIMARY_SATURATION && hsv[2] >= MIN_PRIMARY_VALUE;
            if (coloured && (best == null || swatch.getPopulation() > best.getPopulation())) {
                best = swatch;
            }
        }
        return best != null ? best.getRgb() : palette.getDominantColor(DEFAULT_BACKGROUND);
    }

    public static int background(int primaryColor) {
        float[] hsv = new float[3];
        Color.colorToHSV(primaryColor, hsv);
        while (hsv[2] > 0.02f && ColorUtils.calculateContrast(0xFFFFFFFF, Color.HSVToColor(hsv))
                < MIN_CONTRAST_WITH_WHITE) {
            hsv[2] -= 0.01f;
        }
        return Color.HSVToColor(hsv);
    }

    public static int noise(int backgroundColor) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(backgroundColor, hsl);
        hsl[1] = Math.min(1f, hsl[1] * NOISE_SATURATION_RATIO);
        hsl[2] = Math.min(1f, hsl[2] + NOISE_LIGHTNESS_OFFSET);
        return ColorUtils.HSLToColor(hsl);
    }

    public static int bubble(int avatarColor) {
        float[] hsv = new float[3];
        Color.colorToHSV(avatarColor, hsv);
        hsv[1] = Math.min(1f, hsv[1] * BUBBLE_SATURATION_RATIO);
        hsv[2] = BUBBLE_VALUE;
        return ColorUtils.setAlphaComponent(Color.HSVToColor(hsv), BUBBLE_ALPHA);
    }
}
