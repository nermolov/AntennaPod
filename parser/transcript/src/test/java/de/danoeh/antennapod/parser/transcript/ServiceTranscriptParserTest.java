package de.danoeh.antennapod.parser.transcript;

import de.danoeh.antennapod.model.feed.DiarizedTranscript;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ServiceTranscriptParserTest {
    private DiarizedTranscript transcript;

    @Before
    public void setUp() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("service_transcript.json")) {
            transcript = ServiceTranscriptParser.parse(IOUtils.toString(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testSortedByStartAndEmptyTurnsDropped() {
        assertEquals(5, transcript.getTurns().size());
        long previous = -1;
        for (DiarizedTranscript.Turn turn : transcript.getTurns()) {
            assertTrue(turn.getStartMs()[0] >= previous);
            previous = turn.getStartMs()[0];
        }
        assertEquals("QBasic", transcript.getTurns().get(0).getWords()[0]);
        assertEquals(1060, transcript.getTurns().get(0).getStartMs()[0]);
    }

    @Test
    public void testSpeakerIndicesByFirstAppearance() {
        assertEquals(3, transcript.getSpeakerCount());
        assertEquals("SPEAKER_00", transcript.getTurns().get(0).getSpeaker());
        assertEquals(0, transcript.getTurns().get(0).getSpeakerIndex());
        assertEquals(1, transcript.getTurns().get(1).getSpeakerIndex());
        assertEquals(0, transcript.getTurns().get(2).getSpeakerIndex());
        assertEquals(2, transcript.getTurns().get(3).getSpeakerIndex());
        assertEquals(1, transcript.getTurns().get(4).getSpeakerIndex());
    }

    @Test
    public void testOverlappingTurnsKept() {
        DiarizedTranscript.Turn first = transcript.getTurns().get(1);
        DiarizedTranscript.Turn second = transcript.getTurns().get(2);
        long firstEnd = first.getEndMs()[first.getEndMs().length - 1];
        assertTrue(second.getStartMs()[0] < firstEnd);
        assertEquals(9840, second.getStartMs()[0]);
    }

    @Test
    public void testWordStartsAreMonotonic() {
        DiarizedTranscript.Turn turn = transcript.getTurns().get(4);
        assertEquals(4, turn.getWords().length);
        assertArrayEquals(new long[] {42080, 42400, 42400, 42880}, turn.getStartMs());
        for (DiarizedTranscript.Turn t : transcript.getTurns()) {
            for (int i = 0; i < t.getWords().length; i++) {
                assertTrue(t.getEndMs()[i] >= t.getStartMs()[i]);
                if (i > 0) {
                    assertTrue(t.getStartMs()[i] >= t.getStartMs()[i - 1]);
                }
            }
        }
    }

    @Test
    public void testTextOnlyTurnInterpolated() {
        DiarizedTranscript.Turn turn = transcript.getTurns().get(3);
        assertArrayEquals(new String[] {"one", "two", "three", "four"}, turn.getWords());
        assertArrayEquals(new long[] {40000, 40500, 41000, 41500}, turn.getStartMs());
        assertArrayEquals(new long[] {40500, 41000, 41500, 42000}, turn.getEndMs());
    }

    @Test
    public void testCodecRoundTrip() throws Exception {
        byte[] encoded = DiarizedTranscriptCodec.encode(transcript);
        DiarizedTranscript decoded = DiarizedTranscriptCodec.decode(encoded);
        assertEquals(transcript.getSpeakerCount(), decoded.getSpeakerCount());
        assertEquals(transcript.getTurns().size(), decoded.getTurns().size());
        for (int i = 0; i < transcript.getTurns().size(); i++) {
            DiarizedTranscript.Turn a = transcript.getTurns().get(i);
            DiarizedTranscript.Turn b = decoded.getTurns().get(i);
            assertEquals(a.getSpeaker(), b.getSpeaker());
            assertEquals(a.getSpeakerIndex(), b.getSpeakerIndex());
            assertArrayEquals(a.getWords(), b.getWords());
            assertArrayEquals(a.getStartMs(), b.getStartMs());
            assertArrayEquals(a.getEndMs(), b.getEndMs());
        }
    }
}
