package de.danoeh.antennapod.net.transcription;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
public class TranscriptionClientTest {
    private TranscriptionClient client;
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicBoolean offline = new AtomicBoolean(false);

    @Before
    public void setUp() {
        Interceptor fake = chain -> {
            if (offline.get()) {
                throw new IOException("offline");
            }
            Request request = chain.request();
            lastAuth.set(request.header("Authorization"));
            lastQuery.set(request.url().query());
            String path = request.url().encodedPath();
            if (path.equals("/transcripts")) {
                if ("sha256=known".equals(request.url().query())) {
                    return response(request, 200,
                            "{\"status\":\"queued\",\"job_id\":\"j1\",\"transcript_id\":null}");
                }
                return response(request, 404, "{\"detail\":\"nothing\"}");
            } else if (path.equals("/jobs") && request.method().equals("GET")) {
                return response(request, 200, "[{\"id\":\"a\",\"status\":\"running\",\"sha256\":\"h\","
                        + "\"key\":null,\"ready_s\":12.5},{\"id\":\"b\",\"status\":\"done\"},"
                        + "{\"id\":\"c\",\"status\":\"queued\",\"sha256\":null,\"key\":\"k\"}]");
            } else if (path.equals("/jobs") && request.method().equals("DELETE")) {
                lastMethod.set("DELETE /jobs");
                return response(request, 200, "{\"cancelled\":[]}");
            } else if (path.equals("/jobs/j1/partial")) {
                return response(request, 200, "{\"status\":\"running\",\"complete\":false,\"audio_s\":3600.0,"
                        + "\"ready_until\":125.5,\"n_turns\":2,\"since\":1,\"turns\":[{\"speaker\":\"S0\"}]}");
            } else if (path.equals("/jobs/gone/partial")) {
                return response(request, 404, "{\"detail\":\"no such job\"}");
            } else if (path.equals("/jobs/bad/partial")) {
                return response(request, 409, "{\"detail\":\"job failed\"}");
            } else if (path.equals("/jobs/j1")) {
                return response(request, 200, "{\"status\":\"running\",\"stage\":\"vibevoice\","
                        + "\"overall_progress\":0.5,\"queue_position\":null,\"message\":\"m\","
                        + "\"transcript_id\":null,\"ready_s\":30.0,\"audio_s\":null}");
            }
            return response(request, 500, "oops");
        };
        client = new TranscriptionClient(new OkHttpClient.Builder().addInterceptor(fake).build(),
                "http://example.test/", "secret");
    }

    private static Response response(Request request, int code, String body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(ResponseBody.create(body, MediaType.parse("application/json"))).build();
    }

    @Test
    public void testLookupFoundSendsBearer() throws Exception {
        Lookup lookup = client.lookupBySha256("known");
        assertEquals("Bearer secret", lastAuth.get());
        assertEquals("sha256=known", lastQuery.get());
        assertEquals("queued", lookup.status);
        assertEquals("j1", lookup.jobId);
        assertNull(lookup.transcriptId);
        assertFalse(lookup.isDone());
    }

    @Test
    public void testLookupNotFoundReturnsNull() throws Exception {
        assertNull(client.lookupBySha256("unknown"));
    }

    @Test
    public void testGetJobParsesNullableFields() throws Exception {
        JobStatus status = client.getJob("j1");
        assertEquals("running", status.status);
        assertEquals("vibevoice", status.stage);
        assertEquals(0.5, status.overallProgress, 0.0001);
        assertNull(status.queuePosition);
        assertNull(status.error);
        assertEquals(30.0, status.readyS, 0.0001);
        assertNull(status.audioS);
    }

    @Test
    public void testGetPartial() throws Exception {
        Partial partial = client.getPartial("j1", 1);
        assertEquals("since=1", lastQuery.get());
        assertEquals("running", partial.status);
        assertFalse(partial.complete);
        assertEquals(3600.0, partial.audioS, 0.0001);
        assertEquals(125500, partial.readyUntilMs);
        assertEquals(2, partial.turnCount);
        assertTrue(partial.turnsJson.contains("S0"));
    }

    @Test
    public void testGetPartialErrors() {
        try {
            client.getPartial("gone", 0);
            fail();
        } catch (TranscriptionException e) {
            assertTrue(e.isNotFound());
        }
        try {
            client.getPartial("bad", 0);
            fail();
        } catch (TranscriptionException e) {
            assertEquals(409, e.getHttpCode());
        }
    }

    @Test
    public void testListActiveJobsKeepsQueuedAndRunning() throws Exception {
        List<ActiveJob> jobs = client.listActiveJobs();
        assertEquals(2, jobs.size());
        assertEquals("a", jobs.get(0).id);
        assertTrue(jobs.get(0).isRunning());
        assertEquals("h", jobs.get(0).sha256);
        assertNull(jobs.get(0).key);
        assertEquals(12.5, jobs.get(0).readyS, 0.0001);
        assertEquals("c", jobs.get(1).id);
        assertEquals("k", jobs.get(1).key);
        assertNull(jobs.get(1).readyS);
    }

    @Test
    public void testCancelAll() throws Exception {
        client.cancelAll();
        assertEquals("DELETE /jobs", lastMethod.get());
    }

    @Test
    public void testHttpErrorCarriesCode() {
        try {
            client.getJob("bad");
            fail();
        } catch (TranscriptionException e) {
            assertEquals(500, e.getHttpCode());
            assertFalse(e.isNotFound());
            assertFalse(e.isNetworkError());
        }
    }

    @Test
    public void testNetworkError() {
        offline.set(true);
        try {
            client.getJob("j1");
            fail();
        } catch (TranscriptionException e) {
            assertTrue(e.isNetworkError());
        }
    }

    @Test
    public void testSha256Hex() throws Exception {
        File file = File.createTempFile("sha", ".bin");
        try {
            Files.write(file.toPath(), "abc".getBytes(StandardCharsets.UTF_8));
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                    TranscriptionClient.sha256Hex(file));
        } finally {
            assertTrue(file.delete());
        }
    }

    @Test
    public void testEpisodeKey() {
        String key = TranscriptionClient.episodeKey("https://feed/rss", "guid-1");
        assertEquals(64, key.length());
        assertEquals(key, TranscriptionClient.episodeKey("https://feed/rss", "guid-1"));
        assertNotEquals(key, TranscriptionClient.episodeKey("https://feed/rss", "guid-2"));
        assertEquals(TranscriptionClient.episodeKey("a", "b"), TranscriptionClient.episodeKey("a", "b"));
    }
}
