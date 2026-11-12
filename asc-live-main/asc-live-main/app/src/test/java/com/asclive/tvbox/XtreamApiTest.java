package com.asclive.tvbox;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class XtreamApiTest {

    @Test
    public void preservesDirectTsUrlWithoutAndroidUriEncoding() {
        XtreamApi.Item item = new XtreamApi.Item(XtreamApi.Kind.LIVE, "1", "Channel", "", "", "",
                "ts", "https://server.invalid/live/1.ts", "https://server.invalid/live/1.ts", null);

        assertEquals("https://server.invalid/live/1.ts",
                XtreamApi.streamUrl("https://server.invalid", "user", "pass", item));
    }

    @Test
    public void preservesDirectHlsUrlWithoutAndroidUriEncoding() {
        XtreamApi.Item item = new XtreamApi.Item(XtreamApi.Kind.LIVE, "2", "Channel", "", "", "",
                "m3u8", "https://server.invalid/live/2.m3u8", "https://server.invalid/live/2.m3u8", null);

        assertEquals("https://server.invalid/live/2.m3u8",
                XtreamApi.streamUrl("https://server.invalid", "user", "pass", item));
    }
}
