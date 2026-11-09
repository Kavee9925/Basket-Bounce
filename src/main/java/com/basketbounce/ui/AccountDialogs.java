package com.basketbounce.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.util.Arrays;

/**
 * Displays Login and Create Account demo forms using the Basket Bounce menu theme.
 * No account data is stored because this project has no database/authentication backend.
 */
final class AccountDialogs {
    private static final Color NAVY = new Color(17, 29, 65);
    private static final Color BLUE = new Color(64, 116, 190);
    private static final Color GREEN = new Color(35, 157, 125);
    private static final Color BUTTON_TEXT = new Color(31, 55, 82);
    private static final Color TEXT = new Color(235, 243, 255);
    private static final Font LABEL_FONT = new Font("SansSerif", Font.BOLD, 13);

    private AccountDialogs() {
    }

    static void showLogin(Component parent) {
        JTextField usernameField = createTextField();
        JPasswordField passwordField = createPasswordField();
        JLabel status = createStatusLabel();

        JPanel fields = createFieldsPanel();
        addFormRow(fields, "Username", usernameField);
        addFormRow(fields, "Password", passwordField);

        JDialog dialog = createDialog(parent, "LOGIN", "Welcome back! Enter your details to continue.", 500, 390);
        JButton submitButton = createActionButton("LOGIN", new Color(174, 216, 255));
        JButton cancelButton = createActionButton("CANCEL", new Color(255, 218, 184));

        submitButton.addActionListener(event -> {
            char[] password = passwordField.getPassword();
            if (usernameField.getText().trim().isEmpty() || password.length == 0) {
                status.setForeground(new Color(255, 190, 150));
                status.setText("Please enter both your username and password.");
            } else {
                status.setForeground(new Color(178, 235, 215));
                status.setText("Demo only: login needs a connected database and backend.");
            }
            Arrays.fill(password, '\0');
        });
        cancelButton.addActionListener(event -> dialog.dispose());

        showDialogContent(dialog, fields, status, submitButton, cancelButton);
        dialog.setVisible(true);
    }

    static void showCreateAccount(Component parent) {
        JTextField fullNameField = createTextField();
        JTextField usernameField = createTextField();
        JTextField emailField = createTextField();
        JPasswordField passwordField = createPasswordField();
        JPasswordField confirmPasswordField = createPasswordField();
        JLabel status = createStatusLabel();

        JPanel fields = createFieldsPanel();
        addFormRow(fields, "Full Name", fullNameField);
        addFormRow(fields, "Username", usernameField);
        addFormRow(fields, "Email Address", emailField);
        addFormRow(fields, "Password", passwordField);
        addFormRow(fields, "Confirm Password", confirmPasswordField);

        JDialog dialog = createDialog(
                parent,
                "CREATE ACCOUNT",
                "Fill in the details below to set up your profile.",
                540,
                535
        );
        JButton createButton = createActionButton("CREATE ACCOUNT", new Color(166, 235, 207));
        JButton cancelButton = createActionButton("CANCEL", new Color(255, 218, 184));

        createButton.addActionListener(event -> {
            char[] password = passwordField.getPassword();
            char[] confirmPassword = confirmPasswordField.getPassword();
            String validationMessage = validateAccountDetails(
                    fullNameField.getText().trim(),
                    usernameField.getText().trim(),
                    emailField.getText().trim(),
                    password,
                    confirmPassword
            );

            if (validationMessage != null) {
                status.setForeground(new Color(255, 190, 150));
                status.setText(validationMessage);
            } else {
                status.setForeground(new Color(178, 235, 215));
                status.setText("Demo only: account creation needs a connected database and backend.");
            }

            Arrays.fill(password, '\0');
            Arrays.fill(confirmPassword, '\0');
        });
        cancelButton.addActionListener(event -> dialog.dispose());

        showDialogContent(dialog, fields, status, createButton, cancelButton);
        dialog.setVisible(true);
    }

    private static JDialog createDialog(Component parent, String title, String subtitle, int width, int height) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(new DialogBackground());
        dialog.setSize(width, height);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(parent);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(new Color(150, 190, 225, 110), 1));
        dialog.getRootPane().putClientProperty("subtitle", subtitle);
        return dialog;
    }

    private static void showDialogContent(
            JDialog dialog,
            JPanel fields,
            JLabel status,
            JButton primaryButton,
            JButton cancelButton
    ) {
        String subtitle = (String) dialog.getRootPane().getClientProperty("subtitle");
        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(28, 34, 26, 34));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(dialog.getTitle());
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setForeground(new Color(255, 222, 112));
        title.setFont(new Font("SansSerif", Font.BOLD, 25));
        JLabel description = new JLabel(subtitle);
        description.setAlignmentX(Component.CENTER_ALIGNMENT);
        description.setForeground(TEXT);
        description.setFont(new Font("SansSerif", Font.PLAIN, 12));
        heading.add(title);
        heading.add(Box.createVerticalStrut(8));
        heading.add(description);

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        center.add(fields, BorderLayout.CENTER);
        center.add(status, BorderLayout.SOUTH);

        JPanel actions = new JPanel(new GridLayout(1, 2, 12, 0));
        actions.setOpaque(false);
        actions.add(primaryButton);
        actions.add(cancelButton);

        content.add(heading, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(new DialogBackground(content));
        dialog.getRootPane().setDefaultButton(primaryButton);
    }

    private static JPanel createFieldsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(4, 0, 4, 0));
        return panel;
    }

    private static void addFormRow(JPanel panel, String labelText, JComponent input) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = panel.getComponentCount() / 2;
        labelConstraints.anchor = GridBagConstraints.LINE_START;
        labelConstraints.insets = new Insets(6, 0, 6, 14);

        JLabel label = new JLabel(labelText);
        label.setForeground(TEXT);
        label.setFont(LABEL_FONT);
        panel.add(label, labelConstraints);

        GridBagConstraints inputConstraints = new GridBagConstraints();
        inputConstraints.gridx = 1;
        inputConstraints.gridy = labelConstraints.gridy;
        inputConstraints.weightx = 1;
        inputConstraints.fill = GridBagConstraints.HORIZONTAL;
        inputConstraints.insets = new Insets(6, 0, 6, 0);
        panel.add(input, inputConstraints);
    }

    private static JTextField createTextField() {
        JTextField field = new JTextField();
        styleInput(field);
        return field;
    }

    private static JPasswordField createPasswordField() {
        JPasswordField field = new JPasswordField();
        styleInput(field);
        return field;
    }

    private static void styleInput(JTextField field) {
        field.setFont(new Font("SansSerif", Font.PLAIN, 14));
        field.setForeground(new Color(28, 43, 68));
        field.setBackground(new Color(239, 246, 255));
        field.setCaretColor(new Color(35, 157, 125));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 205, 230), 1),
                new EmptyBorder(8, 10, 8, 10)
        ));
        field.setPreferredSize(new Dimension(250, 36));
    }

    private static JLabel createStatusLabel() {
        JLabel status = new JLabel(" ");
        status.setForeground(TEXT);
        status.setFont(new Font("SansSerif", Font.PLAIN, 11));
        status.setHorizontalAlignment(SwingConstants.CENTER);
        return status;
    }

    private static JButton createActionButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setUI(new BasicButtonUI());
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setForeground(BUTTON_TEXT);
        button.setBackground(color);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color.darker(), 1),
                new EmptyBorder(10, 12, 10, 12)
        ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static String validateAccountDetails(
            String fullName,
            String username,
            String email,
            char[] password,
            char[] confirmPassword
    ) {
        if (fullName.isEmpty() || username.isEmpty() || email.isEmpty()
                || password.length == 0 || confirmPassword.length == 0) {
            return "Please complete all fields.";
        }
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return "Please enter a valid email address.";
        }
        if (password.length < 6) {
            return "Password must contain at least 6 characters.";
        }
        if (!Arrays.equals(password, confirmPassword)) {
            return "The passwords do not match. Please try again.";
        }
        return null;
    }

    private static final class DialogBackground extends JPanel {
        DialogBackground() {
            this(null);
        }

        DialogBackground(JComponent content) {
            setLayout(new BorderLayout());
            if (content != null) {
                add(content, BorderLayout.CENTER);
            }
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, NAVY, getWidth(), getHeight(), new Color(69, 119, 153)));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(255, 255, 255, 20));
            for (int i = 0; i < 9; i++) {
                int size = 12 + (i % 3) * 8;
                int x = 18 + (i * 71) % Math.max(1, getWidth() - 35);
                int y = 20 + (i * 53) % Math.max(1, getHeight() - 35);
                g.fillOval(x, y, size, size);
            }
            g.dispose();
        }
    }
}
