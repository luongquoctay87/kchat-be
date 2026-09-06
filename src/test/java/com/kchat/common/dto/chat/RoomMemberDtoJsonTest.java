package com.kchat.common.dto.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomMemberDtoJsonTest {

    @Test
    void serializesBooleanFlagsWithIsPrefix() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        String json = mapper.writeValueAsString(
                new RoomMemberDto("id", "user", "Bạn", "member", true, true));
        assertTrue(json.contains("\"is_me\":true"), json);
        assertTrue(json.contains("\"is_online\":true"), json);
    }
}
