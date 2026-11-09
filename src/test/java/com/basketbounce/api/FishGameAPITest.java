package com.basketbounce.api;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FishGameAPITest {
    @Test
    void readsOfficialFishAndChestAnswerFields() throws Exception {
        var json = JsonParser.parseString("{\"question\":\"https://example.com/question.png\",\"fish\":6,\"chest\":5}").getAsJsonObject();
        assertEquals("6,5", FishGameAPI.readAnswer(json));
    }
    @Test
    void supportsOlderSingleSolutionField() throws Exception {
        var json = JsonParser.parseString("{\"question\":\"https://example.com/question.png\",\"solution\":\"42\"}").getAsJsonObject();
        assertEquals("42", FishGameAPI.readAnswer(json));
    }
}
