package com.basketbounce.game;

import com.basketbounce.model.FallingObject;

public class ScoreManager {
    private int score;

    public int score() {
        return score;
    }

    public void add(FallingObject.Kind k) {
        score += switch (k) {
            case HEART -> 30;
            case STAR -> 50;
            case COIN -> 10;
            case GIFT -> 100;
            case CUPCAKE -> 75;
            case BOMB -> 0;
        };
    }

    public void addBonus(int n) {
        score += n;
    }

    public void reset() {
        score = 0;
    }
}
