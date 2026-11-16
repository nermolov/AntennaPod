package de.danoeh.antennapod.net.transcription;

import androidx.annotation.Nullable;

public class ActiveJob {
    public final String id;
    public final String status;
    @Nullable
    public final String sha256;
    @Nullable
    public final String key;
    @Nullable
    public final Double readyS;

    public ActiveJob(String id, String status, @Nullable String sha256, @Nullable String key,
                     @Nullable Double readyS) {
        this.id = id;
        this.status = status;
        this.sha256 = sha256;
        this.key = key;
        this.readyS = readyS;
    }

    public boolean isRunning() {
        return "running".equals(status);
    }
}
