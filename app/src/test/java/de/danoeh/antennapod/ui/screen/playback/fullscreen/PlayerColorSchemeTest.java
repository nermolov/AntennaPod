package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import androidx.core.graphics.ColorUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PlayerColorSchemeTest {
    private static void assertColorNear(int expected, int actual, int tolerance) {
        assertEquals("red", Color.red(expected), Color.red(actual), tolerance);
        assertEquals("green", Color.green(expected), Color.green(actual), tolerance);
        assertEquals("blue", Color.blue(expected), Color.blue(actual), tolerance);
    }

    @Test
    public void noiseApproximatesDesignForNavy() {
        assertColorNear(0xFF4F5596, PlayerColorScheme.noise(0xFF2F3473), 8);
    }

    @Test
    public void noiseApproximatesDesignForMaroon() {
        assertColorNear(0xFF723238, PlayerColorScheme.noise(0xFF491E22), 20);
    }

    @Test
    public void noiseApproximatesDesignForOlive() {
        assertColorNear(0xFF5D593A, PlayerColorScheme.noise(0xFF464011), 35);
    }

    @Test
    public void bubbleApproximatesDesignForGreenAvatar() {
        int bubble = PlayerColorScheme.bubble(0xFF6B8440);
        assertColorNear(0xFFF4FFE1, bubble, 10);
        assertEquals(0xE8, Color.alpha(bubble));
    }

    @Test
    public void bubbleApproximatesDesignForBlueAvatar() {
        assertColorNear(0xFFDAECFF, PlayerColorScheme.bubble(0xFF5D89B8), 10);
    }

    @Test
    public void backgroundIsDarkenedUntilContrastWithWhiteIsHigh() {
        int background = PlayerColorScheme.background(0xFF2080E8);
        assertTrue(ColorUtils.calculateContrast(0xFFFFFFFF, background) >= PlayerColorScheme.MIN_CONTRAST_WITH_WHITE);
    }

    @Test
    public void backgroundKeepsHueAndSaturationWhenDarkening() {
        float[] before = new float[3];
        float[] after = new float[3];
        Color.colorToHSV(0xFFE8504A, before);
        Color.colorToHSV(PlayerColorScheme.background(0xFFE8504A), after);
        assertEquals(before[0], after[0], 2f);
        assertEquals(before[1], after[1], 0.03f);
        assertTrue(after[2] < before[2]);
    }

    @Test
    public void backgroundLeavesAlreadyDarkColorUntouched() {
        assertEquals(0xFF2F3473, PlayerColorScheme.background(0xFF2F3473));
    }
}
