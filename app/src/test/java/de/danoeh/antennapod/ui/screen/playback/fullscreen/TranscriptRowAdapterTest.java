package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import static org.junit.Assert.assertEquals;

import de.danoeh.antennapod.model.feed.DiarizedTranscript;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class TranscriptRowAdapterTest {
    private static LiveTranscriptModel model() {
        List<DiarizedTranscript.Turn> turns = new ArrayList<>();
        turns.add(new DiarizedTranscript.Turn("A", 0, new String[] {"Hello", "there"},
                new long[] {1000, 1400}, new long[] {1300, 1700}));
        turns.add(new DiarizedTranscript.Turn("B", 1, new String[] {"Hi", "friend"},
                new long[] {3000, 3400}, new long[] {3300, 3800}));
        turns.add(new DiarizedTranscript.Turn("A", 0, new String[] {"Yes"},
                new long[] {5000}, new long[] {5300}));
        return new LiveTranscriptModel(new DiarizedTranscript(turns));
    }

    @Test
    public void jumpToShowsStateForNewPosition() {
        TranscriptRowAdapter adapter = new TranscriptRowAdapter();
        adapter.setModel(model());
        adapter.jumpTo(5100);
        assertEquals(3, adapter.getItemCount());
        adapter.jumpTo(3100);
        assertEquals(2, adapter.getItemCount());
        adapter.jumpTo(0);
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void visibleCountAtMatchesModel() {
        TranscriptRowAdapter adapter = new TranscriptRowAdapter();
        adapter.setModel(model());
        assertEquals(2, adapter.visibleCountAt(3100));
    }

    @Test
    public void setTimeAfterJumpContinuesFromJumpedState() {
        TranscriptRowAdapter adapter = new TranscriptRowAdapter();
        adapter.setModel(model());
        adapter.jumpTo(3100);
        adapter.setTime(5100);
        assertEquals(3, adapter.getItemCount());
    }
}
