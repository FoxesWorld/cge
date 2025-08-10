package com.jme3.awt;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized UI theme for Calista Game Engine dialogs and HUD-like windows.
 *
 * Improvements over a simple constants holder:
 *  - HiDPI-aware sizing and fonts (scale based on screen DPI)
 *  - Palette harmonization with derived accent / glow colors
 *  - Accessible contrast helper (contrast ratio)
 *  - Utilities for common paint patterns (neon glow, aurora gradients)
 *  - Convenience method to apply quick FlatLaf / UIManager overrides
 *
 * Usage examples:
 *  - In your dialog constructor:
 *      setFont(Theme.getScaledFont(Theme.FONT_UI, 1.0f));
 *      setBackground(Theme.DIALOG_BG);
 *
 *  - Apply small overrides to FlatLaf:
 *      Theme.applyToUIManager();
 *
 *  - Get glow paint for custom painting:
 *      Paint glow = Theme.createGlow(Theme.PRIMARY_ACCENT_COLOR, 8);
 *
 * NOTE: This class is intentionally self-contained and provides conservative fallbacks
 * (system fonts) so it compiles and looks acceptable even if custom fonts are not installed.
 */
public final class Theme {
    private static final Logger LOG = Logger.getLogger(Theme.class.getName());

    // --- DPI / scale ---
    private static final double BASE_DPI = 96.0; // conventional baseline
    private static final double SCREEN_DPI = Toolkit.getDefaultToolkit().getScreenResolution();
    private static final float SCALE = (float) Math.max(1.0f, SCREEN_DPI / BASE_DPI);

    /**
     * Get UI scale factor derived from screen DPI.
     * Typical values: 1.0 (96 DPI), 1.25 (120 DPI), 1.5 (144 DPI), etc.
     */
    public static float getScale() {
        return SCALE;
    }

    // --- Core colors (designer-chosen, harmonious) ---
    public static final Color PRIMARY_COLOR = new Color(0, 200, 160);        // neon-teal
    public static final Color PRIMARY_ACCENT_COLOR = new Color(94, 82, 255); // violet accent
    public static final Color DIALOG_BG = new Color(30, 31, 34);             // dark dialog base
    public static final Color TITLE_BAR_BG = new Color(40, 42, 46, 220);     // translucent title
    public static final Color OVERLAY_BG = new Color(0, 0, 0, 120);         // backdrop dimming
    public static final Color SHADOW_COLOR = new Color(0, 0, 0, 150);
    public static final Color BORDER_COLOR = new Color(60, 63, 66);
    public static final Color BORDER_COLOR_LIGHT = new Color(90, 93, 96, 40);

    // Aurora subtle accents (low alpha: decorative, not for readable text)
    public static final Color AURORA_1 = new Color(138, 43, 226, 32);
    public static final Color AURORA_2 = new Color(255, 69, 0, 28);

    // Text
    public static final Color TEXT_COLOR = new Color(230, 230, 234);
    public static final Color TEXT_MUTED_COLOR = new Color(170, 170, 180);
    public static final Color TEXT_AREA_BG = new Color(22, 23, 25); // code background (opaque)

    // Buttons / backgrounds
    public static final Color BUTTON_SECONDARY_BG = new Color(55, 58, 64);
    public static final Color BUTTON_PRIMARY_BG = PRIMARY_COLOR;
    public static final Color BUTTON_PRIMARY_FG = Color.black;

    // --- Sizing and metrics (HiDPI-aware) ---
    public static final int CORNER_RADIUS = scaleInt(16);
    public static final int SHADOW_SIZE = scaleInt(8);
    public static final int DEFAULT_PADDING = scaleInt(12);
    public static final Insets DEFAULT_INSETS = new Insets(DEFAULT_PADDING, DEFAULT_PADDING, DEFAULT_PADDING, DEFAULT_PADDING);

    // --- Fonts (detect system UI font; provide fallbacks) ---
    // We prefer system/segmented UI font; if not present fallback to SansSerif / Monospaced.
    private static final String SYS_UI_FONT = UIManager.getFont("Label.font") != null
            ? UIManager.getFont("Label.font").getFamily()
            : "SansSerif";

    public static final Font FONT_UI = deriveFont(new Font(SYS_UI_FONT, Font.PLAIN, 14), 14f);
    public static final Font FONT_UI_BOLD = deriveFont(new Font(SYS_UI_FONT, Font.BOLD, 15), 15f);
    public static final Font FONT_TITLE = deriveFont(new Font(SYS_UI_FONT, Font.BOLD, 18), 18f);
    public static final Font FONT_MONO = deriveFont(new Font("Monospaced", Font.PLAIN, 13), 13f);

    // --- Private constructor (statics only) ---
    private Theme() { /* no instantiation */ }

    // --- Utilities: scaling helpers ---

    /**
     * Scales an integer pixel value by the DPI scale.
     */
    public static int scaleInt(int base) {
        return Math.max(1, Math.round(base * SCALE));
    }

    /**
     * Returns a derived font scaled by UI scale factor, preserving style.
     * Example: Theme.getScaledFont(Theme.FONT_UI, 1.2f)
     */
    public static Font getScaledFont(Font base, float multiplier) {
        if (base == null) base = FONT_UI;
        float size = Math.max(1f, base.getSize2D() * multiplier * SCALE);
        return base.deriveFont(base.getStyle(), size);
    }

    /**
     * Helper to derive a font and pre-scale it by the UI scale factor.
     */
    private static Font deriveFont(Font f, float logicalSize) {
        return f.deriveFont(f.getStyle(), logicalSize * SCALE);
    }

    // --- Color utilities ---

    /**
     * Derives a translucent glow color from a base color.
     *
     * @param base  base color
     * @param alpha 0..255 alpha
     */
    public static Color glow(Color base, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }

    /**
     * Create a linear gradient paint (vertical) using two theme aurora colors.
     * Useful for decorative backgrounds behind title or header.
     */
    public static Paint createAuroraGradient(int width, int height) {
        // gradient from top-left to bottom-right subtle mix between aurora colors
        return new GradientPaint(0, 0, AURORA_1, width, height, AURORA_2);
    }

    /**
     * Returns a slightly darkened version of given color.
     */
    public static Color darken(Color c, float factor) {
        factor = Math.max(0f, Math.min(1f, factor));
        int r = Math.max(0, (int) (c.getRed() * (1f - factor)));
        int g = Math.max(0, (int) (c.getGreen() * (1f - factor)));
        int b = Math.max(0, (int) (c.getBlue() * (1f - factor)));
        return new Color(r, g, b, c.getAlpha());
    }

    /**
     * Returns a slightly lightened version of given color.
     */
    public static Color lighten(Color c, float factor) {
        factor = Math.max(0f, Math.min(1f, factor));
        int r = Math.min(255, (int) (c.getRed() + (255 - c.getRed()) * factor));
        int g = Math.min(255, (int) (c.getGreen() + (255 - c.getGreen()) * factor));
        int b = Math.min(255, (int) (c.getBlue() + (255 - c.getBlue()) * factor));
        return new Color(r, g, b, c.getAlpha());
    }

    /**
     * Returns a Color with identical RGB but provided alpha.
     */
    public static Color withAlpha(Color c, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    // --- Accessibility / contrast helpers ---

    /**
     * Calculate the relative luminance of a color (WCAG).
     */
    public static double relativeLuminance(Color c) {
        double r = srgbToLinear(c.getRed() / 255.0);
        double g = srgbToLinear(c.getGreen() / 255.0);
        double b = srgbToLinear(c.getBlue() / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double srgbToLinear(double v) {
        if (v <= 0.03928) return v / 12.92;
        return Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /**
     * Compute contrast ratio according to WCAG 2.0.
     * Returns ratio >= 1.0 (higher is better); recommended minimum is 4.5 for normal text.
     */
    public static double contrastRatio(Color a, Color b) {
        double L1 = relativeLuminance(a) + 0.05;
        double L2 = relativeLuminance(b) + 0.05;
        return Math.max(L1, L2) / Math.min(L1, L2);
    }

    /**
     * Convenience: ensure readable foreground for given background. If contrast is low,
     * returns either white or black depending on which has higher contrast.
     */
    public static Color readableOn(Color background) {
        double contrastWithWhite = contrastRatio(background, Color.white);
        double contrastWithBlack = contrastRatio(background, Color.black);
        return contrastWithWhite >= contrastWithBlack ? Color.white : Color.black;
    }

    // --- UI/Look-and-feel helpers ---

    /**
     * Apply a small set of UIManager overrides convenient for FlatLaf-based dialogs:
     *  - focus ring width
     *  - default dialog / panel background
     *  - button radius
     *
     * This is non-invasive and safe to call early in UI initialization (after FlatLaf.setup()).
     */
    public static void applyToUIManager() {
        try {
            UIManager.put("Panel.background", DIALOG_BG);
            UIManager.put("OptionPane.background", DIALOG_BG);
            UIManager.put("Button.background", BUTTON_SECONDARY_BG);
            UIManager.put("Button.foreground", TEXT_COLOR);
            UIManager.put("Button.arc", scaleInt(8));
            UIManager.put("Component.focusWidth", scaleInt(2));
            UIManager.put("ToolTip.background", withAlpha(Color.black, 220));
            // fonts
            UIManager.put("Label.font", FONT_UI);
            UIManager.put("Button.font", FONT_UI_BOLD);
            UIManager.put("TextPane.font", FONT_MONO);
        } catch (Exception ex) {
            LOG.log(Level.FINER, "applyToUIManager failed", ex);
        }
    }

    /**
     * Build a rounded border for components (useful for panels, text areas).
     */
    public static Border createRoundedBorder(int radius) {
        int r = Math.max(1, scaleInt(radius));
        return BorderFactory.createCompoundBorder(
                new RoundedLineBorder(BORDER_COLOR, 1, r),
                new EmptyBorder(scaleInt(8), scaleInt(10), scaleInt(8), scaleInt(10))
        );
    }

    /**
     * Lightweight rounded line border implementation (stroke only).
     * Minimal, dependency-free.
     */
    private static final class RoundedLineBorder implements Border {
        private final Color color;
        private final int thickness;
        private final int radius;

        RoundedLineBorder(Color color, int thickness, int radius) {
            this.color = color;
            this.thickness = Math.max(1, thickness);
            this.radius = Math.max(2, radius);
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness, thickness, thickness, thickness);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(thickness));
                g2.drawRoundRect(x + thickness/2, y + thickness/2,
                        width - thickness, height - thickness,
                        radius, radius);
            } finally {
                g2.dispose();
            }
        }
    }

    // --- Small helpers and documentation aid ---

    /**
     * Log current theme diagnostics (useful for development).
     */
    public static void logDiagnostics() {
        LOG.info(() -> "Theme diagnostics: scale=" + SCALE
                + ", screenDPI=" + SCREEN_DPI
                + ", FONT_UI=" + FONT_UI.getFamily()
                + ", contrast (text/dialog)=" + String.format("%.2f", contrastRatio(TEXT_COLOR, DIALOG_BG)));
    }

    // Example main for quick preview (developer use only)
    public static void main(String[] args) {
        // quick preview of some theme elements
        SwingUtilities.invokeLater(() -> {
            try {
                // optionally setup FlatLaf here if available
                // FlatDarkLaf.setup();
                Theme.applyToUIManager();
            } catch (Exception ignored) { }
            JFrame f = new JFrame("Theme Preview");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            JPanel p = new JPanel();
            p.setBackground(DIALOG_BG);
            p.setBorder(createRoundedBorder(CORNER_RADIUS));
            p.setLayout(new BorderLayout());
            JLabel title = new JLabel("Calista Error Preview");
            title.setFont(FONT_TITLE);
            title.setForeground(PRIMARY_COLOR);
            title.setBorder(new EmptyBorder(12,12,8,12));
            JTextArea ta = new JTextArea("Example code / stack trace area...\nLine 1\nLine 2\n\tat com.example.Main (Main.java:42)");
            ta.setBackground(TEXT_AREA_BG);
            ta.setForeground(TEXT_MUTED_COLOR);
            ta.setFont(FONT_MONO);
            p.add(title, BorderLayout.NORTH);
            p.add(new JScrollPane(ta), BorderLayout.CENTER);
            f.setContentPane(p);
            f.setSize(640, 360);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            logDiagnostics();
        });
    }
}
