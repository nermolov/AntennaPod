package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TranscriptPlaybackGateTest {
    private static final long LEAD = 450;

    @Test
    public void testStartsBlocked() {
        TranscriptPlaybackGate gate = new TranscriptPlaybackGate(LEAD);
        assertTrue(gate.isBlocked());
        assertTrue(gate.update(0, 0, false));
    }

    @Test
    public void testOpensOnlyWhenReadyIsWellAhead() {
        TranscriptPlaybackGate gate = new TranscriptPlaybackGate(LEAD);
        assertTrue(gate.update(0, 5000, false));
        assertTrue(gate.update(0, 10000, false));
        assertFalse(gate.update(0, 11000, false));
    }

    @Test
    public void testBlocksWhenPlaybackCatchesUp() {
        TranscriptPlaybackGate gate = new TranscriptPlaybackGate(LEAD);
        gate.update(0, 60000, false);
        assertFalse(gate.update(30000, 60000, false));
        assertFalse(gate.update(57000, 60000, false));
        assertTrue(gate.update(57600, 60000, false));
    }

    @Test
    public void testHysteresisAfterBlocking() {
        TranscriptPlaybackGate gate = new TranscriptPlaybackGate(LEAD);
        gate.update(0, 60000, false);
        assertTrue(gate.update(58000, 60000, false));
        assertTrue(gate.update(58000, 65000, false));
        assertFalse(gate.update(58000, 69000, false));
    }

    @Test
    public void testSeekPastReadyBlocks() {
        TranscriptPlaybackGate gate = new TranscriptPlaybackGate(LEAD);
        gate.update(0, 120000, false);
        assertFalse(gate.isBlocked());
        assertTrue(gate.update(600000, 120000, false));
    }

    @Test
    public void testCompleteAlwaysOpens() {
        TranscriptPlaybackGate gate = new TranscriptPlaybackGate(LEAD);
        assertFalse(gate.update(900000, 0, true));
    }

    @Test
    public void testResetBlocksAgain() {
        TranscriptPlaybackGate gate = new TranscriptPlaybackGate(LEAD);
        gate.update(0, 120000, false);
        gate.reset();
        assertTrue(gate.isBlocked());
    }
}
