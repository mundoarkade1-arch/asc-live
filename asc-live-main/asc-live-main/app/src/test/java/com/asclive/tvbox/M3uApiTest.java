package com.asclive.tvbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class M3uApiTest {

    @Test
    public void parsesSimpleUrlWithoutHeaders() {
        M3uApi.StreamInfo info = M3uApi.parseStreamLine(
                "https://example.invalid/live/channel.m3u8", null);

        assertEquals(info.originalUrl, info.effectiveUrl);
        assertTrue(info.headers.isEmpty());
    }

    @Test
    public void separatesPipeHeadersAndDecodesValues() {
        M3uApi.StreamInfo info = M3uApi.parseStreamLine(
                "https://example.invalid/live/channel.ts|User-Agent=TV%20Box&Referer=https%3A%2F%2Fexample.invalid%2F&Cookie=session%3Dabc%3B%20flag%3D1&unknown=value",
                null);

        assertEquals("https://example.invalid/live/channel.ts", info.effectiveUrl);
        assertEquals("TV Box", info.headers.get("User-Agent"));
        assertEquals("https://example.invalid/", info.headers.get("Referer"));
        assertEquals("session=abc; flag=1", info.headers.get("Cookie"));
        assertFalse(info.headers.containsKey("unknown"));
    }

    @Test
    public void preservesExtVlcHeadersAndDoesNotLeakUnknownOptions() {
        Map<String, String> headers = new LinkedHashMap<>();
        M3uApi.parseHeaderOption("http-user-agent=Player%2F1.0", headers);
        M3uApi.parseHeaderOption("http-referrer=https%3A%2F%2Fexample.invalid", headers);
        M3uApi.parseHeaderOption("http-cookie=id%3D1", headers);
        M3uApi.parseHeaderOption("inputstream.adaptive.manifest_type=hls", headers);

        assertEquals("Player/1.0", headers.get("User-Agent"));
        assertEquals("https://example.invalid", headers.get("Referer"));
        assertEquals("id=1", headers.get("Cookie"));
        assertFalse(headers.containsKey("inputstream.adaptive.manifest_type"));
    }

    @Test
    public void keepsLongUrlsIntact() {
        StringBuilder url = new StringBuilder("https://example.invalid/live/channel.ts?");
        while (url.length() < 5000) url.append("p=abcdefghijklmnopqrstuvwxyz&");

        M3uApi.StreamInfo info = M3uApi.parseStreamLine(url.toString(), null);

        assertEquals(url.toString(), info.effectiveUrl);
    }

    @Test
    public void acceptsKnownHeadersFromExtHttpMetadataFallbackSyntax() {
        Map<String, String> headers = new LinkedHashMap<>();
        M3uApi.parseHttpMetadata(
                "User-Agent=Json%2F1&Referer=https%3A%2F%2Fexample.invalid&unknown=ignored",
                headers);

        assertEquals("Json/1", headers.get("User-Agent"));
        assertEquals("https://example.invalid", headers.get("Referer"));
        assertFalse(headers.containsKey("unknown"));
    }
}
