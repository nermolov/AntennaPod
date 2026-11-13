package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackClockTest {
    @Test
    public void testFirstUpdateIsJump() {
        PlaybackClock clock = new PlaybackClock();
        assertTrue(clock.update(5000, 100));
        assertEquals(5000, clock.positionAt(100));
    }

    @Test
    public void testInterpolatesWhilePlaying() {
        PlaybackClock clock = new PlaybackClock();
        clock.update(10000, 0);
        clock.setPlaying(true, 0);
        assertEquals(10500, clock.positionAt(500));
        assertFalse(clock.update(11000, 1000));
        assertEquals(11250, clock.positionAt(1250));
    }

    @Test
    public void testPausedDoesNotAdvance() {
        PlaybackClock clock = new PlaybackClock();
        clock.update(10000, 0);
        clock.setPlaying(true, 0);
        clock.setPlaying(false, 1000);
        assertFalse(clock.isPlaying());
        assertEquals(11000, clock.positionAt(5000));
    }

    @Test
    public void testSpeedReanchors() {
        PlaybackClock clock = new PlaybackClock();
        clock.update(0, 0);
        clock.setPlaying(true, 0);
        clock.setSpeed(2.0f, 1000);
        assertEquals(1000, clock.positionAt(1000));
        assertEquals(3000, clock.positionAt(2000));
    }

    @Test
    public void testJumpDetection() {
        PlaybackClock clock = new PlaybackClock();
        clock.update(0, 0);
        clock.setPlaying(true, 0);
        assertFalse(clock.update(1400, 1000));
        assertTrue(clock.update(60000, 2000));
        assertTrue(clock.update(0, 3000));
    }

    @Test
    public void testClamping() {
        PlaybackClock clock = new PlaybackClock();
        clock.setDuration(10000);
        clock.update(9000, 0);
        clock.setPlaying(true, 0);
        assertEquals(10000, clock.positionAt(5000));
        clock.seekBy(-50000, 5000);
        assertEquals(0, clock.positionAt(5000));
    }

    @Test
    public void testSeekBy() {
        PlaybackClock clock = new PlaybackClock();
        clock.update(20000, 0);
        clock.setPlaying(true, 0);
        clock.seekBy(10000, 1000);
        assertEquals(31000, clock.positionAt(1000));
        assertEquals(31500, clock.positionAt(1500));
        clock.seekBy(-30000, 1500);
        assertEquals(1500, clock.positionAt(1500));
    }
}
