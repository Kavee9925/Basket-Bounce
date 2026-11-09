package com.basketbounce;

import com.basketbounce.game.LevelManager;
import com.basketbounce.game.LifeManager;
import com.basketbounce.game.ScoreManager;
import com.basketbounce.model.FallingObject;
import com.basketbounce.model.WeatherType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameRulesTest {
    @Test
    void livesNeverExceedThree() {
        LifeManager lives = new LifeManager();
        assertEquals(3, lives.lives());
        assertFalse(lives.lose());
        assertEquals(2, lives.lives());
        assertTrue(lives.reward());
        assertEquals(3, lives.lives());
        assertFalse(lives.reward());
        assertEquals(3, lives.lives());
    }
    @Test
    void thirdMissEndsGame() {
        LifeManager lives = new LifeManager();
        assertFalse(lives.lose());
        assertFalse(lives.lose());
        assertTrue(lives.lose());
        assertEquals(0, lives.lives());
    }
    @Test
    void objectScoresDiffer() {
        ScoreManager score = new ScoreManager();
        score.add(FallingObject.Kind.COIN);
        score.add(FallingObject.Kind.STAR);
        score.add(FallingObject.Kind.HEART);
        score.add(FallingObject.Kind.GIFT);
        score.add(FallingObject.Kind.CUPCAKE);
        assertEquals(265, score.score());
    }
    @Test
    void allTenLevelsHaveUniqueWeatherAndProgressiveSpeed() {
        LevelManager levels = new LevelManager();
        WeatherType[] weather = new WeatherType[10];
        double previousSpeed = 0;
        for (int i = 0; i < 10; i++) {
            weather[i] = levels.weather();
            assertTrue(levels.speed() > previousSpeed);
            previousSpeed = levels.speed();
            if (i < 9) assertTrue(levels.next());
        }
        assertEquals(10, java.util.Arrays.stream(weather).distinct().count());
        assertFalse(levels.next());
    }
    @Test
    void levelOneIsSlowest() {
        LevelManager levels = new LevelManager();
        assertEquals(80, levels.speed(), 0.001);
        assertEquals(2200, levels.spawnMillis());
    }
}
