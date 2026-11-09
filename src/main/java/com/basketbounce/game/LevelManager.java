package com.basketbounce.game;

import com.basketbounce.model.WeatherType;

/** Ten-stage difficulty curve. Targets are cumulative across the run. */
public class LevelManager {
    private int level = 1;
    private static final int[] TARGET = {
        300, 700, 1200, 1800, 2500, 3300, 4200, 5200, 6300, 7500
    };
    private static final String[] NAMES = {
        "Sunny Garden",
        "Rainy Town",
        "Snowy Valley",
        "Stormy Sky",
        "Ultimate Bounce",
        "Windy Cliffs",
        "Midnight City",
        "Autumn Park",
        "Desert Dunes",
        "Aurora Kingdom"
    };
    // Level 1 is deliberately slow. Speed rises steadily without an abrupt jump.
    private static final double[] FALL_SPEED = {
        80, 82, 98, 116, 136, 158, 182, 208, 236, 266
    };
    private static final int[] SPAWN_MILLIS = {
        2200, 2050, 1900, 1750, 1600, 1475, 1350, 1225, 1125, 1025
    };
    private static final double[] BOMB_CHANCE = {
        .035, .045, .055, .065, .075, .085, .095, .105, .115, .125
    };
    private static final WeatherType[] WEATHER = {
        WeatherType.SUNNY,
        WeatherType.RAINY,
        WeatherType.SNOWY,
        WeatherType.STORMY,
        WeatherType.FOGGY,
        WeatherType.WINDY,
        WeatherType.NIGHT,
        WeatherType.AUTUMN,
        WeatherType.DESERT,
        WeatherType.AURORA
    };
    public int level() {
        return level;
    }

    public int target() {
        return TARGET[level - 1];
    }

    public String name() {
        return NAMES[level - 1];
    }

    public double speed() {
        return FALL_SPEED[level - 1];
    }

    public int spawnMillis() {
        return SPAWN_MILLIS[level - 1];
    }

    public double bombChance() {
        return BOMB_CHANCE[level - 1];
    }

    public WeatherType weather() {
        return WEATHER[level - 1];
    }

    public boolean complete(int score) {
        return score >= target();
    }

    public boolean next() {
        if (level < TARGET.length) {
            level++;
            return true;
        }
        return false;
    }

    public void reset() {
        level = 1;
    }
}
