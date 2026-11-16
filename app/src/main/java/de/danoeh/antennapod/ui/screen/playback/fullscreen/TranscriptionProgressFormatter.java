package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import de.danoeh.antennapod.R;

import java.util.Locale;

public final class TranscriptionProgressFormatter {
    private static final String STATUS_QUEUED = "queued";

    private TranscriptionProgressFormatter() {
    }

    public static boolean isQueued(@Nullable String status) {
        return STATUS_QUEUED.equals(status);
    }

    @StringRes
    public static int stageLabel(@Nullable String status, @Nullable String stage) {
        if (isQueued(status)) {
            return R.string.transcript_stage_queued;
        }
        if (stage == null) {
            return R.string.transcript_stage_working;
        }
        String normalized = stage.toLowerCase(Locale.ROOT);
        if (normalized.contains("queue")) {
            return R.string.transcript_stage_queued;
        } else if (normalized.contains("prepare")) {
            return R.string.transcript_stage_prepare;
        } else if (normalized.contains("vibevoice")) {
            return R.string.transcript_stage_transcribe;
        } else if (normalized.contains("parakeet") || normalized.contains("align")) {
            return R.string.transcript_stage_align;
        } else if (normalized.contains("write")) {
            return R.string.transcript_stage_finish;
        }
        return R.string.transcript_stage_working;
    }

    public static int percent(@Nullable Double overallProgress) {
        if (overallProgress == null || overallProgress.isNaN()) {
            return -1;
        }
        return (int) Math.round(Math.max(0.0, Math.min(1.0, overallProgress)) * 100);
    }
}
