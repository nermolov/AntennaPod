package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import de.danoeh.antennapod.model.feed.DiarizedTranscript;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LiveTranscriptModelTest {
    private static DiarizedTranscript.Turn turn(String speaker, int index, String[] words, long[] starts,
                                                long[] ends) {
        return new DiarizedTranscript.Turn(speaker, index, words, starts, ends);
    }

    private static DiarizedTranscript transcript() {
        List<DiarizedTranscript.Turn> turns = new ArrayList<>();
        turns.add(turn("A", 0, new String[] {"Hello", "there"}, new long[] {1000, 1400}, new long[] {1300, 1700}));
        turns.add(turn("B", 1, new String[] {"Hi", "friend"}, new long[] {3000, 3400}, new long[] {3300, 3800}));
        turns.add(turn("A", 0, new String[] {"Yes"}, new long[] {5000}, new long[] {5300}));
        return new DiarizedTranscript(turns);
    }

    @Test
    public void testTurnsAndSpeakerIndices() {
        LiveTranscriptModel model = new LiveTranscriptModel(transcript());
        assertEquals(3, model.getTurnCount());
        assertEquals(2, model.getSpeakerCount());
        assertEquals("Hello there", model.getTurn(0).getText());
        assertEquals("A", model.getTurn(0).getSpeaker());
        assertEquals(0, model.getTurn(0).getSpeakerIndex());
        assertEquals(1, model.getTurn(1).getSpeakerIndex());
        assertEquals(2, model.getTurn(0).getWordCount());
        assertEquals(1000, model.getTurn(0).getStartTime());
        assertEquals(1700, model.getTurn(0).getEndTime());
    }

    @Test
    public void testCharOffsets() {
        LiveTranscriptModel.Turn turn = new LiveTranscriptModel(transcript()).getTurn(1);
        assertEquals("Hi friend", turn.getText());
        assertEquals(0, turn.getWordCharOffset(0));
        assertEquals(3, turn.getWordCharOffset(1));
        assertEquals(3400, turn.getWordStartTime(1));
    }

    @Test
    public void testVisibleTurnCountAt() {
        LiveTranscriptModel model = new LiveTranscriptModel(transcript());
        assertEquals(0, model.visibleTurnCountAt(999));
        assertEquals(1, model.visibleTurnCountAt(1000));
        assertEquals(1, model.visibleTurnCountAt(2999));
        assertEquals(2, model.visibleTurnCountAt(3000));
        assertEquals(3, model.visibleTurnCountAt(999999));
        assertEquals(1, model.visibleTurnCountAt(2000));
    }

    @Test
    public void testSpokenWordCountAt() {
        LiveTranscriptModel model = new LiveTranscriptModel(transcript());
        assertEquals(0, model.spokenWordCountAt(0, 999));
        assertEquals(1, model.spokenWordCountAt(0, 1399));
        assertEquals(2, model.spokenWordCountAt(0, 1400));
        assertEquals(0, model.spokenWordCountAt(1, 2000));
    }

    @Test
    public void testOverlappingTurnsAndEmptyTurns() {
        List<DiarizedTranscript.Turn> turns = new ArrayList<>();
        turns.add(turn("A", 0, new String[] {"one", "two", "three"}, new long[] {1000, 2000, 3000},
                new long[] {1900, 2900, 3900}));
        turns.add(turn("B", 1, new String[] {"uh-huh"}, new long[] {2500}, new long[] {2800}));
        turns.add(turn("B", 1, new String[0], new long[0], new long[0]));
        LiveTranscriptModel model = new LiveTranscriptModel(new DiarizedTranscript(turns));
        assertEquals(2, model.getTurnCount());
        assertEquals(2, model.getSpeakerCount());
        assertEquals(2, model.visibleTurnCountAt(2500));
        assertEquals(2, model.spokenWordCountAt(0, 2500));
        assertEquals(3900, model.getTurn(0).getEndTime());
    }

    @Test
    public void testAppendTurns() {
        LiveTranscriptModel model = new LiveTranscriptModel(new DiarizedTranscript(new ArrayList<>()));
        assertEquals(0, model.getSpeakerCount());
        List<DiarizedTranscript.Turn> first = new ArrayList<>();
        first.add(turn("A", 0, new String[] {"Hello"}, new long[] {1000}, new long[] {1300}));
        model.appendTurns(first);
        assertEquals(1, model.getTurnCount());
        assertEquals(1, model.getSpeakerCount());
        assertEquals(1, model.visibleTurnCountAt(5000));
        List<DiarizedTranscript.Turn> second = new ArrayList<>();
        second.add(turn("C", 2, new String[] {"Hi", "you"}, new long[] {4000, 4500}, new long[] {4400, 4900}));
        second.add(turn("B", 1, new String[0], new long[0], new long[0]));
        model.appendTurns(second);
        assertEquals(2, model.getTurnCount());
        assertEquals(3, model.getSpeakerCount());
        assertEquals(1, model.visibleTurnCountAt(3999));
        assertEquals(2, model.visibleTurnCountAt(4000));
        assertEquals(2, model.spokenWordCountAt(1, 4500));
        assertEquals("Hi you", model.getTurn(1).getText());
    }

    @Test
    public void testReadyUntilAndComplete() {
        LiveTranscriptModel model = new LiveTranscriptModel(transcript());
        assertEquals(0, model.getReadyUntilMs());
        assertEquals(false, model.isComplete());
        model.setReadyUntilMs(120000);
        model.setComplete(true);
        assertEquals(120000, model.getReadyUntilMs());
        assertEquals(true, model.isComplete());
    }
}
