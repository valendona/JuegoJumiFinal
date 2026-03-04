package engine.view.hud.impl;

import java.awt.*;
import java.awt.geom.*;

/**
 * WaveHUD — Panel superior centrado.
 * Muestra oleada, enemigos restantes y puntuación en una única barra.
 */
public class WaveHUD {

    private static final Color BG_COLOR      = new Color(8, 10, 24, 210);
    private static final Color BORDER_COLOR  = new Color(80, 130, 255, 140);
    private static final Color GLOW_COLOR    = new Color(60, 100, 255, 35);
    private static final Color WAVE_COLOR    = new Color(100, 200, 255);
    private static final Color ENEMY_COLOR   = new Color(255, 100, 100);
    private static final Color SCORE_COLOR   = new Color(255, 210, 60);
    private static final Color LABEL_COLOR   = new Color(100, 120, 180);
    private static final Color DIVIDER_COLOR = new Color(60, 80, 160, 120);
    private static final Color POPUP_COLOR   = new Color(255, 230, 80);
    private static final Color ANNOUNCE_BG   = new Color(8, 10, 28, 220);
    private static final Color ANNOUNCE_BORDER = new Color(100, 160, 255, 200);

    private String announceText  = null;
    private long   announceUntil = 0L;
    private static final long ANNOUNCE_DURATION = 2500L;

    private int wave         = 1;
    private int enemiesLeft  = 0;
    private int enemiesTotal = 0;
    private int score        = 0;
    private int highScore    = 0;

    // Popup "+N"
    private String popupText   = null;
    private long   popupStart  = 0L;
    private static final long POPUP_MS = 1400L;

    // *** PUBLICS ***
    public void setWave(int wave)           { this.wave = wave; }
    public void setEnemiesLeft(int n)       { this.enemiesLeft = n; }
    public void setEnemiesTotal(int n)      { this.enemiesTotal = n; }
    public void setScore(int s)             { this.score = s; }
    public void setHighScore(int h)         { this.highScore = h; }
    public void addScorePopup(int pts) {
        popupText  = "+" + pts;
        popupStart = System.currentTimeMillis();
    }

    public void announce(String text, long durationMs) {
        this.announceText  = text;
        this.announceUntil = System.currentTimeMillis() + durationMs;
    }

    public void draw(Graphics2D g, int viewW, int viewH) {
        var origComposite = g.getComposite();
        var origHints     = g.getRenderingHints();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawTopBar(g, viewW);

        long now = System.currentTimeMillis();
        if (announceText != null && now < announceUntil) {
            drawAnnounce(g, viewW, viewH, now);
        } else if (announceText != null) {
            announceText = null;
        }

        if (popupText != null && now - popupStart < POPUP_MS) {
            drawPopup(g, viewW, now);
        } else if (popupText != null) {
            popupText = null;
        }

        g.setComposite(origComposite);
        g.setRenderingHints(origHints);
    }

    // *** PRIVATE ***

    private void drawTopBar(Graphics2D g, int viewW) {
        // 3 secciones: OLEADA | ENEMIGOS | PUNTUACIÓN
        int badgeW = Math.min(560, viewW - 40);
        int badgeH = 48;
        int badgeX = (viewW - badgeW) / 2;
        int badgeY = 12;

        // Sombra
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
        g.setColor(Color.BLACK);
        g.fillRoundRect(badgeX + 3, badgeY + 3, badgeW, badgeH, 16, 16);

        // Fondo gradiente
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));
        GradientPaint bg = new GradientPaint(
                badgeX, badgeY,           new Color(12, 16, 40, 225),
                badgeX, badgeY + badgeH,  new Color(6, 8, 22, 225));
        g.setPaint(bg);
        g.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 14, 14);

        // Glow borde
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        g.setColor(GLOW_COLOR);
        g.setStroke(new BasicStroke(4f));
        g.drawRoundRect(badgeX - 1, badgeY - 1, badgeW + 2, badgeH + 2, 15, 15);

        // Borde fino
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.setColor(BORDER_COLOR);
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(badgeX, badgeY, badgeW, badgeH, 14, 14);

        // Divisores internos
        g.setColor(DIVIDER_COLOR);
        g.setStroke(new BasicStroke(1f));
        int div1 = badgeX + badgeW / 3;
        int div2 = badgeX + badgeW * 2 / 3;
        g.drawLine(div1, badgeY + 8, div1, badgeY + badgeH - 8);
        g.drawLine(div2, badgeY + 8, div2, badgeY + badgeH - 8);

        int cy = badgeY + badgeH / 2;
        // Sección oleada
        drawSection(g, badgeX + badgeW / 6, cy, "OLEADA",
                String.valueOf(wave), WAVE_COLOR);
        // Sección enemigos
        drawSection(g, badgeX + badgeW / 2, cy, "ENEMIGOS",
                enemiesLeft + " / " + enemiesTotal, ENEMY_COLOR);
        // Sección puntuación
        drawSection(g, badgeX + badgeW * 5 / 6, cy, "PUNTOS",
                String.format("%06d", score), SCORE_COLOR);
    }

    private void drawSection(Graphics2D g, int cx, int cy, String label, String value, Color valueColor) {
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(LABEL_COLOR);
        g.drawString(label, cx - fm.stringWidth(label) / 2, cy - 7);

        g.setFont(new Font("Monospaced", Font.BOLD, 17));
        fm = g.getFontMetrics();
        g.setColor(valueColor);
        g.drawString(value, cx - fm.stringWidth(value) / 2, cy + 12);
    }

    private void drawPopup(Graphics2D g, int viewW, long now) {
        float progress = (float)(now - popupStart) / POPUP_MS;
        float alpha = Math.max(0f, 1f - progress * 1.3f);
        int popY = 72 - (int)(progress * 24);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        FontMetrics fm = g.getFontMetrics();
        // Sombra del texto
        g.setColor(new Color(0, 0, 0, (int)(180 * alpha)));
        g.drawString(popupText, (viewW - fm.stringWidth(popupText)) / 2 + 1, popY + 1);
        g.setColor(POPUP_COLOR);
        g.drawString(popupText, (viewW - fm.stringWidth(popupText)) / 2, popY);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }

    private void drawAnnounce(Graphics2D g, int viewW, int viewH, long now) {
        long remaining = announceUntil - now;
        long elapsed   = ANNOUNCE_DURATION - remaining;
        float alpha;
        if (elapsed < 280)      alpha = elapsed / 280f;
        else if (remaining < 380) alpha = remaining / 380f;
        else                    alpha = 1.0f;
        alpha = Math.max(0f, Math.min(1f, alpha));

        boolean isBossAnnounce = announceText != null &&
                (announceText.contains("BOSS") || announceText.contains("MINIBOSS"));
        Color annBorder = isBossAnnounce ? new Color(220, 60, 60, 200) : ANNOUNCE_BORDER;
        Color annText   = isBossAnnounce ? new Color(255, 100, 80)     : WAVE_COLOR;

        int panelW = 520, panelH = 72;
        int panelX = (viewW - panelW) / 2;
        int panelY = viewH / 2 - 140;

        // Sombra
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.3f));
        g.setColor(Color.BLACK);
        g.fillRoundRect(panelX + 4, panelY + 4, panelW, panelH, 20, 20);

        // Fondo
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.9f));
        GradientPaint bg = new GradientPaint(
                panelX, panelY,           new Color(10, 14, 40, 230),
                panelX, panelY + panelH,  new Color(6, 8, 20, 230));
        g.setPaint(bg);
        g.fillRoundRect(panelX, panelY, panelW, panelH, 18, 18);

        // Glow borde
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.4f));
        g.setColor(new Color(annBorder.getRed(), annBorder.getGreen(), annBorder.getBlue(), 50));
        g.setStroke(new BasicStroke(5f));
        g.drawRoundRect(panelX - 1, panelY - 1, panelW + 2, panelH + 2, 19, 19);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.setColor(annBorder);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(panelX, panelY, panelW, panelH, 18, 18);

        g.setFont(new Font("Monospaced", Font.BOLD, 28));
        FontMetrics fm = g.getFontMetrics();
        // Sombra texto
        g.setColor(new Color(0, 0, 0, (int)(200 * alpha)));
        g.drawString(announceText, (viewW - fm.stringWidth(announceText)) / 2 + 2,
                panelY + panelH / 2 + fm.getAscent() / 2 - 4 + 2);
        // Texto principal
        g.setColor(new Color(annText.getRed(), annText.getGreen(), annText.getBlue(), (int)(255 * alpha)));
        g.drawString(announceText, (viewW - fm.stringWidth(announceText)) / 2,
                panelY + panelH / 2 + fm.getAscent() / 2 - 4);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }
}
