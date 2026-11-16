package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.danoeh.antennapod.R;
import org.junit.Test;

public class TranscriptionProgressFormatterTest {
    @Test
    public void queuedStatusWinsOverStage() {
        assertEquals(R.string.transcript_stage_queued, TranscriptionProgressFormatter.stageLabel("queued", "prepare"));
        assertTrue(TranscriptionProgressFormatter.isQueued("queued"));
        assertFalse(TranscriptionProgressFormatter.isQueued("running"));
    }

    @Test
    public void mapsStages() {
        assertEquals(R.string.transcript_stage_prepare, TranscriptionProgressFormatter.stageLabel("running", "prepare"));
        assertEquals(R.string.transcript_stage_transcribe,
                TranscriptionProgressFormatter.stageLabel("running", "vibevoice"));
        assertEquals(R.string.transcript_stage_align, TranscriptionProgressFormatter.stageLabel("running", "parakeet"));
        assertEquals(R.string.transcript_stage_align, TranscriptionProgressFormatter.stageLabel("running", "align"));
        assertEquals(R.string.transcript_stage_finish, TranscriptionProgressFormatter.stageLabel("running", "write"));
        assertEquals(R.string.transcript_stage_working, TranscriptionProgressFormatter.stageLabel("running", null));
        assertEquals(R.string.transcript_stage_working, TranscriptionProgressFormatter.stageLabel("running", "x"));
    }

    @Test
    public void percentIsClamped() {
        assertEquals(-1, TranscriptionProgressFormatter.percent(null));
        assertEquals(0, TranscriptionProgressFormatter.percent(-0.5));
        assertEquals(42, TranscriptionProgressFormatter.percent(0.424));
        assertEquals(100, TranscriptionProgressFormatter.percent(3.0));
    }
}
