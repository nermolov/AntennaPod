package de.danoeh.antennapod.parser.transcript;

import de.danoeh.antennapod.model.feed.DiarizedTranscript;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class DiarizedTranscriptCodec {
    private DiarizedTranscriptCodec() {
    }

    public static byte[] encode(DiarizedTranscript transcript) {
        try {
            JSONArray turns = new JSONArray();
            for (DiarizedTranscript.Turn turn : transcript.getTurns()) {
                JSONObject obj = new JSONObject();
                obj.put("s", turn.getSpeaker());
                obj.put("i", turn.getSpeakerIndex());
                JSONArray words = new JSONArray();
                JSONArray starts = new JSONArray();
                JSONArray ends = new JSONArray();
                for (int i = 0; i < turn.getWords().length; i++) {
                    words.put(turn.getWords()[i]);
                    starts.put(turn.getStartMs()[i]);
                    ends.put(turn.getEndMs()[i]);
                }
                obj.put("w", words);
                obj.put("a", starts);
                obj.put("b", ends);
                turns.put(obj);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (OutputStream out = new GZIPOutputStream(bytes)) {
                out.write(turns.toString().getBytes(StandardCharsets.UTF_8));
            }
            return bytes.toByteArray();
        } catch (JSONException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static DiarizedTranscript decode(byte[] data) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            JSONArray array = new JSONArray(new String(out.toByteArray(), StandardCharsets.UTF_8));
            Map<String, String> interned = new HashMap<>();
            List<DiarizedTranscript.Turn> turns = new ArrayList<>();
            for (int t = 0; t < array.length(); t++) {
                JSONObject obj = array.getJSONObject(t);
                JSONArray words = obj.getJSONArray("w");
                JSONArray starts = obj.getJSONArray("a");
                JSONArray ends = obj.getJSONArray("b");
                String[] wordArray = new String[words.length()];
                long[] startArray = new long[words.length()];
                long[] endArray = new long[words.length()];
                for (int i = 0; i < wordArray.length; i++) {
                    wordArray[i] = words.getString(i);
                    startArray[i] = starts.getLong(i);
                    endArray[i] = ends.getLong(i);
                }
                String speaker = obj.getString("s");
                String shared = interned.get(speaker);
                if (shared == null) {
                    interned.put(speaker, speaker);
                    shared = speaker;
                }
                turns.add(new DiarizedTranscript.Turn(shared, obj.getInt("i"), wordArray, startArray, endArray));
            }
            return new DiarizedTranscript(turns);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }
}
