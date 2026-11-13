package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import de.danoeh.antennapod.model.feed.Transcript;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class FakeTranscriptGeneratorTest {
    private static final long DURATION = 10 * 60 * 1000L;

    @Test
    public void testDeterministic() {
        Transcript a = FakeTranscriptGenerator.generate(42, DURATION);
        Transcript b = FakeTranscriptGenerator.generate(42, DURATION);
        assertEquals(a.getSegmentCount(), b.getSegmentCount());
        for (int i = 0; i < a.getSegmentCount(); i++) {
            assertEquals(a.getSegmentAt(i).getStartTime(), b.getSegmentAt(i).getStartTime());
            assertEquals(a.getSegmentAt(i).getWords(), b.getSegmentAt(i).getWords());
            assertEquals(a.getSegmentAt(i).getSpeaker(), b.getSegmentAt(i).getSpeaker());
        }
    }

    @Test
    public void testDifferentSeedsDiffer() {
        Transcript a = FakeTranscriptGenerator.generate(1, DURATION);
        Transcript b = FakeTranscriptGenerator.generate(2, DURATION);
        boolean differs = a.getSegmentCount() != b.getSegmentCount();
        for (int i = 0; !differs && i < a.getSegmentCount(); i++) {
            differs = a.getSegmentAt(i).getStartTime() != b.getSegmentAt(i).getStartTime();
        }
        assertTrue(differs);
    }

    @Test
    public void testStartsStrictlyIncreasingAndCoversDuration() {
        Transcript transcript = FakeTranscriptGenerator.generate(7, DURATION);
        long previous = -1;
        for (int i = 0; i < transcript.getSegmentCount(); i++) {
            long start = transcript.getSegmentAt(i).getStartTime();
            assertTrue(start > previous);
            assertTrue(transcript.getSegmentAt(i).getEndTime() > start);
            assertEquals(-1, transcript.getSegmentAt(i).getWords().indexOf(' '));
            previous = start;
        }
        assertTrue(previous < DURATION);
        assertTrue(previous > DURATION - 30000);
        assertTrue(transcript.getSegmentAt(0).getStartTime() < 2000);
    }

    @Test
    public void testDefaultDuration() {
        Transcript transcript = FakeTranscriptGenerator.generate(3, 0);
        long last = transcript.getSegmentAt(transcript.getSegmentCount() - 1).getStartTime();
        assertTrue(last > 59 * 60 * 1000L);
    }

    @Test
    public void testThreeSpeakersAndNoBackToBackTurns() {
        Transcript transcript = FakeTranscriptGenerator.generate(11, DURATION);
        assertEquals(3, transcript.getSpeakers().size());
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < transcript.getSegmentCount(); i++) {
            seen.add(transcript.getSegmentAt(i).getSpeaker());
        }
        assertEquals(3, seen.size());

        LiveTranscriptModel model = new LiveTranscriptModel(transcript);
        assertTrue(model.getTurnCount() > 10);
        for (int i = 1; i < model.getTurnCount(); i++) {
            assertNotEquals(model.getTurn(i - 1).getSpeaker(), model.getTurn(i).getSpeaker());
        }
    }

    @Test
    public void testTurnLengthsVary() {
        LiveTranscriptModel model = new LiveTranscriptModel(FakeTranscriptGenerator.generate(5, DURATION));
        long min = Long.MAX_VALUE;
        long max = 0;
        for (int i = 0; i < model.getTurnCount(); i++) {
            long length = model.getTurn(i).getEndTime() - model.getTurn(i).getStartTime();
            min = Math.min(min, length);
            max = Math.max(max, length);
        }
        assertTrue(max > 2 * min);
        assertTrue(max < 40000);
    }
}
