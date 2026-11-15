package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import android.graphics.Color;
import androidx.core.graphics.ColorUtils;

public final class PlayerColorScheme {
    public static final int DEFAULT_BACKGROUND = 0xFF2F3473;
    static final double MIN_CONTRAST_WITH_WHITE = 7.0;
    static final float NOISE_SATURATION_RATIO = 0.796f;
    static final float NOISE_VALUE_RATIO = 1.305f;
    static final float BUBBLE_SATURATION_RATIO = 0.26f;
    static final float BUBBLE_VALUE = 1f;
    static final int BUBBLE_ALPHA = 0xE8;

    private PlayerColorScheme() {
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
        float[] hsv = new float[3];
        Color.colorToHSV(backgroundColor, hsv);
        hsv[1] = Math.min(1f, hsv[1] * NOISE_SATURATION_RATIO);
        hsv[2] = Math.min(1f, hsv[2] * NOISE_VALUE_RATIO);
        return Color.HSVToColor(hsv);
    }

    public static int bubble(int avatarColor) {
        float[] hsv = new float[3];
        Color.colorToHSV(avatarColor, hsv);
        hsv[1] = Math.min(1f, hsv[1] * BUBBLE_SATURATION_RATIO);
        hsv[2] = BUBBLE_VALUE;
        return ColorUtils.setAlphaComponent(Color.HSVToColor(hsv), BUBBLE_ALPHA);
    }
}
