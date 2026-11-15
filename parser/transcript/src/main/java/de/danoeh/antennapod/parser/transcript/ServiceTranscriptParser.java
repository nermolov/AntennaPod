package de.danoeh.antennapod.parser.transcript;

import de.danoeh.antennapod.model.feed.DiarizedTranscript;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ServiceTranscriptParser {
    private ServiceTranscriptParser() {
    }

    private static class RawTurn {
        String speaker;
        long start;
        long end;
        String[] words;
        long[] starts;
        long[] ends;
    }

    public static DiarizedTranscript parse(String json) throws JSONException {
        JSONArray array = new JSONArray(json);
        List<RawTurn> raw = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            RawTurn turn = parseTurn(array.getJSONObject(i));
            if (turn != null) {
                raw.add(turn);
            }
        }
        Collections.sort(raw, (a, b) -> Long.compare(a.start, b.start));
        Map<String, Integer> speakerIndices = new HashMap<>();
        List<DiarizedTranscript.Turn> turns = new ArrayList<>();
        for (RawTurn turn : raw) {
            Integer index = speakerIndices.get(turn.speaker);
            if (index == null) {
                index = speakerIndices.size();
                speakerIndices.put(turn.speaker, index);
            }
            turns.add(new DiarizedTranscript.Turn(turn.speaker, index, turn.words, turn.starts, turn.ends));
        }
        return new DiarizedTranscript(turns);
    }

    private static RawTurn parseTurn(JSONObject obj) {
        RawTurn turn = new RawTurn();
        turn.speaker = obj.optString("speaker", "");
        long turnStart = toMs(obj.optDouble("start", 0));
        long turnEnd = Math.max(turnStart, toMs(obj.optDouble("end", obj.optDouble("start", 0))));
        JSONArray jsonWords = obj.optJSONArray("words");
        List<String> words = new ArrayList<>();
        List<Long> starts = new ArrayList<>();
        List<Long> ends = new ArrayList<>();
        if (jsonWords != null && jsonWords.length() > 0) {
            for (int i = 0; i < jsonWords.length(); i++) {
                JSONObject word = jsonWords.optJSONObject(i);
                if (word == null) {
                    continue;
                }
                String text = word.optString("w", "").trim();
                if (text.isEmpty()) {
                    continue;
                }
                long start = toMs(word.optDouble("start", turnStart / 1000.0));
                long end = toMs(word.optDouble("end", start / 1000.0));
                words.add(text);
                starts.add(start);
                ends.add(Math.max(start, end));
            }
        } else {
            String text = obj.optString("text", "").trim();
            if (!text.isEmpty()) {
                String[] parts = text.split("\\s+");
                long length = turnEnd - turnStart;
                for (int i = 0; i < parts.length; i++) {
                    long start = turnStart + length * i / parts.length;
                    long end = turnStart + length * (i + 1) / parts.length;
                    words.add(parts[i]);
                    starts.add(start);
                    ends.add(end);
                }
            }
        }
        if (words.isEmpty()) {
            return null;
        }
        turn.words = words.toArray(new String[0]);
        turn.starts = new long[words.size()];
        turn.ends = new long[words.size()];
        long previous = Long.MIN_VALUE;
        for (int i = 0; i < turn.words.length; i++) {
            long start = Math.max(starts.get(i), previous == Long.MIN_VALUE ? starts.get(i) : previous);
            turn.starts[i] = start;
            turn.ends[i] = Math.max(start, ends.get(i));
            previous = start;
        }
        turn.start = turn.starts[0];
        turn.end = turn.ends[turn.ends.length - 1];
        return turn;
    }

    private static long toMs(double seconds) {
        return Math.round(seconds * 1000.0);
    }
}
