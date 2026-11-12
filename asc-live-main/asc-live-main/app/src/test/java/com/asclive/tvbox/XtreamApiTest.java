package com.asclive.tvbox;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class XtreamApiTest {

    @Test
    public void usesContainerReportedByServer() {
        XtreamApi.Item item = new XtreamApi.Item(XtreamApi.Kind.LIVE, "42", "Canal", "",
                "1", "", "ts");

        assertEquals("https://server.example/live/user/pass/42.ts",
                XtreamApi.streamUrl("https://server.example/", "user", "pass", item));
    }

    @Test
    public void defaultsToHlsWhenServerOmitsContainer() {
        XtreamApi.Item item = new XtreamApi.Item(XtreamApi.Kind.LIVE, "42", "Canal", "",
                "1", "", "");

        assertEquals("https://server.example/live/user/pass/42.m3u8",
                XtreamApi.streamUrl("https://server.example//", "user", "pass", item));
    }
}
