package com.jme3.awt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Modern, optimized dialog panel with:
 * - Single offscreen buffer (recreated only on size / scale change)
 * - 60 FPS animation loop (lightweight Swing Timer)
 * - Smooth fade in/out and smooth aurora motion (lerp)
 * - Soft layered shadow and radial aurora gradients
 * - Proper lifecycle: timers start/stop on add/remove
 */
class ModernDialogPanel extends JPanel {

    // --- visual state ---
    private float opacity = 0f;            // current composite opacity
    private float opacityTarget = 1f;      // desired opacity when showing
    private float opacitySpeed = 6f;       // units/sec for fade interpolation

    // --- animation loop ---
    private final Timer mainTimer;         // drives aurora + repaint + opacity LERP
    private static final int FPS = 60;
    private static final int TIMER_DELAY = 1000 / FPS;

    // --- dragging ---
    private Point dragOffset;

    // --- offscreen buffer (double buffering manually) ---
    private BufferedImage buffer;
    private double bufferScale = 1.0;      // for Hi-DPI displays
    private boolean needsRedraw = true;

    // --- aurora / effects state ---
    private final Ellipse2D.Float aurora1 = new Ellipse2D.Float(-200, -150, 520, 320);
    private final Ellipse2D.Float aurora2 = new Ellipse2D.Float(480, 260, 480, 260);
    private float a1vx = 0.9f, a1vy = 0.6f;
    private float a2vx = -0.7f, a2vy = 1.0f;
    private float auroraLerp = 0.08f;      // smoothing for motion

    // --- theme constants (kept local for performance) ---
    private static final class Theme {
        static final Color OVERLAY_BG = new Color(0x0F0F12);
        static final Color DIALOG_BG = new Color(0x141416);
        static final Color BORDER_COLOR = new Color(0x2E2E32, true);
        static final Color SHADOW_COLOR = new Color(0, 0, 0, 120);
        static final Color AURORA_1 = new Color(0x2A9D8F, true);
        static final Color AURORA_2 = new Color(0xE76F51, true);
        static final int CORNER_RADIUS = 18;
        static final int SHADOW_SIZE = 18;
    }

    ModernDialogPanel() {
        setOpaque(false);
        setLayout(new BorderLayout());

        // main animation loop: 60 FPS, updates animation state and repaints when needed
        mainTimer = new Timer(TIMER_DELAY, e -> {
            float tpf = TIMER_DELAY / 1000f;
            // smooth opacity towards target
            if (Math.abs(opacityTarget - opacity) > 0.001f) {
                float alpha = Math.min(1f, tpf * opacitySpeed);
                opacity = lerp(opacity, opacityTarget, alpha);
                needsRedraw = true;
            }
            // animate aurora positions with gentle velocity + lerp for smoothing
            float nx1 = (float) (aurora1.x + Math.cos(a1vx) * 1.4f);
            float ny1 = (float) (aurora1.y + Math.sin(a1vy) * 1.1f);
            aurora1.x = lerp(aurora1.x, nx1, auroraLerp);
            aurora1.y = lerp(aurora1.y, ny1, auroraLerp);

            float nx2 = (float) (aurora2.x + Math.sin(a2vy) * 1.2f);
            float ny2 = (float) (aurora2.y + Math.cos(a2vx) * 1.0f);
            aurora2.x = lerp(aurora2.x, nx2, auroraLerp);
            aurora2.y = lerp(aurora2.y, ny2, auroraLerp);

            // vary the small internal angles for pseudo-random motion
            a1vx += 0.007f;
            a1vy += 0.005f;
            a2vx += 0.006f;
            a2vy -= 0.004f;

            needsRedraw = true;
            if (isVisible()) repaint();
        });

        // Mouse dragging for frameless window
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                dragOffset = e.getPoint();
            }
            @Override public void mouseDragged(MouseEvent e) {
                Window w = SwingUtilities.getWindowAncestor(ModernDialogPanel.this);
                if (w != null && dragOffset != null) {
                    w.setLocation(e.getXOnScreen() - dragOffset.x, e.getYOnScreen() - dragOffset.y);
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    // ---------------- lifecycle ----------------

    @Override
    public void addNotify() {
        super.addNotify();
        startAnimations();
    }

    @Override
    public void removeNotify() {
        stopAnimations();
        super.removeNotify();
    }

    private void startAnimations() {
        if (!mainTimer.isRunning()) mainTimer.start();
    }

    private void stopAnimations() {
        if (mainTimer.isRunning()) mainTimer.stop();
    }

    // ---------------- public controls ----------------

    /**
     * Show panel with animated fade-in.
     */
    public void showAnimated() {
        opacityTarget = 1f;
        startAnimations();
        needsRedraw = true;
        repaint();
    }

    /**
     * Hide panel with animated fade-out. When finished, the window is disposed.
     */
    public void hideAnimated() {
        opacityTarget = 0f;
        startAnimations();
        // schedule disposal once fully invisible inside paintComponent
    }

    public void setOpacitySpeed(float speed) {
        this.opacitySpeed = Math.max(0.5f, speed);
    }

    // ---------------- painting / buffering ----------------

    @Override
    protected void paintComponent(Graphics g) {
        // create/adjust buffer if needed (including Hi-DPI)
        ensureBuffer();

        // redraw logic only when needed
        if (needsRedraw) {
            drawToBuffer();
            needsRedraw = false;
        }

        // finally draw buffer to screen with overall opacity composite
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, opacity))));
        // center the buffer if its scale differs from device transform
        g2.drawImage(buffer, 0, 0, getWidth(), getHeight(), null);
        g2.dispose();

        // if we're fading out to zero, and reached zero — dispose window
        if (opacityTarget == 0f && opacity <= 0.001f) {
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w != null) {
                stopAnimations();
                w.dispose();
            }
        }
    }

    // Ensure buffer exists and matches component size and device scale
    private void ensureBuffer() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());

        // compute device scale (Hi-DPI)
        GraphicsConfiguration gc = getGraphicsConfiguration();
        double scaleX = 1.0, scaleY = 1.0;
        if (gc != null) {
            AffineTransform tx = gc.getDefaultTransform();
            scaleX = tx.getScaleX();
            scaleY = tx.getScaleY();
        }
        double targetScale = Math.max(scaleX, scaleY);

        int bufW = (int) Math.ceil(w * targetScale);
        int bufH = (int) Math.ceil(h * targetScale);

        if (buffer == null || buffer.getWidth() != bufW || buffer.getHeight() != bufH || Math.abs(bufferScale - targetScale) > 1e-6) {
            bufferScale = targetScale;
            buffer = new BufferedImage(bufW, bufH, BufferedImage.TYPE_INT_ARGB);
            needsRedraw = true;
        }
    }

    // The heavy lift: draw dialog visuals into offscreen "buffer"
    private void drawToBuffer() {
        if (buffer == null) return;

        Graphics2D g = buffer.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            // Reset transform: scale to bufferScale so coordinates match component pixels
            g.setTransform(AffineTransform.getScaleInstance(bufferScale, bufferScale));

            int W = getWidth();
            int H = getHeight();

            // Clear with full transparency first
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, W, H);
            g.setComposite(AlphaComposite.SrcOver);

            // 1) dark overlay background
            g.setColor(Theme.OVERLAY_BG);
            g.fillRect(0, 0, W, H);

            // 2) soft layered shadow (multiple rounded rects with growing alpha)
            int s = Theme.SHADOW_SIZE;
            for (int i = 0; i < 6; i++) {
                float pct = (6 - i) / 6f;
                int inset = s - i * 3;
                RoundRectangle2D.Float rect = new RoundRectangle2D.Float(inset, inset, W - inset * 2f, H - inset * 2f, Theme.CORNER_RADIUS, Theme.CORNER_RADIUS);
                g.setColor(new Color(0, 0, 0, Math.min(180, (int) (50 * pct))));
                g.fill(rect);
            }

            // 3) dialog main shape
            int inset = s;
            RoundRectangle2D.Float dialogShape = new RoundRectangle2D.Float(inset, inset, W - inset * 2f, H - inset * 2f, Theme.CORNER_RADIUS, Theme.CORNER_RADIUS);
            g.setClip(dialogShape);
            g.setColor(Theme.DIALOG_BG);
            g.fill(dialogShape);

            // 4) aurora radial paints
            // compute centers and radii (in component coords)
            float c1x = aurora1.x + aurora1.width * 0.5f;
            float c1y = aurora1.y + aurora1.height * 0.5f;
            float r1 = Math.max(aurora1.width, aurora1.height) * 0.6f;

            RadialGradientPaint rg1 = new RadialGradientPaint(new Point((int) c1x, (int) c1y),
                    r1,
                    new float[]{0f, 0.6f, 1f},
                    new Color[]{new Color(Theme.AURORA_1.getRed(), Theme.AURORA_1.getGreen(), Theme.AURORA_1.getBlue(), 200),
                            new Color(Theme.AURORA_1.getRed(), Theme.AURORA_1.getGreen(), Theme.AURORA_1.getBlue(), 70),
                            new Color(0, 0, 0, 0)});
            g.setPaint(rg1);
            g.fill(new Ellipse2D.Float(aurora1.x, aurora1.y, aurora1.width, aurora1.height));

            float c2x = aurora2.x + aurora2.width * 0.5f;
            float c2y = aurora2.y + aurora2.height * 0.5f;
            float r2 = Math.max(aurora2.width, aurora2.height) * 0.6f;

            RadialGradientPaint rg2 = new RadialGradientPaint(new Point((int) c2x, (int) c2y),
                    r2,
                    new float[]{0f, 0.6f, 1f},
                    new Color[]{new Color(Theme.AURORA_2.getRed(), Theme.AURORA_2.getGreen(), Theme.AURORA_2.getBlue(), 180),
                            new Color(Theme.AURORA_2.getRed(), Theme.AURORA_2.getGreen(), Theme.AURORA_2.getBlue(), 60),
                            new Color(0, 0, 0, 0)});
            g.setPaint(rg2);
            g.fill(new Ellipse2D.Float(aurora2.x, aurora2.y, aurora2.width, aurora2.height));

            // Remove clip for border drawing
            g.setClip(null);

            // 5) subtle border
            g.setStroke(new BasicStroke(1.2f));
            g.setColor(Theme.BORDER_COLOR);
            g.draw(dialogShape);

            // Optionally: draw decorative top bar / title placeholder to make dialog feel "finished"
            int barH = 48;
            RoundRectangle2D.Float topBar = new RoundRectangle2D.Float(inset, inset, W - inset * 2f, barH, Theme.CORNER_RADIUS, Theme.CORNER_RADIUS);
            g.setClip(dialogShape); // clip again so topbar stays inside
            g.setComposite(AlphaComposite.SrcOver.derive(0.06f));
            g.setColor(Color.WHITE);
            g.fill(topBar);
            g.setComposite(AlphaComposite.SrcOver);

        } finally {
            g.dispose();
        }
    }

    // ---------------- utilities ----------------

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // Small overload for doubles (used in aurora coords)
    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}