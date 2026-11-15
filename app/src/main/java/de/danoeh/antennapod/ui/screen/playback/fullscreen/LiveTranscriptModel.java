package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import de.danoeh.antennapod.model.feed.DiarizedTranscript;

import java.util.ArrayList;
import java.util.List;

public class LiveTranscriptModel {
    private final List<Turn> turns = new ArrayList<>();
    private final int speakerCount;

    public LiveTranscriptModel(DiarizedTranscript transcript) {
        for (DiarizedTranscript.Turn source : transcript.getTurns()) {
            if (source.getWords().length == 0) {
                continue;
            }
            Turn.Builder builder = new Turn.Builder(source.getSpeaker(), source.getSpeakerIndex());
            for (int i = 0; i < source.getWords().length; i++) {
                builder.add(source.getWords()[i], source.getStartMs()[i]);
                builder.endTime = Math.max(builder.endTime, source.getEndMs()[i]);
            }
            turns.add(builder.build());
        }
        speakerCount = transcript.getSpeakerCount();
    }

    public int getTurnCount() {
        return turns.size();
    }

    public Turn getTurn(int i) {
        return turns.get(i);
    }

    public int getSpeakerCount() {
        return speakerCount;
    }

    public int visibleTurnCountAt(long timeMs) {
        int low = 0;
        int high = turns.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (turns.get(mid).getStartTime() <= timeMs) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    public int spokenWordCountAt(int turnIndex, long timeMs) {
        Turn turn = turns.get(turnIndex);
        int low = 0;
        int high = turn.getWordCount();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (turn.getWordStartTime(mid) <= timeMs) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    public static class Turn {
        private final String speaker;
        private final int speakerIndex;
        private final String text;
        private final long[] wordStarts;
        private final int[] charOffsets;
        private final long endTime;

        private Turn(Builder builder) {
            speaker = builder.speaker;
            speakerIndex = builder.speakerIndex;
            text = builder.text.toString();
            wordStarts = new long[builder.starts.size()];
            charOffsets = new int[builder.offsets.size()];
            for (int i = 0; i < wordStarts.length; i++) {
                wordStarts[i] = builder.starts.get(i);
                charOffsets[i] = builder.offsets.get(i);
            }
            endTime = builder.endTime;
        }

        public String getSpeaker() {
            return speaker;
        }

        public int getSpeakerIndex() {
            return speakerIndex;
        }

        public String getText() {
            return text;
        }

        public int getWordCount() {
            return wordStarts.length;
        }

        public long getWordStartTime(int i) {
            return wordStarts[i];
        }

        public int getWordCharOffset(int i) {
            return charOffsets[i];
        }

        public long getStartTime() {
            return wordStarts[0];
        }

        public long getEndTime() {
            return endTime;
        }

        private static class Builder {
            final String speaker;
            final int speakerIndex;
            final StringBuilder text = new StringBuilder();
            final List<Long> starts = new ArrayList<>();
            final List<Integer> offsets = new ArrayList<>();
            long endTime = 0;

            Builder(String speaker, int speakerIndex) {
                this.speaker = speaker;
                this.speakerIndex = speakerIndex;
            }

            void add(String word, long start) {
                if (text.length() > 0) {
                    text.append(' ');
                }
                offsets.add(text.length());
                starts.add(start);
                text.append(word);
            }

            Turn build() {
                return new Turn(this);
            }
        }
    }
}
