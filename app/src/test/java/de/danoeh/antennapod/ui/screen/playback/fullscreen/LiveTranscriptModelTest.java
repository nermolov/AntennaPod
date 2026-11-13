package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import de.danoeh.antennapod.model.feed.Transcript;
import de.danoeh.antennapod.model.feed.TranscriptSegment;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LiveTranscriptModelTest {
    private static Transcript wordTranscript() {
        Transcript transcript = new Transcript();
        transcript.addSegment(new TranscriptSegment(1000, 1300, "Hello", "A"));
        transcript.addSegment(new TranscriptSegment(1400, 1700, "there", "A"));
        transcript.addSegment(new TranscriptSegment(3000, 3300, "Hi", "B"));
        transcript.addSegment(new TranscriptSegment(3400, 3800, "friend", "B"));
        transcript.addSegment(new TranscriptSegment(5000, 5300, "Yes", "A"));
        return transcript;
    }

    @Test
    public void testGroupingAndSpeakerIndices() {
        LiveTranscriptModel model = new LiveTranscriptModel(wordTranscript());
        assertEquals(3, model.getTurnCount());
        assertEquals(2, model.getSpeakerCount());
        assertEquals("Hello there", model.getTurn(0).getText());
        assertEquals("A", model.getTurn(0).getSpeaker());
        assertEquals(0, model.getTurn(0).getSpeakerIndex());
        assertEquals(1, model.getTurn(1).getSpeakerIndex());
        assertEquals(0, model.getTurn(2).getSpeakerIndex());
        assertEquals(2, model.getTurn(0).getWordCount());
        assertEquals(1000, model.getTurn(0).getStartTime());
        assertEquals(1700, model.getTurn(0).getEndTime());
    }

    @Test
    public void testCharOffsets() {
        LiveTranscriptModel.Turn turn = new LiveTranscriptModel(wordTranscript()).getTurn(1);
        assertEquals("Hi friend", turn.getText());
        assertEquals(0, turn.getWordCharOffset(0));
        assertEquals(3, turn.getWordCharOffset(1));
        assertEquals(3400, turn.getWordStartTime(1));
    }

    @Test
    public void testVisibleTurnCountAt() {
        LiveTranscriptModel model = new LiveTranscriptModel(wordTranscript());
        assertEquals(0, model.visibleTurnCountAt(0));
        assertEquals(0, model.visibleTurnCountAt(999));
        assertEquals(1, model.visibleTurnCountAt(1000));
        assertEquals(1, model.visibleTurnCountAt(2999));
        assertEquals(2, model.visibleTurnCountAt(3000));
        assertEquals(3, model.visibleTurnCountAt(5000));
        assertEquals(3, model.visibleTurnCountAt(999999));
        assertEquals(1, model.visibleTurnCountAt(1500));
    }

    @Test
    public void testSpokenWordCountAt() {
        LiveTranscriptModel model = new LiveTranscriptModel(wordTranscript());
        assertEquals(0, model.spokenWordCountAt(0, 999));
        assertEquals(1, model.spokenWordCountAt(0, 1000));
        assertEquals(1, model.spokenWordCountAt(0, 1399));
        assertEquals(2, model.spokenWordCountAt(0, 1400));
        assertEquals(2, model.spokenWordCountAt(0, 100000));
        assertEquals(2, model.spokenWordCountAt(0, 1400));
    }

    @Test
    public void testSeekingBackwards() {
        LiveTranscriptModel model = new LiveTranscriptModel(wordTranscript());
        assertEquals(3, model.visibleTurnCountAt(6000));
        assertEquals(1, model.visibleTurnCountAt(2000));
        assertEquals(0, model.spokenWordCountAt(1, 2000));
        assertEquals(2, model.spokenWordCountAt(1, 3400));
    }

    @Test
    public void testMultiWordSegmentDistribution() {
        Transcript transcript = new Transcript();
        transcript.addSegment(new TranscriptSegment(1000, 5000, "one two  three four", "A"));
        transcript.addSegment(new TranscriptSegment(5000, 6000, "five six", "A"));
        LiveTranscriptModel model = new LiveTranscriptModel(transcript);
        assertEquals(1, model.getTurnCount());
        LiveTranscriptModel.Turn turn = model.getTurn(0);
        assertEquals("one two three four five six", turn.getText());
        assertEquals(6, turn.getWordCount());
        assertEquals(1000, turn.getWordStartTime(0));
        assertEquals(2000, turn.getWordStartTime(1));
        assertEquals(3000, turn.getWordStartTime(2));
        assertEquals(4000, turn.getWordStartTime(3));
        assertEquals(5000, turn.getWordStartTime(4));
        assertEquals(5500, turn.getWordStartTime(5));
        assertEquals(4, turn.getWordCharOffset(2) - turn.getWordCharOffset(1));
        assertEquals(6000, turn.getEndTime());
        assertEquals(3, model.spokenWordCountAt(0, 3500));
    }
}
