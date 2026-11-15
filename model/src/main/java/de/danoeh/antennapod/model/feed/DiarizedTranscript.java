package de.danoeh.antennapod.model.feed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DiarizedTranscript {
    private final List<Turn> turns;
    private final int speakerCount;

    public DiarizedTranscript(List<Turn> turns) {
        this.turns = Collections.unmodifiableList(new ArrayList<>(turns));
        int maxIndex = -1;
        for (Turn turn : turns) {
            maxIndex = Math.max(maxIndex, turn.getSpeakerIndex());
        }
        this.speakerCount = maxIndex + 1;
    }

    public List<Turn> getTurns() {
        return turns;
    }

    public int getSpeakerCount() {
        return speakerCount;
    }

    public static class Turn {
        private final String speaker;
        private final int speakerIndex;
        private final String[] words;
        private final long[] startMs;
        private final long[] endMs;

        public Turn(String speaker, int speakerIndex, String[] words, long[] startMs, long[] endMs) {
            this.speaker = speaker;
            this.speakerIndex = speakerIndex;
            this.words = words;
            this.startMs = startMs;
            this.endMs = endMs;
        }

        public String getSpeaker() {
            return speaker;
        }

        public int getSpeakerIndex() {
            return speakerIndex;
        }

        public String[] getWords() {
            return words;
        }

        public long[] getStartMs() {
            return startMs;
        }

        public long[] getEndMs() {
            return endMs;
        }
    }
}
