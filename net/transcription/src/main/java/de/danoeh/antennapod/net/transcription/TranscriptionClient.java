package de.danoeh.antennapod.net.transcription;

import androidx.annotation.Nullable;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

public class TranscriptionClient {
    private static final MediaType BINARY = MediaType.parse("application/octet-stream");
    private final OkHttpClient client;
    private final String baseUrl;
    private final String token;

    public TranscriptionClient(OkHttpClient client, String baseUrl, String token) {
        this.client = client;
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        this.baseUrl = trimmed;
        this.token = token;
    }

    public static TranscriptionClient fromPreferences() {
        OkHttpClient client = AntennapodHttpClient.getHttpClient().newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.MINUTES)
                .readTimeout(5, TimeUnit.MINUTES)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        return new TranscriptionClient(client, UserPreferences.getTranscriptionServerUrl(),
                UserPreferences.getTranscriptionApiToken());
    }

    public static String sha256Hex(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String episodeKey(@Nullable String feedDownloadUrl, @Nullable String identifyingValue) {
        String combined = (feedDownloadUrl == null ? "" : feedDownloadUrl) + "\n"
                + (identifyingValue == null ? "" : identifyingValue);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(combined.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String episodeKey(FeedItem item) {
        String feedUrl = item.getFeed() == null ? null : item.getFeed().getDownloadUrl();
        return episodeKey(feedUrl, item.getIdentifyingValue());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b & 0xff));
        }
        return builder.toString();
    }

    @Nullable
    public Lookup lookupBySha256(String sha256) throws TranscriptionException {
        return lookup("sha256", sha256);
    }

    @Nullable
    public Lookup lookupByKey(String key) throws TranscriptionException {
        return lookup("key", key);
    }

    @Nullable
    private Lookup lookup(String param, String value) throws TranscriptionException {
        HttpUrl url = baseHttpUrl().newBuilder().addPathSegment("transcripts")
                .addQueryParameter(param, value).build();
        try {
            JSONObject json = new JSONObject(execute(request(url).get().build()));
            return new Lookup(json.optString("status", ""), optString(json, "job_id"),
                    optString(json, "transcript_id"));
        } catch (TranscriptionException e) {
            if (e.isNotFound()) {
                return null;
            }
            throw e;
        } catch (JSONException e) {
            throw new TranscriptionException("Invalid response", e);
        }
    }

    public SubmitResult submit(File audio, String sha256, String key, @Nullable String context)
            throws TranscriptionException {
        MultipartBody.Builder body = new MultipartBody.Builder().setType(MultipartBody.FORM);
        if (sha256 != null && !sha256.isEmpty()) {
            body.addFormDataPart("sha256", sha256);
        }
        if (key != null && !key.isEmpty()) {
            body.addFormDataPart("key", key);
        }
        if (context != null && !context.isEmpty()) {
            body.addFormDataPart("context", context);
        }
        body.addFormDataPart("file", audio.getName(), RequestBody.create(audio, BINARY));
        HttpUrl url = baseHttpUrl().newBuilder().addPathSegment("jobs").build();
        try {
            JSONObject json = new JSONObject(execute(request(url).post(body.build()).build()));
            return new SubmitResult(json.getString("id"), json.optString("status", ""),
                    json.optBoolean("cached", false), optString(json, "transcript_id"));
        } catch (JSONException e) {
            throw new TranscriptionException("Invalid response", e);
        }
    }

    public JobStatus getJob(String id) throws TranscriptionException {
        HttpUrl url = baseHttpUrl().newBuilder().addPathSegment("jobs").addPathSegment(id).build();
        try {
            JSONObject json = new JSONObject(execute(request(url).get().build()));
            Double progress = json.isNull("overall_progress") ? null : json.optDouble("overall_progress");
            Integer queuePosition = json.isNull("queue_position") ? null : json.optInt("queue_position");
            return new JobStatus(json.optString("status", ""), optString(json, "stage"), progress, queuePosition,
                    optString(json, "message"), optString(json, "error"), optString(json, "transcript_id"));
        } catch (JSONException e) {
            throw new TranscriptionException("Invalid response", e);
        }
    }

    public String getJobResult(String id) throws TranscriptionException {
        HttpUrl url = baseHttpUrl().newBuilder().addPathSegment("jobs").addPathSegment(id)
                .addPathSegment("result").addQueryParameter("format", "json").build();
        return execute(request(url).get().build());
    }

    public String getTranscriptResult(String transcriptId) throws TranscriptionException {
        HttpUrl url = baseHttpUrl().newBuilder().addPathSegment("transcripts").addPathSegment(transcriptId)
                .addPathSegment("result").addQueryParameter("format", "json").build();
        return execute(request(url).get().build());
    }

    public void deleteJob(String id) throws TranscriptionException {
        HttpUrl url = baseHttpUrl().newBuilder().addPathSegment("jobs").addPathSegment(id).build();
        execute(request(url).delete().build());
    }

    @Nullable
    private static String optString(JSONObject json, String name) {
        if (json.isNull(name)) {
            return null;
        }
        String value = json.optString(name, "");
        return value.isEmpty() ? null : value;
    }

    private HttpUrl baseHttpUrl() throws TranscriptionException {
        HttpUrl url = HttpUrl.parse(baseUrl);
        if (url == null) {
            throw new TranscriptionException(0, "Invalid transcription server URL");
        }
        return url;
    }

    private Request.Builder request(HttpUrl url) {
        return new Request.Builder().url(url).header("Authorization", "Bearer " + token);
    }

    private String execute(Request request) throws TranscriptionException {
        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            if (!response.isSuccessful()) {
                throw new TranscriptionException(response.code(), describeError(response.code(), text));
            }
            return text;
        } catch (IOException e) {
            if (e instanceof TranscriptionException) {
                throw (TranscriptionException) e;
            }
            throw new TranscriptionException(String.valueOf(e.getMessage()), e);
        }
    }

    private static String describeError(int code, String body) {
        try {
            String detail = new JSONObject(body).optString("detail", "");
            if (!detail.isEmpty()) {
                return "HTTP " + code + ": " + detail;
            }
        } catch (JSONException ignored) {
            return "HTTP " + code;
        }
        return "HTTP " + code;
    }
}
