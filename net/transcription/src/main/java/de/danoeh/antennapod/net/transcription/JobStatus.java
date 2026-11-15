package de.danoeh.antennapod.net.transcription;

import androidx.annotation.Nullable;

public class JobStatus {
    public final String status;
    @Nullable
    public final String stage;
    @Nullable
    public final Double overallProgress;
    @Nullable
    public final Integer queuePosition;
    @Nullable
    public final String message;
    @Nullable
    public final String error;
    @Nullable
    public final String transcriptId;

    public JobStatus(String status, @Nullable String stage, @Nullable Double overallProgress,
                     @Nullable Integer queuePosition, @Nullable String message, @Nullable String error,
                     @Nullable String transcriptId) {
        this.status = status;
        this.stage = stage;
        this.overallProgress = overallProgress;
        this.queuePosition = queuePosition;
        this.message = message;
        this.error = error;
        this.transcriptId = transcriptId;
    }

    public boolean isDone() {
        return "done".equals(status);
    }

    public boolean isFailed() {
        return "failed".equals(status) || "error".equals(status) || "cancelled".equals(status);
    }
}
