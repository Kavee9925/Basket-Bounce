package com.basketbounce.ui;

import com.basketbounce.api.*;
import com.basketbounce.game.*;
import com.basketbounce.model.*;
import com.basketbounce.storage.*;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.io.File;
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
    private volatile long lastApiCallNanos;
    private Question pending;
    private boolean left, right;
    private int lastLevel = 1;
    private boolean sound = true;
    private final AudioManager audio = new AudioManager();
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mongodb-storage");
        t.setDaemon(true);
        return t;
    });
    private volatile MongoDBService.UserAccount activeAccount;
    private volatile boolean sessionActive;
    private volatile MongoDBService.PlayerStats accountStats = new MongoDBService.PlayerStats(0, 1, 0, 0);

    public GameWindow() {
        super("Basket Bounce");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                audio.close();
                apiExecutor.shutdownNow();
                databaseExecutor.shutdown();
                MongoDBService.getInstance().close();
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
        for (char digit = '0'; digit <= '9'; digit++) {
            final char keyDigit = digit;
            bindKey("typed " + keyDigit, "fish-answer-" + keyDigit, () -> {
                if (panel.state == GameState.BONUS_QUESTION && panel.questionAnswer.length() < 3) {
                    panel.questionAnswer += keyDigit;
                    panel.repaint();
                }
            });
        }
        bindKey("pressed BACK_SPACE", "fish-answer-backspace", () -> {
            if (panel.state == GameState.BONUS_QUESTION && !panel.questionAnswer.isEmpty()) {
                panel.questionAnswer = panel.questionAnswer.substring(0, panel.questionAnswer.length() - 1);
                panel.repaint();
            }
        });
        bindKey("pressed ENTER", "fish-answer-submit", () -> {
            if (panel.state == GameState.BONUS_QUESTION) panel.answerQuestion();
        });
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
        lastApiCallNanos = System.nanoTime();
        clock = Executors.newSingleThreadScheduledExecutor();
        clock.scheduleAtFixedRate(() -> {
            if (panel.state == GameState.PLAYING
                    && !panel.questionLoading
                    && System.nanoTime() - lastApiCallNanos >= TimeUnit.SECONDS.toNanos(15)) {
                requestQuestion();
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
    }

    private void requestQuestion() {
        if (panel.questionLoading || panel.state != GameState.PLAYING
                || System.nanoTime() - lastApiCallNanos < TimeUnit.SECONDS.toNanos(15)) return;
        panel.questionLoading = true;
        lastApiCallNanos = System.nanoTime();
        apiExecutor.submit(() -> {
            try {
                Question q = new FishGameAPI().fetch();
                SwingUtilities.invokeLater(() -> {
                    if (panel.state == GameState.PLAYING) {
                        pending = q;
                        panel.questionAnswer = "";
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
        if (activeAccount == null) {
            saves.save(panel.score.score(), panel.level.level());
        } else {
            MongoDBService.UserAccount account = activeAccount;
            int finalScore = panel.score.score();
            int finalLevel = panel.level.level();
            databaseExecutor.submit(() -> {
                try {
                    MongoDBService.getInstance().saveGame(account, finalScore, finalLevel);
                    accountStats = MongoDBService.getInstance().getPlayerStats(account);
                    SwingUtilities.invokeLater(panel::repaint);
                } catch (Exception exception) {
                    System.err.println("Could not save game results to MongoDB: " + exception.getMessage());
                }
            });
        }
        lastLevel = panel.level.level();
        panel.state = panel.level.level() == 10 && panel.level.complete(panel.score.score()) ? GameState.VICTORY : GameState.GAME_OVER;
        panel.repaint();
    }

    private void menu() {
        panel.state = GameState.MENU;
        panel.repaint();
        panel.requestFocusInWindow();
    }

    private void loginFromMenu() {
        String username = panel.loginUsername.getText().trim();
        char[] password = panel.loginPassword.getPassword();
        if (username.isEmpty() || password.length == 0) {
            panel.loginStatus = "Please enter your username/email and password.";
            panel.repaint();
            java.util.Arrays.fill(password, '\0');
            return;
        }
        panel.loginStatus = "Signing in...";
        panel.repaint();
        new SwingWorker<MongoDBService.UserAccount, Void>() {
            @Override
            protected MongoDBService.UserAccount doInBackground() {
                return MongoDBService.getInstance().login(username, password);
            }

            @Override
            protected void done() {
                java.util.Arrays.fill(password, '\0');
                try {
                    setActiveAccount(get());
                    panel.loginStatus = "Login successful!";
                } catch (Exception exception) {
                    Throwable cause = exception.getCause();
                    panel.loginStatus = cause instanceof IllegalArgumentException
                            ? cause.getMessage() : "Cannot connect to MongoDB. Please try again.";
                }
                panel.repaint();
            }
        }.execute();
    }

    private void setActiveAccount(MongoDBService.UserAccount account) {
        activeAccount = account;
        sessionActive = true;
        panel.guestName = account.username();
        databaseExecutor.submit(() -> {
            try {
                accountStats = MongoDBService.getInstance().getPlayerStats(account);
                SwingUtilities.invokeLater(panel::repaint);
            } catch (Exception exception) {
                System.err.println("Could not load player profile: " + exception.getMessage());
            }
        });
        panel.repaint();
    }

    private void refreshProfile() {
        if (activeAccount == null) {
            saves.reload();
            panel.guestName = saves.profile().playerName;
            panel.repaint();
            return;
        }
        MongoDBService.UserAccount account = activeAccount;
        databaseExecutor.submit(() -> {
            try {
                accountStats = MongoDBService.getInstance().getPlayerStats(account);
                SwingUtilities.invokeLater(panel::repaint);
            } catch (Exception exception) {
                System.err.println("Could not refresh player profile: " + exception.getMessage());
                SwingUtilities.invokeLater(panel::repaint);
            }
        });
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

    private void drawCompactButton(Graphics2D g, String text, int x, int y, int w, int h, Color color) {
        Graphics2D button = (Graphics2D) g.create();
        button.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        button.setColor(new Color(0, 0, 0, 55));
        button.fillRoundRect(x + 2, y + 3, w, h, 14, 14);
        button.setPaint(new GradientPaint(x, y, color.brighter(), x, y + h, color.darker()));
        button.fillRoundRect(x, y, w, h, 14, 14);
        button.setColor(new Color(255, 255, 255, 90));
        button.drawRoundRect(x, y, w - 1, h - 1, 14, 14);
        button.setColor(Color.WHITE);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        center(button, text, x + w / 2, y + h / 2 + 4);
        button.dispose();
    }

    private void center(Graphics2D g, String s, int x, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, x - fm.stringWidth(s) / 2, y);
    }
    private class GamePanel extends JPanel {
        volatile GameState state = GameState.MENU;
        private final Image menuBackground = loadMenuImage("image 3.jpg");
        private final Image guestBackground = loadMenuImage("image 1.jpg");
        private final Image accountBackground = loadMenuImage("image 2.jpg");

        private Image loadMenuImage(String fileName) {
            try {
                java.net.URL resource = getClass().getResource("/Images/" + fileName);
                if (resource != null) return ImageIO.read(resource);
                File imageFile = new File("src/Images", fileName);
                if (imageFile.isFile()) return ImageIO.read(imageFile);
            } catch (Exception exception) {
                System.err.println("Could not load menu background " + fileName + ": " + exception.getMessage());
            }
            return null;
        }

        private void drawMenuBackground(Graphics2D g, Image image, int w, int h) {
            // Paint the background across the complete window. Drawing it directly
            // at the panel size keeps all three menu images visible without side bars.
            if (image == null) {
                g.setPaint(new GradientPaint(0, 0, new Color(17, 29, 65), w, h, new Color(69, 119, 153)));
                g.fillRect(0, 0, w, h);
                return;
            }
            g.drawImage(image, 0, 0, w, h, this);
        }
        final Basket basket = new Basket();
        final ScoreManager score = new ScoreManager();
        final LifeManager lives = new LifeManager();
        final LevelManager level = new LevelManager();
        final ObjectSpawner spawner = new ObjectSpawner();
        final List<FallingObject> objects = new ArrayList<>();
        final Random random = new Random();
        long last = System.nanoTime(), lastQuestionNanos = System.nanoTime();
        volatile boolean questionLoading = false;
        String apiMessage = "";
        String questionAnswer = "";
        double anim = 0;
        int lightning = 0;
        String guestName = "Guest Player";
        final JTextField loginUsername = new JTextField();
        final JPasswordField loginPassword = new JPasswordField();
        String loginStatus = "";
        GamePanel() {
            setLayout(null);
            setBackground(new Color(18, 28, 52));
            styleMenuInput(loginUsername);
            styleMenuInput(loginPassword);
            add(loginUsername);
            add(loginPassword);
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
        @Override
        public void doLayout() {
            double scale = Math.min(getWidth() / 1000.0, getHeight() / 680.0);
            double ox = (getWidth() - 1000 * scale) / 2.0;
            double oy = (getHeight() - 680 * scale) / 2.0;
            int fieldX = (int) Math.round(ox + 363 * scale);
            int fieldWidth = (int) Math.round(274 * scale);
            int fieldHeight = (int) Math.round(36 * scale);
            loginUsername.setBounds(fieldX, (int) Math.round(oy + 225 * scale), fieldWidth, fieldHeight);
            loginPassword.setBounds(fieldX, (int) Math.round(oy + 285 * scale), fieldWidth, fieldHeight);
            boolean menuVisible = state == GameState.MENU && !sessionActive;
            loginUsername.setVisible(menuVisible);
            loginPassword.setVisible(menuVisible);
        }

        private void styleMenuInput(JTextField field) {
            field.setFont(new Font("SansSerif", Font.PLAIN, 14));
            field.setForeground(new Color(245, 248, 255));
            field.setCaretColor(new Color(95, 190, 255));
            field.setBackground(new Color(20, 29, 48));
            field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(110, 145, 190), 1),
                    BorderFactory.createEmptyBorder(7, 10, 7, 10)));
            field.setOpaque(true);
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
                if (sessionActive) {
                    if (x >= 365 && x <= 635 && y >= 245 && y <= 290) {
                        start();
                    } else if (x >= 365 && x <= 635 && y >= 300 && y <= 345) {
                        state = GameState.PROFILE;
                    } else if (x >= 365 && x <= 635 && y >= 355 && y <= 400) {
                        state = GameState.LEADERBOARD;
                    } else if (x >= 220 && x <= 380 && y >= 425 && y <= 470) {
                        state = GameState.HELP;
                    } else if (x >= 420 && x <= 580 && y >= 425 && y <= 470) {
                        state = GameState.SETTINGS;
                    } else if (x >= 620 && x <= 780 && y >= 425 && y <= 470) {
                        activeAccount = null;
                        sessionActive = false;
                        loginStatus = "";
                        loginUsername.setText("");
                        loginPassword.setText("");
                        menu();
                    }
                } else if (x >= 363 && x <= 637 && y >= 340 && y <= 382) {
                    loginFromMenu();
                } else if (y >= 395 && y <= 437) {
                    if (x >= 330 && x <= 490) {
                        AccountDialogs.showCreateAccount(this, GameWindow.this::setActiveAccount);
                    } else if (x >= 510 && x <= 670) {
                        activeAccount = null;
                        sessionActive = true;
                        guestName = saves.profile().playerName;
                        loginStatus = "";
                    }
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
            } else if (state == GameState.PROFILE) {
                int buttonY = getHeight() - 65;
                if (y >= buttonY && y <= buttonY + 42) {
                    int buttonW = activeAccount == null ? 190 : 205;
                    int buttonGap = 12;
                    int totalW = activeAccount == null ? buttonW * 3 + buttonGap * 2 : buttonW * 2 + buttonGap;
                    int buttonX = (getWidth() - totalW) / 2;
                    if (x >= buttonX && x <= buttonX + buttonW) {
                        menu();
                    } else if (x >= buttonX + buttonW + buttonGap && x <= buttonX + (buttonW + buttonGap) * 2 - buttonGap) {
                        refreshProfile();
                    } else if (activeAccount == null && x >= buttonX + (buttonW + buttonGap) * 2
                            && x <= buttonX + (buttonW + buttonGap) * 2 + buttonW) {
                        AccountDialogs.showCreateAccount(this, GameWindow.this::setActiveAccount);
                    }
                }
            } else if (state == GameState.LEADERBOARD || state == GameState.HELP || state == GameState.SETTINGS) {
                if (y > getHeight() - 90) menu();
                else if (state == GameState.SETTINGS && y >= 230 && y <= 280) {
                    sound = !sound;
                }
            } else if (state == GameState.BONUS_QUESTION) {
                if (y >= 485 && y <= 565) answerQuestion();
            }
            requestFocusInWindow();
            repaint();
        }
        void answerQuestion() {
            if (pending == null || questionAnswer.isBlank()) return;
            if (normalizePuzzleAnswer(questionAnswer).equals(normalizePuzzleAnswer(pending.answer()))) {
                boolean gainedLife = lives.reward();
                if (!gainedLife) score.addBonus(150);
                apiMessage = gainedLife ? "Correct fish count! +1 life" : "Correct fish count! +150 points";
            } else {
                apiMessage = "Incorrect fish count. No reward this time.";
            }
            pending = null;
            questionAnswer = "";
            state = GameState.PLAYING;
            lastApiCallNanos = System.nanoTime();
            repaint();
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
            doLayout();
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
            Image background = !sessionActive
                    ? menuBackground
                    : (activeAccount == null ? guestBackground : accountBackground);
            drawMenuBackground(g, background, w, h);

            double scale = Math.min(w / 1000.0, h / 680.0);
            double ox = (w - 1000 * scale) / 2.0, oy = (h - 680 * scale) / 2.0;
            Graphics2D menu = (Graphics2D) g.create();
            menu.translate(ox, oy);
            menu.scale(scale, scale);
            drawMenuContent(menu, 1000, 680);
            menu.dispose();
        }
        void drawMenuContent(Graphics2D g, int w, int h) {
            if (sessionActive) {
                drawHomeMenuContent(g, w, h);
                return;
            }
            g.setColor(new Color(230, 239, 255));
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            center(g, "Username or Email", w / 2, 218);
            center(g, "Password", w / 2, 278);
            drawButton(g, "LOGIN", w / 2 - 137, 340, 274, 42, new Color(35, 139, 224));
            drawButton(g, "CREATE ACCOUNT", w / 2 - 170, 395, 160, 42, new Color(28, 43, 68));
            drawButton(g, "PLAY AS GUEST", w / 2 + 10, 395, 160, 42, new Color(28, 43, 68));
            if (!loginStatus.isBlank()) {
                g.setColor(loginStatus.startsWith("Login successful")
                        ? new Color(120, 245, 170) : new Color(255, 205, 170));
                g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                center(g, loginStatus, w / 2, 470);
            }
            g.setColor(new Color(225, 235, 250, 150));
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            center(g, "10 LEVELS  •  5 COLLECTIBLES  •  BONUS CHALLENGES", w / 2, h - 18);
        }
        void drawHomeMenuContent(Graphics2D g, int w, int h) {
            drawButton(g, "PLAY GAME", w / 2 - 135, 245, 270, 45, new Color(35, 139, 224));
            drawButton(g, "PROFILE", w / 2 - 135, 300, 270, 45, new Color(28, 43, 68));
            drawButton(g, "LEADERBOARD", w / 2 - 135, 355, 270, 45, new Color(28, 43, 68));
            drawCompactButton(g, "HOW TO PLAY", w / 2 - 280, 425, 160, 45, new Color(54, 78, 120));
            drawCompactButton(g, "SETTINGS", w / 2 - 80, 425, 160, 45, new Color(35, 85, 145));
            drawCompactButton(g, "LOGOUT", w / 2 + 120, 425, 160, 45, new Color(165, 75, 85));
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
            g.fillRoundRect(w / 2 - 255, 65, 510, 530, 28, 28);
            g.setColor(new Color(255, 219, 99));
            g.setFont(new Font("SansSerif", Font.BOLD, 30));
            center(g, "BONUS CHALLENGE", w / 2, 112);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.PLAIN, 16));
            center(g, "Count the fish in the official Fish Game API puzzle", w / 2, 143);
            if (pending != null && pending.image() != null) {
                int sourceWidth = pending.image().getWidth();
                int sourceHeight = pending.image().getHeight();
                double imageScale = Math.min(330.0 / sourceWidth, 245.0 / sourceHeight);
                int imageWidth = (int) (sourceWidth * imageScale);
                int imageHeight = (int) (sourceHeight * imageScale);
                g.drawImage(pending.image(), w / 2 - imageWidth / 2, 158, imageWidth, imageHeight, null);
            }

            g.setColor(new Color(220, 230, 245));
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            center(g, "YOUR FISH COUNT", w / 2, 433);
            g.setColor(new Color(16, 27, 48));
            g.fillRoundRect(w / 2 - 105, 447, 210, 48, 13, 13);
            g.setColor(new Color(103, 211, 183));
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(w / 2 - 105, 447, 210, 48, 13, 13);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 25));
            center(g, questionAnswer.isEmpty() ? "_" : questionAnswer, w / 2, 479);
            drawButton(g, "SUBMIT ANSWER", w / 2 - 125, 510, 250, 48, new Color(42, 173, 133));
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(new Color(220, 230, 245));
            center(g, "Type the number and press ENTER  •  Correct: +1 life or +150 points", w / 2, 578);
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
            int highestScore = activeAccount == null ? saves.profile().highestScore : accountStats.highestScore();
            center(g, "HIGHEST SCORE  " + highestScore, w / 2, 280);
            center(g, "LEVEL REACHED  " + level.level(), w / 2, 325);
            drawButton(g, "PLAY AGAIN", w / 2 - 125, 400, 250, 52, new Color(44, 173, 132));
            drawButton(g, "MAIN MENU", w / 2 - 125, 470, 250, 52, new Color(72, 115, 180));
        }
        void drawPlayerProfile(Graphics2D g, int w, int h) {
            g.setPaint(new GradientPaint(0, 0, new Color(8, 18, 48), w, h, new Color(18, 43, 86)));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(255, 255, 255, 15));
            for (int i = 0; i < 34; i++) {
                int px = (i * 137 + 29) % w;
                int py = (i * 83 + 17) % h;
                g.fillOval(px, py, 2 + (i % 3), 2 + (i % 3));
            }

            int panelX = 34, panelY = 22, panelW = w - 68, panelH = h - 44;
            g.setColor(new Color(5, 13, 35, 218));
            g.fillRoundRect(panelX, panelY, panelW, panelH, 26, 26);
            g.setColor(new Color(72, 143, 226, 150));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(panelX, panelY, panelW, panelH, 26, 26);

            g.setFont(new Font("SansSerif", Font.BOLD, 29));
            g.setColor(new Color(255, 218, 91));
            center(g, "PLAYER PROFILE", w / 2, 62);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(new Color(205, 222, 248));
            center(g, "BASKET BOUNCE  •  PLAYER RECORD & LIFETIME STATISTICS", w / 2, 84);

            boolean registered = activeAccount != null;
            String playerName = registered ? activeAccount.username() : saves.profile().playerName;
            String subline = registered ? activeAccount.email() : "Guest session • Progress saved on this computer";
            if (playerName == null || playerName.isBlank()) playerName = "Guest Player";
            int avatarX = 62, avatarY = 106;
            g.setColor(new Color(24, 151, 225));
            g.fillOval(avatarX, avatarY, 58, 58);
            g.setColor(new Color(139, 218, 255));
            g.setStroke(new BasicStroke(2f));
            g.drawOval(avatarX, avatarY, 58, 58);
            g.setFont(new Font("SansSerif", Font.BOLD, 25));
            g.setColor(Color.WHITE);
            center(g, playerName.substring(0, 1).toUpperCase(Locale.ROOT), avatarX + 29, avatarY + 38);
            g.setFont(new Font("SansSerif", Font.BOLD, 17));
            g.setColor(new Color(239, 246, 255));
            g.drawString(playerName, 137, 128);

            String status = registered ? "REGISTERED" : "GUEST SESSION";
            Color statusColor = registered ? new Color(45, 174, 132) : new Color(194, 145, 45);
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            int badgeW = g.getFontMetrics().stringWidth(status) + 24;
            g.setColor(statusColor);
            g.fillRoundRect(137, 139, badgeW, 22, 11, 11);
            g.setColor(Color.WHITE);
            g.drawString(status, 149, 154);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(new Color(192, 211, 239));
            g.drawString(subline == null ? "" : subline, 137, 180);

            int highestScore = registered ? accountStats.highestScore() : saves.profile().highestScore;
            int highestLevel = registered ? accountStats.highestLevel() : saves.profile().highestLevel;
            int totalGames = registered ? accountStats.totalGames() : saves.profile().totalGames;
            long totalScore = registered ? accountStats.totalScore() : saves.profile().totalScore;
            long averageScore = totalGames == 0 ? 0 : totalScore / totalGames;
            int left = 58, gap = 14, cardW = (w - 116 - gap * 2) / 3, cardH = 112;
            int top1 = 205, top2 = 330;
            drawProfileStat(g, left, top1, cardW, cardH, "HIGHEST SCORE", String.valueOf(highestScore), "Personal best score", new Color(255, 207, 75));
            drawProfileStat(g, left + cardW + gap, top1, cardW, cardH, "HIGHEST LEVEL", "Level " + highestLevel, "Furthest level reached", new Color(31, 190, 239));
            drawProfileStat(g, left + (cardW + gap) * 2, top1, cardW, cardH, "GAMES PLAYED", String.valueOf(totalGames), "Total games launched", new Color(161, 137, 255));
            drawProfileStat(g, left, top2, cardW, cardH, "TOTAL SCORE", String.valueOf(totalScore), "Lifetime points earned", new Color(57, 216, 166));
            drawProfileStat(g, left + cardW + gap, top2, cardW, cardH, "AVERAGE SCORE", String.valueOf(averageScore), "Average points per game", new Color(57, 216, 166));
            drawProfileStat(g, left + (cardW + gap) * 2, top2, cardW, cardH, "PLAYER TYPE", registered ? "Registered" : "Guest", registered ? "Account profile" : "Local guest profile", new Color(31, 190, 239));

            int infoY = h - 105;
            g.setColor(new Color(18, 37, 72));
            g.fillRoundRect(58, infoY, w - 116, 30, 10, 10);
            g.setColor(new Color(95, 145, 204, 150));
            g.drawRoundRect(58, infoY, w - 116, 30, 10, 10);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(new Color(208, 225, 248));
            center(g, registered ? "Your Basket Bounce profile and game results are saved to MongoDB."
                    : "Guest progress is saved locally. Create an account to keep a registered player record.", w / 2, infoY + 19);

            int buttonY = h - 65;
            int buttonW = registered ? 205 : 190;
            int buttonGap = 12;
            int totalW = registered ? buttonW * 2 + buttonGap : buttonW * 3 + buttonGap * 2;
            int buttonX = (w - totalW) / 2;
            drawButton(g, "BACK TO MENU", buttonX, buttonY, buttonW, 42, new Color(54, 91, 145));
            drawButton(g, "REFRESH", buttonX + buttonW + buttonGap, buttonY, buttonW, 42, new Color(24, 151, 224));
            if (!registered) {
                drawButton(g, "CREATE ACCOUNT", buttonX + (buttonW + buttonGap) * 2, buttonY, buttonW, 42, new Color(43, 180, 139));
            }
        }

        void drawProfileStat(Graphics2D g, int x, int y, int width, int height,
                             String heading, String value, String detail, Color accent) {
            g.setColor(new Color(13, 31, 65));
            g.fillRoundRect(x, y, width, height, 15, 15);
            g.setColor(new Color(71, 112, 174, 145));
            g.drawRoundRect(x, y, width, height, 15, 15);
            g.setColor(accent);
            g.fillRoundRect(x + 10, y + 8, width - 20, 4, 4, 4);
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            g.setColor(new Color(190, 213, 245));
            g.drawString(heading, x + 15, y + 31);
            g.setFont(new Font("SansSerif", Font.BOLD, 23));
            g.setColor(accent);
            String shownValue = value.length() > 15 ? value.substring(0, 13) + "…" : value;
            g.drawString(shownValue, x + 15, y + 64);
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.setColor(new Color(194, 211, 238));
            g.drawString(detail, x + 15, y + 88);
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
                drawPlayerProfile(g, w, h);
                return;
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
