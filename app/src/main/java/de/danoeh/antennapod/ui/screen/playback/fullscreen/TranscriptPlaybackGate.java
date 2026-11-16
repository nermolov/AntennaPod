package de.danoeh.antennapod.ui.screen.playback.fullscreen;

public class TranscriptPlaybackGate {
    static final long BLOCK_MARGIN_MS = 2000;
    static final long RESUME_AHEAD_MS = 10000;

    private final long leadMs;
    private boolean blocked = true;

    public TranscriptPlaybackGate(long leadMs) {
        this.leadMs = leadMs;
    }

    public void reset() {
        blocked = true;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public boolean update(long positionMs, long readyUntilMs, boolean complete) {
        if (complete) {
            blocked = false;
        } else if (blocked) {
            blocked = readyUntilMs < positionMs + leadMs + RESUME_AHEAD_MS;
        } else {
            blocked = readyUntilMs <= positionMs + leadMs + BLOCK_MARGIN_MS;
        }
        return blocked;
    }
}
