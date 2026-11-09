package com.basketbounce;

import com.basketbounce.ui.GameWindow;

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
            new GameWindow().setVisible(true);
        });
    }
}
