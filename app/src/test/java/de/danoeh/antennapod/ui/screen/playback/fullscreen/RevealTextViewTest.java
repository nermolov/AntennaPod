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

    @Test
    public void unspokenStartOffsetFindsFirstUnspokenWord() {
        long[] starts = {100, 200, 300};
        int[] offsets = {0, 6, 12};
        assertEquals(0, RevealTextView.unspokenStartOffset(starts, offsets, 50, 17));
        assertEquals(6, RevealTextView.unspokenStartOffset(starts, offsets, 100, 17));
        assertEquals(12, RevealTextView.unspokenStartOffset(starts, offsets, 250, 17));
        assertEquals(17, RevealTextView.unspokenStartOffset(starts, offsets, 300, 17));
        assertEquals(17, RevealTextView.unspokenStartOffset(starts, offsets, Long.MAX_VALUE, 17));
    }
}
