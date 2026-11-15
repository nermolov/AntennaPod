package de.danoeh.antennapod.net.transcription;

import java.io.IOException;

public class TranscriptionException extends IOException {
    private final int httpCode;

    public TranscriptionException(int httpCode, String message) {
        super(message);
        this.httpCode = httpCode;
    }

    public TranscriptionException(String message, Throwable cause) {
        super(message, cause);
        this.httpCode = 0;
    }

    public int getHttpCode() {
        return httpCode;
    }

    public boolean isNetworkError() {
        return httpCode == 0;
    }

    public boolean isNotFound() {
        return httpCode == 404;
    }
}
