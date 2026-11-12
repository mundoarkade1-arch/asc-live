package com.asclive.tvbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.Test;

public class PlaybackDiagnosticsTest {

    @Test
    public void keepsEventsInChronologicalWriteOrderAndMarksFirstFailure() throws Exception {
        File file = File.createTempFile("asc-diagnostic-", ".txt");
        try {
            PlaybackDiagnostics.Session session = PlaybackDiagnostics.Session.forTest(file);
            session.event("attempt_started", "attempt=1");
            session.firstFailure("http_error", "http=403");
            session.event("retry_reconnect", "round=1");
            session.firstFailure("http_error", "http=500");
            session.finish("failed", "firstFailureRecorded=true");
            session.flushForTest();

            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            assertTrue(text.indexOf("attempt_started") < text.indexOf("first_failure"));
            assertTrue(text.indexOf("first_failure") < text.indexOf("retry_reconnect"));
            assertEquals(1, count(text, "first_failure"));
            assertTrue(text.contains("final_result"));
        } finally {
            file.delete();
        }
    }

    @Test
    public void sanitizesUrlsSecretsAndHeaders() {
        String value = PlaybackDiagnostics.sanitize(
                "url=https://private.invalid/live.ts?token=abc cookie: full-cookie");

        assertFalse(value.contains("private.invalid"));
        assertFalse(value.contains("abc"));
        assertFalse(value.contains("full-cookie"));
        assertTrue(value.contains("<url>"));
    }

    @Test
    public void rotatesOldFilesByCountAndTotalSize() throws Exception {
        File dir = Files.createTempDirectory("asc-diagnostics-").toFile();
        try {
            for (int i = 0; i < 7; i++) {
                File file = new File(dir, "old-" + i + ".txt");
                Files.write(file.toPath(), Arrays.asList("diagnostic"), StandardCharsets.UTF_8);
                file.setLastModified(System.currentTimeMillis() - (i * 1000L));
            }
            PlaybackDiagnostics.rotateForTest(dir);
            assertTrue(dir.listFiles((d, name) -> name.endsWith(".txt")).length <= 5);
        } finally {
            File[] files = dir.listFiles();
            if (files != null) for (File file : files) file.delete();
            dir.delete();
        }
    }

    private static int count(String text, String part) {
        int count = 0;
        int at = 0;
        while ((at = text.indexOf(part, at)) >= 0) {
            count++;
            at += part.length();
        }
        return count;
    }
}
