package com.kchat.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BotAlertFormatTest {

    @Test
    void formatAndParse_roundTrip() {
        String content = BotAlertFormat.format("⚠ Deploy failed", "k-chat-api", null);
        assertEquals("⚠ Deploy failed · Service: k-chat-api", content);

        BotAlertFormat.Parsed parsed = BotAlertFormat.parse(content);
        assertEquals("⚠ Deploy failed", parsed.title());
        assertEquals("Service: k-chat-api", parsed.service());
    }

    @Test
    void parse_legacySeedFormat() {
        BotAlertFormat.Parsed parsed = BotAlertFormat.parse("⚠ Deploy failed · Service: k-chat-api");
        assertEquals("⚠ Deploy failed", parsed.title());
        assertEquals("Service: k-chat-api", parsed.service());
    }

    @Test
    void parse_multilineBody() {
        String content = BotAlertFormat.format("Alert", "api", "Stack trace line");
        BotAlertFormat.Parsed parsed = BotAlertFormat.parse(content);
        assertEquals("Alert", parsed.title());
        assertEquals("Service: api — Stack trace line", parsed.service());
    }
}
