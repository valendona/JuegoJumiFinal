package engine.view.hud.impl;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * ScoreHUD — Muestra la puntuación en la esquina superior derecha.
 * También gestiona el popup de puntos ganados (+N) con fade-out.
 */
public class ScoreHUD {

    private static final Color SCORE_COLOR  = new Color(255, 220, 50);
    private static final Color LABEL_COLOR  = new Color(160, 160, 160);
    private static final Color POPUP_COLOR  = new Color(255, 255, 100);
    private static final Color BG_COLOR     = new Color(0, 0, 0, 130);
    private static final Color BORDER_COLOR = new Color(255, 160, 0, 100);

    private int  score      = 0;
    private int  highScore  = 0;

    // Popup "+N puntos"
    private String popupText   = null;
    private long   popupStart  = 0L;
    private static final long POPUP_DURATION_MS = 1200L;

    public void addScore(int pts) {
        score += pts;
        if (score > highScore) highScore = score;
        popupText  = "+" + pts;
        popupStart = System.currentTimeMillis();
    }

    public void reset() { score = 0; }

    public int getScore()     { return score; }
    public int getHighScore() { return highScore; }

    public void draw(Graphics2D g, int viewW) {
        var origComposite = g.getComposite();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Badge fondo
        int badgeW = 200, badgeH = 44;
        int badgeX = viewW - badgeW - 12;
        int badgeY = 12;

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.80f));
        g.setColor(BG_COLOR);
        g.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 14, 14);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.setColor(BORDER_COLOR);
        g.drawRoundRect(badgeX, badgeY, badgeW, badgeH, 14, 14);

        int cx = badgeX + badgeW / 2;
        int cy = badgeY + badgeH / 2;

        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(LABEL_COLOR);
        String label = "PUNTUACIÓN";
        g.drawString(label, cx - fm.stringWidth(label) / 2, cy - 6);

        g.setFont(new Font("Monospaced", Font.BOLD, 18));
        fm = g.getFontMetrics();
        g.setColor(SCORE_COLOR);
        String scoreStr = String.format("%06d", score);
        g.drawString(scoreStr, cx - fm.stringWidth(scoreStr) / 2, cy + 12);

        // Popup "+N"
        long now = System.currentTimeMillis();
        if (popupText != null && now - popupStart < POPUP_DURATION_MS) {
            float progress = (float)(now - popupStart) / POPUP_DURATION_MS;
            float alpha = Math.max(0f, 1f - progress * 1.4f);
            int popY = badgeY + badgeH + 6 + (int)(progress * -20);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.setFont(new Font("Monospaced", Font.BOLD, 16));
            fm = g.getFontMetrics();
            g.setColor(POPUP_COLOR);
            g.drawString(popupText, cx - fm.stringWidth(popupText) / 2, popY);
        } else if (popupText != null && now - popupStart >= POPUP_DURATION_MS) {
            popupText = null;
        }

        g.setComposite(origComposite);
    }
}

