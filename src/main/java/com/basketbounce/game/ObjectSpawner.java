package com.basketbounce.game;

import com.basketbounce.model.FallingObject;

import java.util.List;
import java.util.Random;

public class ObjectSpawner {
    private final Random random = new Random();
    private double elapsed;

    public void update(double dt, int width, LevelManager levels, List<FallingObject> out) {
        elapsed += dt * 1000.0;
        if (elapsed < levels.spawnMillis()) return;
        elapsed = 0;
        double r = random.nextDouble();
        double bombChance = levels.bombChance();
        FallingObject.Kind kind;
        if (r < bombChance) kind = FallingObject.Kind.BOMB;
        else if (r < bombChance + .20) kind = FallingObject.Kind.HEART;
        else if (r < bombChance + .40) kind = FallingObject.Kind.STAR;
        else if (r < bombChance + .74) kind = FallingObject.Kind.COIN;
        else if (r < bombChance + .90) kind = FallingObject.Kind.GIFT;
        else kind = FallingObject.Kind.CUPCAKE;
        int safeWidth = Math.max(1, width - 100);
        int x = 50 + random.nextInt(safeWidth);
        double fallSpeed = levels.speed() * (.88 + random.nextDouble() * .24);
        out.add(new FallingObject(kind, x, -35, fallSpeed, (kind == FallingObject.Kind.GIFT || kind == FallingObject.Kind.CUPCAKE) ? 37 : 32));
    }

    public void reset() {
        elapsed = 0;
    }
}
