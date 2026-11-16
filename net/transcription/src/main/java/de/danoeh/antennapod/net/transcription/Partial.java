package de.danoeh.antennapod.net.transcription;

import androidx.annotation.Nullable;

public class Partial {
    public final String status;
    public final boolean complete;
    @Nullable
    public final Double audioS;
    public final long readyUntilMs;
    public final int turnCount;
    public final String turnsJson;

    public Partial(String status, boolean complete, @Nullable Double audioS, long readyUntilMs, int turnCount,
                   String turnsJson) {
        this.status = status;
        this.complete = complete;
        this.audioS = audioS;
        this.readyUntilMs = readyUntilMs;
        this.turnCount = turnCount;
        this.turnsJson = turnsJson;
    }
}
