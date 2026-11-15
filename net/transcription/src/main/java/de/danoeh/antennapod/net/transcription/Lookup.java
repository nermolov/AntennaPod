package de.danoeh.antennapod.net.transcription;

import androidx.annotation.Nullable;

public class Lookup {
    public final String status;
    @Nullable
    public final String jobId;
    @Nullable
    public final String transcriptId;

    public Lookup(String status, @Nullable String jobId, @Nullable String transcriptId) {
        this.status = status;
        this.jobId = jobId;
        this.transcriptId = transcriptId;
    }

    public boolean isDone() {
        return "done".equals(status);
    }
}
