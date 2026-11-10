package com.basketbounce.api;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FishGameAPITest {
    @Test
    void readsOnlyFishCountAndIgnoresTreasureCount() throws Exception {
        var json = JsonParser.parseString("{\"question\":\"https://example.com/question.png\",\"fish\":6,\"chest\":5}").getAsJsonObject();
        assertEquals("6", FishGameAPI.readAnswer(json));
    }

    @Test
    void acceptsZeroFishCount() throws Exception {
        var json = JsonParser.parseString("{\"question\":\"https://example.com/question.png\",\"fish\":0,\"chest\":5}").getAsJsonObject();
        assertEquals("0", FishGameAPI.readAnswer(json));
    }

    @Test
    void doesNotRequireTreasureCount() throws Exception {
        var json = JsonParser.parseString("{\"question\":\"https://example.com/question.png\",\"fish\":3}").getAsJsonObject();
        assertEquals("3", FishGameAPI.readAnswer(json));
    }

    @Test
    void rejectsResponseWithoutFishCount() {
        var json = JsonParser.parseString("{\"question\":\"https://example.com/question.png\",\"chest\":5}").getAsJsonObject();
        assertThrows(java.io.IOException.class, () -> FishGameAPI.readAnswer(json));
    }
}
