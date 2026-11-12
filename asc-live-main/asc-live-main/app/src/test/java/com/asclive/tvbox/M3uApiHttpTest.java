package com.asclive.tvbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class M3uApiHttpTest {

    private ServerSocket server;
    private Thread serverThread;

    @Before
    public void startServer() throws IOException {
        server = new ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"));
        serverThread = new Thread(() -> serve(), "m3u-test-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @After
    public void stopServer() {
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
                // teste encerrando
            }
        }
    }

    @Test
    public void downloadsRedirectedPlaylistAndKeepsLiveChannelData() throws Exception {
        AtomicReference<XtreamApi.Item> received = new AtomicReference<>();

        M3uApi.streamLive(url("/redirect"), false, item -> {
            received.set(item);
            return true;
        });

        XtreamApi.Item item = received.get();
        assertTrue(item != null);
        assertEquals("http://stream.invalid/live/ba.ts?token=fixture", item.url);
        assertEquals("Fixture/1.0", item.headers.get("User-Agent"));
        assertEquals("https://site.invalid/", item.headers.get("Referer"));
        assertEquals("id=fixture", item.headers.get("Cookie"));
        assertEquals("Esportes", item.categoryId);
    }

    @Test
    public void reportsHttpErrorForPlaylistDownload() throws Exception {
        try {
            M3uApi.streamLive(url("/missing"), false, item -> true);
            fail("expected playlist HTTP error");
        } catch (IOException error) {
            assertTrue(error.getMessage().startsWith("lista:"));
            assertTrue(error.getMessage().contains("404"));
        }
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getLocalPort() + path;
    }

    private void serve() {
        while (!server.isClosed()) {
            try (Socket socket = server.accept()) {
                BufferedReader request = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                String first = request.readLine();
                if (first == null) continue;
                String path = first.split(" ")[1];
                while (!request.readLine().isEmpty()) {
                    // Consume headers.
                }
                if ("/redirect".equals(path)) {
                    writeResponse(socket, 302, "Location: /playlist\r\n", "");
                } else if ("/missing".equals(path)) {
                    writeResponse(socket, 404, "", "");
                } else {
                    writeResponse(socket, 200, "Content-Type: audio/x-mpegurl; charset=utf-8\r\n",
                            "#EXTM3U\n"
                                    + "#EXTINF:-1 tvg-id=\"ba\" tvg-name=\"Bahia\" "
                                    + "tvg-logo=\"https://logo.invalid/ba.png\" "
                                    + "group-title=\"Esportes\",Bahia\n"
                                    + "#EXTVLCOPT:http-user-agent=Fixture%2F1.0\n"
                                    + "#EXTVLCOPT:http-referrer=https%3A%2F%2Fsite.invalid%2F\n"
                                    + "http://stream.invalid/live/ba.ts?token=fixture|Cookie=id%3Dfixture\n"
                                    + "#EXTINF:-1 group-title=\"Filmes\",Filme\n"
                                    + "https://stream.invalid/movie/film.mp4\n");
                }
            } catch (IOException ignored) {
                return;
            }
        }
    }

    private static void writeResponse(Socket socket, int code, String headers, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String response = "HTTP/1.1 " + code + " Test\r\n"
                + headers + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n";
        OutputStream output = socket.getOutputStream();
        output.write(response.getBytes(StandardCharsets.US_ASCII));
        output.write(bytes);
        output.flush();
    }
}
