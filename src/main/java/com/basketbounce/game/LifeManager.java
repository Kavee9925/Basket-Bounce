package com.basketbounce.game;
public class LifeManager {
    private int lives = 3;

    public int lives() {
        return lives;
    }

    public boolean lose() {
        if (lives > 0) lives--;
        return lives == 0;
    }

    public boolean reward() {
        if (lives < 3) {
            lives++;
            return true;
        }
        return false;
    }

    public void reset() {
        lives = 3;
    }
}
