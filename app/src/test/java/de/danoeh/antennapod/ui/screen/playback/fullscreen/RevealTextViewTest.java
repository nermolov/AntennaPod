package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RevealTextViewTest {
    @Test
    public void revealedLineCountCountsLeadingLines() {
        long[] starts = {0, 1000, 2000};
        assertEquals(0, RevealTextView.revealedLineCount(starts, -1));
        assertEquals(1, RevealTextView.revealedLineCount(starts, 0));
        assertEquals(2, RevealTextView.revealedLineCount(starts, 1999));
        assertEquals(3, RevealTextView.revealedLineCount(starts, Long.MAX_VALUE));
    }
}
