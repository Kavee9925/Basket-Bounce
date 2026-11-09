package com.basketbounce.game;

import java.awt.*;

public class Basket {
    private double x;
    private int y;
    private final int w = 112, h = 48;
    private boolean left, right;

    public Basket() {
        x = 500;
        y = 535;
    }

    public void keys(boolean l, boolean r) {
        left = l;
        right = r;
    }

    public void update(double dt, int width, int height) {
        if (left) x -= 440 * dt;
        if (right) x += 440 * dt;
        x = Math.max(w / 2.0 + 8, Math.min(width - w / 2.0 - 8, x));
        // Keep the basket near the bottom on both normal and maximized/full-screen windows.
        y = Math.max(170, height - 116);
    }

    public Rectangle bounds() {
        return new Rectangle((int) x - w / 2, y, w, h);
    }

    public void draw(Graphics2D g) {
        int a = (int) x;
        g.setColor(new Color(74, 45, 32));
        g.fillRoundRect(a - w / 2, y, w, h, 15, 15);
        g.setColor(new Color(188, 119, 61));
        g.fillRoundRect(a - w / 2 + 5, y + 5, w - 10, h - 11, 12, 12);
        g.setColor(new Color(245, 190, 115));
        g.setStroke(new BasicStroke(5));
        g.drawArc(a - w / 2, y - 8, w, 35, 180, 180);
        g.setColor(new Color(255, 220, 166));
        g.drawLine(a - w / 2 + 12, y + 14, a + w / 2 - 12, y + 14);
    }
}
