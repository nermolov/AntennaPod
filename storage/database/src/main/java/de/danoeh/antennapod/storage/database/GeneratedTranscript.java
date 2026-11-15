package de.danoeh.antennapod.storage.database;

import androidx.annotation.Nullable;
import de.danoeh.antennapod.model.feed.DiarizedTranscript;

public class GeneratedTranscript {
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_ERROR = "error";

    private final String jobId;
    private final String status;
    private final String error;
    private final DiarizedTranscript transcript;

    public GeneratedTranscript(@Nullable String jobId, @Nullable String status, @Nullable String error,
                               @Nullable DiarizedTranscript transcript) {
        this.jobId = jobId;
        this.status = status;
        this.error = error;
        this.transcript = transcript;
    }

    @Nullable
    public String getJobId() {
        return jobId;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    @Nullable
    public String getError() {
        return error;
    }

    @Nullable
    public DiarizedTranscript getTranscript() {
        return transcript;
    }
}
