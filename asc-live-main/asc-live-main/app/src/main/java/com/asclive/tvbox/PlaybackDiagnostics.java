package com.asclive.tvbox;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.app.Activity;

import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Diagnóstico privado e exportável de uma sessão de reprodução. */
final class PlaybackDiagnostics {

    private static final String DIR = "playback-diagnostics";
    private static final int MAX_FILES = 5;
    private static final long MAX_BYTES = 512L * 1024L;
    private static final SimpleDateFormat DATE =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);
    private static final Pattern URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET_PARAM = Pattern.compile(
            "(?i)((?:token|auth|authorization|password|passwd|key|secret)=)[^&\\s]+");
    private static final Pattern SECRET_HEADER = Pattern.compile(
            "(?i)((?:cookie|authorization):\\s*)[^\\s]+");
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "asc-playback-diagnostics");
        thread.setDaemon(true);
        return thread;
    });

    private PlaybackDiagnostics() {
    }

    static Session start(Context context, String channel, String channelId) {
        File dir = new File(context.getFilesDir(), DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            return Session.disabled();
        }
        rotate(dir);
        String id = DATE.format(new Date()).replaceAll("[^0-9]", "")
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        File file = new File(dir, id + ".txt");
        Session session = new Session(id, file);
        session.event("session_started", "channel=" + safeText(channel)
                + ",channelId=" + safeText(channelId));
        return session;
    }

    static String latest(Context context) {
        try {
            WRITER.submit(() -> { }).get();
        } catch (Exception ignored) {
            // Continua com o que já foi persistido.
        }
        File file = latestFile(context);
        if (file == null) {
            return "Nenhuma tentativa de reprodução registrada.";
        }
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
            return out.toString();
        } catch (IOException error) {
            return "Não foi possível ler o diagnóstico: " + safeText(error.getMessage());
        }
    }

    static File latestFile(Context context) {
        File dir = new File(context.getFilesDir(), DIR);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".txt"));
        if (files == null || files.length == 0) return null;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files[0];
    }

    static void rotateForTest(File directory) {
        rotate(directory);
    }

    static void exportLatest(Activity context) {
        Bg.run(context, () -> {
            File source = latestFile(context);
            if (source == null) return null;
            File exportDir = new File(context.getCacheDir(), "diagnostics");
            if (!exportDir.exists() && !exportDir.mkdirs()) {
                throw new IOException("export directory unavailable");
            }
            File copy = new File(exportDir, "asc-live-diagnostico.txt");
            copyText(source, copy);
            return copy;
        }, new Bg.Done<File>() {
            @Override
            public void ok(File copy) {
                if (copy == null) {
                    Ui.toast(context, "Nenhum diagnóstico para exportar.");
                    return;
                }
                try {
                    Uri uri = FileProvider.getUriForFile(context,
                            context.getPackageName() + ".fileprovider", copy);
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico ASC Live");
                    share.putExtra(Intent.EXTRA_TEXT, "Relatório de diagnóstico do ASC Live.");
                    share.putExtra(Intent.EXTRA_STREAM, uri);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(Intent.createChooser(share, "Exportar diagnóstico"));
                } catch (Exception error) {
                    Ui.toast(context, "Não foi possível exportar o diagnóstico.");
                }
            }

            @Override
            public void fail(Exception error) {
                Ui.toast(context, "Não foi possível exportar o diagnóstico.");
            }
        });
    }

    static String sanitize(String value) {
        if (value == null || value.isEmpty()) return "unknown";
        String safe = URL.matcher(value).replaceAll("<url>");
        safe = SECRET_PARAM.matcher(safe).replaceAll("$1<redacted>");
        safe = SECRET_HEADER.matcher(safe).replaceAll("$1<redacted>");
        return safe.length() > 500 ? safe.substring(0, 500) : safe;
    }

    private static String safeText(String value) {
        return sanitize(value).replace('\n', ' ').replace('\r', ' ');
    }

    private static void copyText(File source, File target) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(source));
             BufferedWriter writer = new BufferedWriter(new FileWriter(target, false))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                writer.write(buffer, 0, count);
            }
        }
    }

    private static void rotate(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".txt"));
        if (files == null) return;
        List<File> sorted = new ArrayList<>(Arrays.asList(files));
        Collections.sort(sorted, Comparator.comparingLong(File::lastModified).reversed());
        long total = 0;
        for (int i = 0; i < sorted.size(); i++) {
            File file = sorted.get(i);
            total += file.length();
            if (i >= MAX_FILES || total > MAX_BYTES) {
                // Relatório antigo é descartável; o atual nunca entra nesta lista.
                file.delete();
            }
        }
    }

    static final class Session {
        private final String id;
        private final File file;
        private boolean firstFailure;
        private boolean closed;

        private Session(String id, File file) {
            this.id = id;
            this.file = file;
        }

        static Session forTest(File file) {
            return new Session("test-session", file);
        }

        static Session disabled() {
            return new Session("disabled", null);
        }

        synchronized void event(String stage, String details) {
            if (closed || file == null) return;
            String line = DATE.format(new Date()) + " | " + id + " | "
                    + safeText(stage) + " | " + safeText(details);
            enqueue(line);
        }

        synchronized void firstFailure(String stage, String details) {
            if (!firstFailure) {
                firstFailure = true;
                event("first_failure", "stage=" + stage + "," + details);
            }
            event(stage, details);
        }

        synchronized void finish(String result, String details) {
            if (closed || file == null) return;
            String line = DATE.format(new Date()) + " | " + id + " | final_result | "
                    + safeText("result=" + result + "," + details);
            enqueue(line);
            closed = true;
        }

        void flushForTest() throws Exception {
            Future<?> done = WRITER.submit(() -> { });
            done.get();
        }

        private void enqueue(String line) {
            WRITER.execute(() -> append(file, line));
        }

        String id() {
            return id;
        }
    }

    private static void append(File file, String line) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write(line);
            writer.newLine();
        } catch (IOException ignored) {
            // O diagnóstico nunca pode interromper a reprodução.
        }
    }
}
