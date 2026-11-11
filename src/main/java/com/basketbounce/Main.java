package com.basketbounce;

import com.basketbounce.storage.MongoDBService;
import com.basketbounce.ui.GameWindow;

import java.util.concurrent.CompletableFuture;

import javax.swing.*;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            CompletableFuture.runAsync(() -> {
                try {
                    MongoDBService.getInstance().initialize();
                    System.out.println("Connected to MongoDB database: " + MongoDBService.DATABASE_NAME);
                } catch (Exception exception) {
                    System.err.println("MongoDB is not available yet. Start MongoDB before using Login/Create Account.");
                }
            });
            new GameWindow().setVisible(true);
        });
    }
}
