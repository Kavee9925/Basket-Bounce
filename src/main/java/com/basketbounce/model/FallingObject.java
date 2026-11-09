package com.basketbounce.model;

import java.awt.*;
import java.awt.geom.Ellipse2D;

public class FallingObject {
    public enum Kind {
        HEART, STAR, COIN, GIFT, CUPCAKE, BOMB
    }
    private final Kind kind;
    private double x, y, vy, phase;
    private final int size;
    private boolean active = true;

    public FallingObject(Kind kind, double x, double y, double vy, int size) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.vy = vy;
        this.size = size;
    }

    public void update(double dt) {
        y += vy * dt;
        phase += dt * 5;
    }

    public void draw(Graphics2D g) {
        int s = size;
        float bob = (float) Math.sin(phase) * 2;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int px = (int) x, py = (int)(y + bob);
        switch (kind) {
            case HEART -> {
                g.setColor(new Color(255, 75, 119));
                g.fillOval(px - s / 2, py - s / 3, s / 2, s / 2);
                g.fillOval(px, py - s / 3, s / 2, s / 2);
                Polygon p = new Polygon(new int[] {
                    px - s / 2, px + s / 2, px
                }, new int[] {
                    py, py, py + s / 2
                }, 3);
                g.fillPolygon(p);
                g.setColor(new Color(255, 190, 210));
                g.drawLine(px - 5, py - 3, px - 2, py - 7);
            }
            case STAR -> {
                g.setColor(new Color(255, 211, 66));
                Polygon p = new Polygon();
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2 + i * Math.PI / 5;
                    int r = i % 2 == 0 ? s / 2 : s / 4;
                    p.addPoint(px + (int)(Math.cos(a) * r), py + (int)(Math.sin(a) * r));
                }
                g.fillPolygon(p);
                g.setColor(new Color(255, 246, 180));
                g.drawPolygon(p);
            }
            case COIN -> {
                g.setColor(new Color(209, 139, 19));
                g.fillOval(px - s / 2 + 2, py - s / 2 + 2, s - 4, s - 4);
                g.setColor(new Color(255, 202, 55));
                g.fillOval(px - s / 2, py - s / 2, s - 4, s - 4);
                g.setColor(new Color(255, 239, 151));
                g.drawOval(px - s / 3, py - s / 3, 2 * s / 3, 2 * s / 3);
                g.setFont(new Font("SansSerif", Font.BOLD, s / 2));
                g.drawString("$", px - 4, py + 6);
            }
            case GIFT -> {
                g.setColor(new Color(236, 75, 105));
                g.fillRoundRect(px - s / 2, py - s / 3, s, s * 2 / 3, 7, 7);
                g.setColor(new Color(255, 210, 65));
                g.fillRect(px - 3, py - s / 3, 6, s * 2 / 3);
                g.fillRect(px - s / 2, py - 3, s, 6);
                g.drawOval(px - 10, py - s / 2, 12, 10);
                g.drawOval(px - 1, py - s / 2, 12, 10);
            }
            case CUPCAKE -> {
                g.setColor(new Color(255, 184, 205));
                g.fillRoundRect(px - s / 2 + 3, py - s / 4, s - 6, s / 2, 8, 8);
                g.setColor(new Color(255, 226, 164));
                Polygon wrapper = new Polygon(new int[] {
                    px - s / 2 + 5, px + s / 2 - 5, px + s / 3, px - s / 3
                }, new int[] {
                    py + s / 5, py + s / 5, py + s / 2, py + s / 2
                }, 4);
                g.fillPolygon(wrapper);
                g.setColor(new Color(255, 245, 248));
                g.fillOval(px - s / 2 + 4, py - s / 2, s - 8, s / 2);
                g.setColor(new Color(255, 110, 145));
                g.fillOval(px - 3, py - s / 2 - 4, 7, 7);
                g.setColor(new Color(255, 255, 255, 210));
                g.fillOval(px - 10, py - s / 3, 4, 4);
            }
            case BOMB -> {
                g.setColor(new Color(35, 39, 53));
                g.fillOval(px - s / 2, py - s / 2, s, s);
                g.setColor(new Color(244, 116, 61));
                g.setStroke(new BasicStroke(3));
                g.drawLine(px + 5, py - s / 2 + 2, px + 12, py - s / 2 - 7);
                g.setColor(new Color(255, 205, 75));
                g.fillOval(px + 9, py - s / 2 - 11, 6, 6);
                g.setColor(new Color(120, 130, 150));
                g.drawArc(px - s / 4, py - s / 4, s / 2, s / 3, 10, 150);
            }
        }
    }

    public Rectangle bounds() {
        return new Rectangle((int) x - size / 2, (int) y - size / 2, size, size);
    }

    public Kind kind() {
        return kind;
    }

    public boolean active() {
        return active;
    }

    public void deactivate() {
        active = false;
    }

    public double y() {
        return y;
    }
}
