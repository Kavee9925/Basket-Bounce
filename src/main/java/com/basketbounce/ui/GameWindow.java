package com.basketbounce.ui;

import com.basketbounce.api.*;
import com.basketbounce.game.*;
import com.basketbounce.model.*;
import com.basketbounce.storage.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class GameWindow extends JFrame {
    private final SaveManager saves = new SaveManager();
    private final GamePanel panel = new GamePanel();
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final ExecutorService apiExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fish-api");
        t.setDaemon(true);
        return t;
    });
    private ScheduledExecutorService clock;
    private Question pending;
    private boolean left, right;
    private int lastLevel = 1;
    private boolean sound = true;
    private final AudioManager audio = new AudioManager();

    public GameWindow() {
        super("Basket Bounce");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                audio.close();
                apiExecutor.shutdownNow();
            }
        });
        setSize(1000, 680);
        setMinimumSize(new Dimension(850, 600));
        setLocationRelativeTo(null);
        root.add(panel, "game");
        setContentPane(root);
        installGameKeyBindings();
        panel.setFocusable(true);
        panel.requestFocusInWindow();
    }

    private void installGameKeyBindings() {
        // Swing key bindings work even when a child component owns keyboard focus.
        bindKey("pressed LEFT", "move-left-pressed", () -> left = true);
        bindKey("released LEFT", "move-left-released", () -> left = false);
        bindKey("pressed A", "a-pressed", () -> left = true);
        bindKey("released A", "a-released", () -> left = false);
        bindKey("pressed RIGHT", "move-right-pressed", () -> right = true);
        bindKey("released RIGHT", "move-right-released", () -> right = false);
        bindKey("pressed D", "d-pressed", () -> right = true);
        bindKey("released D", "d-released", () -> right = false);
        bindKey("pressed ESCAPE", "pause-game", () -> panel.escape());
    }

    private void bindKey(String stroke, String name, Runnable action) {
        panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(stroke), name);
        panel.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void start() {
        panel.newGame();
        panel.state = GameState.PLAYING;
        panel.requestFocusInWindow();
        if (clock != null) clock.shutdownNow();
        clock = Executors.newSingleThreadScheduledExecutor();
        clock.scheduleAtFixedRate(() -> {
            if (panel.state == GameState.PLAYING) requestQuestion();
        }, 15, 15, TimeUnit.SECONDS);
    }

    private void requestQuestion() {
        if (panel.questionLoading || panel.state != GameState.PLAYING) return;
        panel.questionLoading = true;
        apiExecutor.submit(() -> {
            try {
                Question q = new FishGameAPI().fetch();
                SwingUtilities.invokeLater(() -> {
                    if (panel.state == GameState.PLAYING) {
                        pending = q;
                        panel.state = GameState.BONUS_QUESTION;
                    }
                    panel.questionLoading = false;
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    panel.questionLoading = false;
                    panel.apiMessage = "Bonus question unavailable (" + ex.getMessage() + ")";
                    panel.repaint();
                });
            }
        });
    }

    private void finish() {
        if (clock != null) {
            clock.shutdownNow();
            clock = null;
        }
        saves.save(panel.score.score(), panel.level.level());
        lastLevel = panel.level.level();
        panel.state = panel.level.level() == 10 && panel.level.complete(panel.score.score()) ? GameState.VICTORY : GameState.GAME_OVER;
        panel.repaint();
    }

    private void menu() {
        panel.state = GameState.MENU;
        panel.repaint();
        panel.requestFocusInWindow();
    }

    private void drawButton(Graphics2D g, String text, int x, int y, int w, int h, Color c) {
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        b.setColor(new Color(0, 0, 0, 58));
        b.fillRoundRect(x + 3, y + 5, w, h, 18, 18);
        b.setPaint(new GradientPaint(x, y, c.brighter(), x, y + h, c.darker()));
        b.fillRoundRect(x, y, w, h, 18, 18);
        b.setColor(new Color(255, 255, 255, 95));
        b.setStroke(new BasicStroke(1.2f));
        b.drawRoundRect(x + 1, y + 1, w - 2, h - 2, 17, 17);
        b.setColor(new Color(255, 255, 255, 35));
        b.drawLine(x + 18, y + 5, x + w - 18, y + 5);
        b.setColor(Color.WHITE);
        b.setFont(new Font("SansSerif", Font.BOLD, 18));
        center(b, text, x + w / 2, y + h / 2 + 6);
        b.dispose();
    }

    private void center(Graphics2D g, String s, int x, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, x - fm.stringWidth(s) / 2, y);
    }
    private class GamePanel extends JPanel {
        GameState state = GameState.MENU;
        final Basket basket = new Basket();
        final ScoreManager score = new ScoreManager();
        final LifeManager lives = new LifeManager();
        final LevelManager level = new LevelManager();
        final ObjectSpawner spawner = new ObjectSpawner();
        final List<FallingObject> objects = new ArrayList<>();
        final Random random = new Random();
        long last = System.nanoTime(), lastQuestionNanos = System.nanoTime();
        boolean questionLoading = false;
        String apiMessage = "";
        double anim = 0;
        int lightning = 0;
        String guestName = "Guest Player";
        GamePanel() {
            setBackground(new Color(18, 28, 52));
            new javax.swing.Timer(16, e -> {
                tick();
                repaint();
            }).start();
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    click(e.getX(), e.getY());
                }
            });
        }
        void newGame() {
            score.reset();
            lives.reset();
            level.reset();
            objects.clear();
            spawner.reset();
            left = false;
            right = false;
            basket.keys(false, false);
            lastLevel = 1;
            apiMessage = "";
            last = System.nanoTime();
        }
        void escape() {
            if (state == GameState.PLAYING) state = GameState.PAUSED;
            else if (state == GameState.PAUSED) state = GameState.PLAYING;
            repaint();
        }
        void tick() {
            long now = System.nanoTime();
            double dt = Math.min(.04, (now - last) / 1e9);
            last = now;
            anim += dt;
            basket.keys(left, right);
            if (state != GameState.PLAYING) return;
            basket.update(dt, getWidth(), getHeight());
            spawner.update(dt, getWidth(), level, objects);
            Rectangle basketBounds = basket.bounds();
            Iterator<FallingObject> it = objects.iterator();
            while (it.hasNext()) {
                FallingObject object = it.next();
                object.update(dt);
                if (object.bounds().intersects(basketBounds)) {
                    object.deactivate();
                    if (object.kind() == FallingObject.Kind.BOMB) {
                        finish();
                        return;
                    } else {
                        score.add(object.kind());
                        if (sound) audio.playCollectible();
                    }
                    it.remove();
                    continue;
                }
                if (object.y() > getHeight() + 40) {
                    // Only missed collectible objects cost a life; bombs are safely ignored.
                    if (object.kind() != FallingObject.Kind.BOMB && lives.lose()) {
                        finish();
                        return;
                    }
                    it.remove();
                }
            }
            if (level.complete(score.score())) {
                if (level.level() == 10) {
                    finish();
                    return;
                }
                state = GameState.LEVEL_COMPLETE;
                left = false;
                right = false;
                basket.keys(false, false);
            }
        }
        void click(int x, int y) {
            int cx = getWidth() / 2;
            if (state == GameState.MENU) {
                double scale = Math.min(getWidth() / 1000.0, getHeight() / 680.0);
                double ox = (getWidth() - 1000 * scale) / 2.0, oy = (getHeight() - 680 * scale) / 2.0;
                x = (int)((x - ox) / scale);
                y = (int)((y - oy) / scale);
                if (y >= 270 && y <= 325) start();
                else if (y >= 340 && y <= 388) {
                    state = GameState.LEADERBOARD;
                } else if (y >= 402 && y <= 450) {
                    state = GameState.HELP;
                } else if (y >= 464 && y <= 512) {
                    state = GameState.PROFILE;
                } else if (y >= 526 && y <= 574) {
                    state = GameState.SETTINGS;
                }
            } else if (state == GameState.PAUSED) {
                if (y >= 270 && y <= 320) state = GameState.PLAYING;
                else if (y >= 335 && y <= 385) start();
                else if (y >= 400 && y <= 450) menu();
            } else if (state == GameState.LEVEL_COMPLETE) {
                if (y >= 270 && y <= 325) {
                    if (level.next()) {
                        objects.clear();
                        spawner.reset();
                        last = System.nanoTime();
                        state = GameState.PLAYING;
                    } else finish();
                }
            } else if (state == GameState.GAME_OVER || state == GameState.VICTORY) {
                if (y >= 400 && y <= 455) start();
                else if (y >= 470 && y <= 525) menu();
            } else if (state == GameState.PROFILE || state == GameState.LEADERBOARD || state == GameState.HELP || state == GameState.SETTINGS) {
                if (y > getHeight() - 90) menu();
                else if (state == GameState.PROFILE && y > 250 && y < 315) {
                    String n = JOptionPane.showInputDialog(this, "Enter guest display name:", saves.profile().playerName);
                    saves.rename(n);
                } else if (state == GameState.SETTINGS && y >= 230 && y <= 280) {
                    sound = !sound;
                }
            } else if (state == GameState.BONUS_QUESTION) {
                if (y >= 490 && y <= 560) {
                    answerQuestion();
                }
            }
            requestFocusInWindow();
            repaint();
        }
        void answerQuestion() {
            if (pending == null) return;
            String answer = JOptionPane.showInputDialog(this, "Enter the two values in order: fish, chest (example: 6,5):");
            if (answer == null) return;
            if (normalizePuzzleAnswer(answer).equals(normalizePuzzleAnswer(pending.answer()))) {
                boolean gainedLife = lives.reward();
                if (!gainedLife) score.addBonus(150);
                JOptionPane.showMessageDialog(this, gainedLife ? "Correct! +1 life" : "Correct! Maximum lives reached. +150 points");
            } else JOptionPane.showMessageDialog(this, "Incorrect answer. No reward. Enter fish value first, then chest value.");
            pending = null;
            state = GameState.PLAYING;
        }
        String normalizePuzzleAnswer(String value) {
            return value == null ? "" : value.trim().replaceAll("[^0-9]+", ",").replaceAll("^,|,$", "");
        }
        @Override
        protected void paintComponent(Graphics gg) {
            super.paintComponent(gg);
            Graphics2D g = (Graphics2D) gg.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (state == GameState.PLAYING || state == GameState.PAUSED || state == GameState.BONUS_QUESTION || state == GameState.LEVEL_COMPLETE) {
                drawWorld(g, w, h);
                if (state == GameState.PAUSED) overlay(g, "GAME PAUSED", new String[] {
                    "RESUME", "RESTART", "MAIN MENU"
                });
                if (state == GameState.BONUS_QUESTION) drawQuestion(g, w, h);
                if (state == GameState.LEVEL_COMPLETE) overlay(g, "LEVEL COMPLETE!", new String[] {
                    "CONTINUE"
                });
            } else if (state == GameState.MENU) drawMenu(g, w, h);
            else if (state == GameState.GAME_OVER || state == GameState.VICTORY) drawResult(g, w, h);
            else drawInfo(g, w, h);
            g.dispose();
        }
        void drawWorld(Graphics2D g, int w, int h) {
            WeatherType weather = level.weather();
            Color top = switch (weather) {
                case SUNNY -> new Color(91, 190, 245);
                case RAINY -> new Color(74, 112, 153);
                case SNOWY -> new Color(150, 190, 220);
                case STORMY -> new Color(43, 54, 82);
                case FOGGY -> new Color(133, 151, 157);
                case WINDY -> new Color(90, 160, 177);
                case NIGHT -> new Color(13, 20, 56);
                case AUTUMN -> new Color(190, 119, 67);
                case DESERT -> new Color(231, 166, 83);
                case AURORA -> new Color(22, 55, 88);
            };
            Color bottom = switch (weather) {
                case SUNNY -> new Color(191, 231, 161);
                case RAINY -> new Color(103, 147, 155);
                case SNOWY -> new Color(224, 239, 248);
                case STORMY -> new Color(76, 88, 113);
                case FOGGY -> new Color(194, 204, 194);
                case WINDY -> new Color(185, 213, 190);
                case NIGHT -> new Color(34, 42, 83);
                case AUTUMN -> new Color(116, 78, 54);
                case DESERT -> new Color(245, 211, 139);
                case AURORA -> new Color(35, 106, 111);
            };
            g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(255, 255, 255, 65));
            for (int i = 0; i < 7; i++) {
                int x = (int)((i * 177 + anim * 10) % (w + 130)) - 60;
                g.fillOval(x, 85 + (i % 3) * 45, 100, 35);
                g.fillOval(x + 25, 72 + (i % 3) * 45, 65, 40);
            }
            if (weather == WeatherType.RAINY || weather == WeatherType.STORMY) {
                g.setColor(new Color(210, 232, 255, 115));
                for (int i = 0; i < 95; i++) {
                    int x = (i * 83 + (int)(anim * 180)) % w, y = (i * 47 + (int)(anim * 450)) % h;
                    g.drawLine(x, y, x - 5, y + 15);
                }
            }
            if (weather == WeatherType.SNOWY) {
                g.setColor(new Color(255, 255, 255, 200));
                for (int i = 0; i < 55; i++) {
                    int x = (i * 71 + (int)(Math.sin(anim + i) * 20)) % w, y = (i * 53 + (int)(anim * 35)) % h;
                    g.fillOval(x, y, 5, 5);
                }
            }
            if (weather == WeatherType.STORMY && ((int)(anim * 2) % 8 == 0)) {
                g.setColor(new Color(255, 255, 220, 35));
                g.fillRect(0, 0, w, h);
            }
            if (weather == WeatherType.FOGGY) {
                for (int i = 0; i < 5; i++) {
                    int fx = (int)((i * 230 + anim * 9) % (w + 260)) - 130;
                    g.setColor(new Color(235, 242, 235, 42));
                    g.fillOval(fx, 125 + i * 92, 310, 70);
                }
            }
            if (weather == WeatherType.WINDY) {
                g.setColor(new Color(235, 250, 255, 100));
                for (int i = 0; i < 32; i++) {
                    int x = (int)((i * 61 + anim * 150) % (w + 100)) - 50;
                    int y = (i * 37) % Math.max(1, h - 100);
                    g.drawLine(x, y, x + 28, y - 3);
                }
            }
            if (weather == WeatherType.NIGHT) {
                g.setColor(new Color(255, 246, 190, 220));
                g.fillOval(w - 150, 92, 62, 62);
                g.setColor(new Color(255, 255, 255, 205));
                for (int i = 0; i < 70; i++) {
                    int sx = (i * 97 + 31) % w, sy = (i * 53 + 17) % (Math.max(1, h - 100));
                    g.fillOval(sx, sy, 2 + (i % 2), 2 + (i % 2));
                }
            }
            if (weather == WeatherType.AUTUMN) {
                for (int i = 0; i < 38; i++) {
                    int x = (int)((i * 79 + Math.sin(anim * 1.4 + i) * 38) % (w + 20));
                    int y = (int)((i * 59 + anim * (35 + i % 4 * 8)) % (h + 20));
                    g.setColor(i % 2 == 0 ? new Color(224, 111, 43, 190) : new Color(246, 184, 65, 190));
                    g.fillOval(x, y, 8, 5);
                }
            }
            if (weather == WeatherType.DESERT) {
                g.setColor(new Color(255, 235, 178, 115));
                for (int i = 0; i < 60; i++) {
                    int x = (int)((i * 43 + anim * 90) % (w + 20));
                    int y = (i * 31 + (int)(anim * 23)) % Math.max(1, h - 90);
                    g.fillOval(x, y, 3, 2);
                }
            }
            if (weather == WeatherType.AURORA) {
                for (int i = 0; i < 4; i++) {
                    g.setColor(new Color(i % 2 == 0 ? 70 : 80, 220, 190, 28));
                    int yy = 95 + i * 48 + (int)(Math.sin(anim + i) * 14);
                    g.fillRoundRect(0, yy, w, 32, 30, 30);
                }
            }
            g.setColor(new Color(14, 27, 48, 150));
            g.fillRoundRect(14, 10, w - 28, 62, 20, 20);
            g.setColor(new Color(255, 255, 255, 45));
            g.drawRoundRect(14, 10, w - 28, 62, 20, 20);
            g.setFont(new Font("SansSerif", Font.BOLD, 17));
            g.setColor(new Color(255, 255, 255, 235));
            g.drawString("♥  " + lives.lives() + " / 3", 30, 37);
            g.drawString("★  " + score.score(), 165, 37);
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            g.drawString("LEVEL " + level.level() + "  •  " + level.name(), w - 365, 37);
            g.setColor(new Color(255, 255, 255, 65));
            g.fillRoundRect(30, 49, 245, 8, 8, 8);
            g.setColor(new Color(255, 211, 73));
            g.fillRoundRect(30, 49, (int)(245 * Math.min(1, score.score() / (double) level.target())), 8, 8, 8);
            g.setColor(new Color(238, 245, 255, 210));
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString("TARGET " + level.target(), 285, 57);
            g.setColor(new Color(49, 115, 83, 145));
            g.fillRect(0, h - 42, w, 42);
            for (FallingObject o : objects) o.draw(g);
            basket.draw(g);
            g.setColor(new Color(255, 255, 255, 190));
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.drawString("A / ← MOVE LEFT      D / → MOVE RIGHT      ESC PAUSE", w / 2 - 205, h - 13);
            if (!apiMessage.isBlank()) {
                g.setColor(new Color(30, 40, 60, 180));
                g.fillRoundRect(w / 2 - 190, 75, 380, 35, 12, 12);
                g.setColor(Color.WHITE);
                g.drawString(apiMessage.length() > 55 ? apiMessage.substring(0, 52) + "..." : apiMessage, w / 2 - 178, 97);
            }
        }
        void drawMenu(Graphics2D g, int w, int h) {
            double scale = Math.min(w / 1000.0, h / 680.0);
            double ox = (w - 1000 * scale) / 2.0, oy = (h - 680 * scale) / 2.0;
            Graphics2D menu = (Graphics2D) g.create();
            menu.translate(ox, oy);
            menu.scale(scale, scale);
            drawMenuContent(menu, 1000, 680);
            menu.dispose();
        }
        void drawMenuContent(Graphics2D g, int w, int h) {
            g.setPaint(new GradientPaint(0, 0, new Color(17, 29, 65), w, h, new Color(69, 119, 153)));
            g.fillRect(0, 0, w, h);
            // Soft decorative bubbles create depth without distracting from the menu.
            for (int i = 0; i < 22; i++) {
                int size = 12 + (i % 4) * 9;
                int x = (int)((i * 97 + anim * (10 + i % 3 * 4)) % (w + size)) - size;
                int y = (i * 71) % h;
                g.setColor(new Color(255, 255, 255, 22 + (i % 3) * 7));
                g.fillOval(x, y, size, size);
            }
            g.setColor(new Color(8, 15, 35, 75));
            g.fillRoundRect(w / 2 - 285, 42, 570, 575, 38, 38);
            g.setColor(new Color(255, 255, 255, 30));
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(w / 2 - 285, 42, 570, 575, 38, 38);
            // Small floating game collectibles around the title.
            g.setColor(new Color(255, 211, 66));
            drawStar(g, w / 2 - 220, 118, 25);
            g.setColor(new Color(255, 90, 130));
            g.fillOval(w / 2 + 190, 94, 23, 23);
            g.fillOval(w / 2 + 210, 94, 23, 23);
            g.fillPolygon(new int[] {
                w / 2 + 190, w / 2 + 233, w / 2 + 211
            }, new int[] {
                108, 108, 136
            }, 3);
            g.setColor(new Color(255, 222, 112));
            g.setFont(new Font("SansSerif", Font.BOLD, 51));
            center(g, "BASKET BOUNCE", w / 2, 151);
            g.setColor(new Color(228, 240, 255));
            g.setFont(new Font("SansSerif", Font.PLAIN, 17));
            center(g, "Catch the Good. Dodge the Bad. Beat the Score!", w / 2, 190);
            g.setColor(new Color(150, 225, 205));
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            center(g, "GUEST MODE  •  YOUR SCORES SAVE ON THIS COMPUTER", w / 2, 239);
            int x = w / 2 - 137;
            drawButton(g, "PLAY AS GUEST", x, 270, 274, 55, new Color(35, 177, 137));
            drawButton(g, "LEADERBOARD", x, 340, 274, 48, new Color(64, 116, 190));
            drawButton(g, "HOW TO PLAY", x, 402, 274, 48, new Color(116, 100, 190));
            drawButton(g, "GUEST PROFILE", x, 464, 274, 48, new Color(194, 133, 67));
            drawButton(g, "SETTINGS", x, 526, 274, 48, new Color(76, 103, 132));
            g.setColor(new Color(225, 235, 250, 150));
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            center(g, "10 LEVELS  •  5 COLLECTIBLES  •  BONUS CHALLENGES", w / 2, h - 18);
        }
        void drawStar(Graphics2D g, int cx, int cy, int radius) {
            Polygon star = new Polygon();
            for (int i = 0; i < 10; i++) {
                double angle = -Math.PI / 2 + i * Math.PI / 5;
                int r = i % 2 == 0 ? radius : radius / 2;
                star.addPoint(cx + (int)(Math.cos(angle) * r), cy + (int)(Math.sin(angle) * r));
            }
            g.fillPolygon(star);
        }
        void overlay(Graphics2D g, String title, String[] buttons) {
            int w = getWidth(), h = getHeight();
            g.setColor(new Color(8, 14, 28, 190));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(30, 45, 75));
            g.fillRoundRect(w / 2 - 230, 150, 460, 390, 28, 28);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 34));
            center(g, title, w / 2, 220);
            int y = 270;
            for (String b : buttons) {
                drawButton(g, b, w / 2 - 115, y, 230, 48, new Color(58, 153, 137));
                y += 65;
            }
        }
        void drawQuestion(Graphics2D g, int w, int h) {
            g.setColor(new Color(7, 13, 25, 205));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(36, 54, 87));
            g.fillRoundRect(w / 2 - 255, 75, 510, 500, 28, 28);
            g.setColor(new Color(255, 219, 99));
            g.setFont(new Font("SansSerif", Font.BOLD, 30));
            center(g, "BONUS CHALLENGE", w / 2, 125);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.PLAIN, 16));
            center(g, "Find both values in the official Fish Game API puzzle", w / 2, 157);
            if (pending != null && pending.image() != null) {
                int iw = Math.min(330, pending.image().getWidth()), ih = Math.min(290, pending.image().getHeight());
                g.drawImage(pending.image(), w / 2 - iw / 2, 185, iw, ih, null);
            }
            drawButton(g, "ANSWER QUESTION", w / 2 - 125, 500, 250, 50, new Color(42, 173, 133));
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(new Color(220, 230, 245));
            center(g, "Enter fish value, then chest value • Correct answer: +1 life or +150 points", w / 2, 570);
        }
        void drawResult(Graphics2D g, int w, int h) {
            g.setPaint(new GradientPaint(0, 0, new Color(31, 45, 80), w, h, new Color(85, 114, 151)));
            g.fillRect(0, 0, w, h);
            boolean win = state == GameState.VICTORY;
            g.setColor(win ? new Color(255, 220, 100) : Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 48));
            center(g, win ? "YOU DID IT!" : "GAME OVER", w / 2, 155);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 22));
            center(g, "FINAL SCORE  " + score.score(), w / 2, 235);
            center(g, "HIGHEST SCORE  " + saves.profile().highestScore, w / 2, 280);
            center(g, "LEVEL REACHED  " + level.level(), w / 2, 325);
            drawButton(g, "PLAY AGAIN", w / 2 - 125, 400, 250, 52, new Color(44, 173, 132));
            drawButton(g, "MAIN MENU", w / 2 - 125, 470, 250, 52, new Color(72, 115, 180));
        }
        void drawInfo(Graphics2D g, int w, int h) {
            g.setColor(new Color(24, 38, 67));
            g.fillRect(0, 0, w, h);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 34));
            center(g, state.name().replace('_', ' '), w / 2, 75);
            g.setFont(new Font("SansSerif", Font.PLAIN, 17));
            int y = 130;
            if (state == GameState.PROFILE) {
                String[] lines = {
                    "Guest name: " + saves.profile().playerName,
                    "Highest score: " + saves.profile().highestScore,
                    "Highest level: " + saves.profile().highestLevel,
                    "Total games: " + saves.profile().totalGames,
                    "Total score: " + saves.profile().totalScore,
                    "Click the middle area to rename your guest profile."
                };
                for (String s : lines) {
                    center(g, s, w / 2, y);
                    y += 38;
                }
            } else if (state == GameState.LEADERBOARD) {
                center(g, "TOP GUEST SCORES (saved on this computer)", w / 2, y);
                y += 45;
                int rank = 1;
                for (SaveManager.Run r : saves.leaderboard().stream().limit(10).toList()) {
                    center(g, rank + ".  " + r.player() + "     " + r.score() + " pts     Level " + r.level() + "     " + r.date(), w / 2, y);
                    y += 30;
                    rank++;
                }
                if (rank == 1) center(g, "No completed games yet. Play a round to set the first score!", w / 2, y);
            } else if (state == GameState.HELP) {
                String[] lines = {
                    "Move the basket with A / LEFT and D / RIGHT.",
                    "Catch hearts, stars, coins, gifts and cupcakes to earn points.",
                    "Avoid bombs. Missing a good object costs one life.",
                    "You start with 3 lives. Maximum lives: 3.",
                    "Reach each target score to unlock the next level.",
                    "An official Fish API bonus puzzle appears every 15 seconds.",
                    "Correct answer: +1 life, or +150 points at full lives.",
                    "Press ESC to pause. Guest results save locally."
                };
                for (String s : lines) {
                    center(g, s, w / 2, y);
                    y += 35;
                }
            } else {
                center(g, "Sound effects: " + (sound ? "ON" : "OFF"), w / 2, 190);
                drawButton(g, "TOGGLE SOUND", w / 2 - 120, 230, 240, 48, new Color(70, 132, 170));
            }
            drawButton(g, "BACK TO MENU", w / 2 - 120, h - 80, 240, 48, new Color(54, 157, 132));
        }
    }
}
