package de.danoeh.antennapod.ui.screen.playback.fullscreen;

public class PlaybackClock {
    private static final long JUMP_THRESHOLD_MS = 1500;

    private long anchorPosition = 0;
    private long anchorTime = 0;
    private boolean playing = false;
    private float speed = 1.0f;
    private long duration = -1;
    private boolean initialized = false;

    public boolean update(long positionMs, long nowMs) {
        boolean jump = !initialized || Math.abs(positionMs - positionAt(nowMs)) > JUMP_THRESHOLD_MS;
        initialized = true;
        anchor(positionMs, nowMs);
        return jump;
    }

    public void setPlaying(boolean playing, long nowMs) {
        anchor(positionAt(nowMs), nowMs);
        this.playing = playing;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setSpeed(float speed, long nowMs) {
        anchor(positionAt(nowMs), nowMs);
        this.speed = speed;
    }

    public void setDuration(long durationMs) {
        this.duration = durationMs;
    }

    public long positionAt(long nowMs) {
        long position = anchorPosition;
        if (playing) {
            position += (long) ((nowMs - anchorTime) * speed);
        }
        return clamp(position);
    }

    public void seekBy(long deltaMs, long nowMs) {
        anchor(positionAt(nowMs) + deltaMs, nowMs);
    }

    private void anchor(long position, long nowMs) {
        anchorPosition = clamp(position);
        anchorTime = nowMs;
    }

    private long clamp(long position) {
        if (duration > 0 && position > duration) {
            position = duration;
        }
        return Math.max(0, position);
    }
}
