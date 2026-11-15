package de.danoeh.antennapod.net.transcription;

import androidx.annotation.Nullable;

public class SubmitResult {
    public final String id;
    public final String status;
    public final boolean cached;
    @Nullable
    public final String transcriptId;

    public SubmitResult(String id, String status, boolean cached, @Nullable String transcriptId) {
        this.id = id;
        this.status = status;
        this.cached = cached;
        this.transcriptId = transcriptId;
    }

    public boolean isDone() {
        return "done".equals(status);
    }
}
