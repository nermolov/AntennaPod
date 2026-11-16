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
    @Nullable
    public final Double readyS;
    @Nullable
    public final Double audioS;

    public JobStatus(String status, @Nullable String stage, @Nullable Double overallProgress,
                     @Nullable Integer queuePosition, @Nullable String message, @Nullable String error,
                     @Nullable String transcriptId,
                     @Nullable Double readyS, @Nullable Double audioS) {
        this.status = status;
        this.stage = stage;
        this.overallProgress = overallProgress;
        this.queuePosition = queuePosition;
        this.message = message;
        this.error = error;
        this.transcriptId = transcriptId;
        this.readyS = readyS;
        this.audioS = audioS;
    }

    public boolean isDone() {
        return "done".equals(status);
    }

    public boolean isFailed() {
        return "failed".equals(status) || "error".equals(status) || "cancelled".equals(status);
    }
}
