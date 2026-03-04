package engine.view.hud.impl;

import java.awt.*;

/**
 * VictoryHUD — Pantalla de victoria mostrada al derrotar al boss final.
 * Misma estructura y estética que GameOverHUD pero con paleta dorada.
 */
public class VictoryHUD {

    private static final Color PANEL_BG     = new Color(6, 14, 8, 240);
    private static final Color PANEL_BG2    = new Color(3, 8, 10, 240);
    private static final Color BORDER_COLOR = new Color(200, 170, 40, 180);
    private static final Color GLOW_COLOR   = new Color(180, 150, 20, 45);
    private static final Color TITLE_COLOR  = new Color(255, 220, 60);
    private static final Color TITLE_GLOW   = new Color(200, 160, 20, 100);
    private static final Color STAT_LABEL   = new Color(120, 140, 130);
    private static final Color WAVE_VAL     = new Color(100, 220, 255);
    private static final Color SCORE_VAL    = new Color(255, 210, 60);
    private static final Color DIVIDER_COL  = new Color(160, 140, 30, 100);
    private static final Color SEL_BG       = new Color(0, 50, 110, 220);
    private static final Color UNSEL_BG     = new Color(8, 12, 18, 200);
    private static final Color UNSEL_BORDER = new Color(60, 60, 40, 150);
    private static final Color HINT_COLOR   = new Color(80, 90, 80);

    public enum Action { NONE, RETRY, EXIT }

    private boolean active      = false;
    private int     finalScore  = 0;
    private int     selectedIdx = 0;
    private long    showTime    = 0L;

    private static final long LOCK_MILLIS    = 800L;
    private static final long FADE_IN_MILLIS = 600L;

    public boolean isActive() { return active; }

    public void show(int score) {
        if (active) return;
        this.finalScore  = score;
        this.active      = true;
        this.selectedIdx = 0;
        this.showTime    = System.currentTimeMillis();
    }

    public void hide() { active = false; }

    public Action handleKey(int keyCode) {
        if (!active) return Action.NONE;
        if (System.currentTimeMillis() - showTime < LOCK_MILLIS) return Action.NONE;
        return switch (keyCode) {
            case java.awt.event.KeyEvent.VK_LEFT,
                 java.awt.event.KeyEvent.VK_A    -> { selectedIdx = 0; yield Action.NONE; }
            case java.awt.event.KeyEvent.VK_RIGHT,
                 java.awt.event.KeyEvent.VK_D    -> { selectedIdx = 1; yield Action.NONE; }
            case java.awt.event.KeyEvent.VK_R    -> Action.RETRY;
            case java.awt.event.KeyEvent.VK_ESCAPE -> Action.EXIT;
            case java.awt.event.KeyEvent.VK_ENTER  -> (selectedIdx == 0) ? Action.RETRY : Action.EXIT;
            default -> Action.NONE;
        };
    }

    public void draw(Graphics2D g, int viewW, int viewH) {
        if (!active) return;
        long elapsed = System.currentTimeMillis() - showTime;
        float alpha  = Math.min(1.0f, (float) elapsed / FADE_IN_MILLIS);
        drawContent(g, viewW, viewH, alpha);
    }

    private void drawContent(Graphics2D g, int viewW, int viewH, float a) {
        var origComposite = g.getComposite();
        var origHints     = g.getRenderingHints();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        // Overlay oscuro
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.82f * a));
        g.setPaint(new GradientPaint(0, 0, new Color(2, 8, 4, 230), 0, viewH, new Color(1, 4, 2, 230)));
        g.fillRect(0, 0, viewW, viewH);

        // Panel
        int panelW = Math.min(560, viewW - 60);
        int panelH = 320;
        int panelX = (viewW - panelW) / 2;
        int panelY = (viewH - panelH) / 2;
        int cx     = panelX + panelW / 2;

        // Sombra
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f * a));
        g.setColor(Color.BLACK);
        g.fillRoundRect(panelX + 5, panelY + 5, panelW, panelH, 22, 22);

        // Fondo
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.97f * a));
        g.setPaint(new GradientPaint(panelX, panelY, PANEL_BG, panelX, panelY + panelH, PANEL_BG2));
        g.fillRoundRect(panelX, panelY, panelW, panelH, 20, 20);

        // Glow borde dorado
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f * a));
        g.setColor(GLOW_COLOR);
        g.setStroke(new BasicStroke(6f));
        g.drawRoundRect(panelX - 2, panelY - 2, panelW + 4, panelH + 4, 22, 22);

        // Borde
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        g.setColor(BORDER_COLOR);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(panelX, panelY, panelW, panelH, 20, 20);

        // Línea accent superior dorada
        g.setPaint(new GradientPaint(panelX + 40, panelY + 3, new Color(200, 170, 40, 0),
                cx, panelY + 3, new Color(220, 190, 60, 180)));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(panelX + 40, panelY + 3, panelX + panelW - 40, panelY + 3);

        int y = panelY + 58;

        // Título con glow
        String title = "VICTORIA";
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f * a));
        g.setFont(new Font("Monospaced", Font.BOLD, 46));
        g.setColor(TITLE_GLOW);
        g.drawString(title, cx - g.getFontMetrics().stringWidth(title) / 2 - 1, y + 2);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        g.setFont(new Font("Monospaced", Font.BOLD, 44));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(TITLE_COLOR);
        g.drawString(title, cx - fm.stringWidth(title) / 2, y);

        // Subtítulo
        y += 30;
        g.setFont(new Font("Monospaced", Font.PLAIN, 13));
        fm = g.getFontMetrics();
        g.setColor(new Color(160, 200, 160, (int)(200 * a)));
        String sub = "¡Has derrotado al boss final!";
        g.drawString(sub, cx - fm.stringWidth(sub) / 2, y);

        // Stats
        y += 30;
        drawStatRow(g, panelX, y, panelW, "OLEADAS COMPLETADAS", "10", WAVE_VAL, a);
        y += 28;
        drawStatRow(g, panelX, y, panelW, "PUNTUACIÓN FINAL", String.format("%06d", finalScore), SCORE_VAL, a);

        // Separador
        y += 22;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        g.setPaint(new GradientPaint(panelX + 30, y, new Color(160, 140, 30, 0), cx, y, DIVIDER_COL));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(panelX + 30, y, panelX + panelW - 30, y);

        // Botones
        y += 28;
        String[] labels = { "REINTENTAR", "SALIR" };
        String[] hints  = { "R  /  ENTER", "ESC" };
        Color[]  colors = { new Color(60, 140, 255), new Color(200, 60, 80) };

        int bW = 178, bH = 48, bGap = 20;
        int bx0 = cx - (2 * bW + bGap) / 2;

        for (int i = 0; i < 2; i++) {
            int bx  = bx0 + i * (bW + bGap);
            boolean sel = (selectedIdx == i);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f * a));
            g.setColor(Color.BLACK);
            g.fillRoundRect(bx + 2, y + 2, bW, bH, 12, 12);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (sel ? 0.92f : 0.72f) * a));
            if (sel) {
                g.setPaint(new GradientPaint(bx, y, SEL_BG, bx, y + bH,
                        new Color(colors[i].getRed() / 4, colors[i].getGreen() / 4, colors[i].getBlue() / 4, 220)));
            } else {
                g.setColor(UNSEL_BG);
            }
            g.fillRoundRect(bx, y, bW, bH, 12, 12);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            Color bdr = sel ? colors[i] : UNSEL_BORDER;
            g.setColor(new Color(bdr.getRed(), bdr.getGreen(), bdr.getBlue(), sel ? 210 : 100));
            g.setStroke(new BasicStroke(sel ? 1.5f : 1f));
            g.drawRoundRect(bx, y, bW, bH, 12, 12);

            g.setFont(new Font("Monospaced", Font.BOLD, 14));
            fm = g.getFontMetrics();
            g.setColor(sel ? colors[i].brighter() : new Color(140, 145, 165));
            g.drawString(labels[i], bx + (bW - fm.stringWidth(labels[i])) / 2, y + 20);

            g.setFont(new Font("Monospaced", Font.PLAIN, 10));
            fm = g.getFontMetrics();
            g.setColor(sel ? new Color(colors[i].getRed(), colors[i].getGreen(), colors[i].getBlue(), 180)
                           : new Color(80, 85, 110));
            g.drawString(hints[i], bx + (bW - fm.stringWidth(hints[i])) / 2, y + 35);
        }

        y += bH + 16;
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        fm = g.getFontMetrics();
        g.setColor(new Color(HINT_COLOR.getRed(), HINT_COLOR.getGreen(), HINT_COLOR.getBlue(),
                (int)(255 * a * 0.7f)));
        String nav = "←/→  navegar     ENTER  confirmar";
        g.drawString(nav, cx - fm.stringWidth(nav) / 2, y);

        g.setComposite(origComposite);
        g.setRenderingHints(origHints);
    }

    private void drawStatRow(Graphics2D g, int panelX, int y, int panelW,
                             String label, String value, Color valueColor, float a) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        g.setColor(new Color(255, 255, 255, 8));
        g.fillRoundRect(panelX + 30, y - 14, panelW - 60, 20, 6, 6);
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.setColor(STAT_LABEL);
        g.drawString(label, panelX + 44, y);
        g.setFont(new Font("Monospaced", Font.BOLD, 16));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(valueColor);
        g.drawString(value, panelX + panelW - 44 - fm.stringWidth(value), y);
    }
}


