package engine.view.hud.impl;

import java.awt.*;
import java.awt.geom.*;

/**
 * GameOverHUD — Pantalla de fin de partida con fade-in y estética espacial.
 */
public class GameOverHUD {

    private static final Color BG_DEEP      = new Color(8, 2, 6);
    private static final Color PANEL_BG     = new Color(16, 6, 14, 240);
    private static final Color PANEL_BG2    = new Color(8, 3, 10, 240);
    private static final Color BORDER_COLOR = new Color(200, 40, 60, 180);
    private static final Color GLOW_COLOR   = new Color(180, 20, 40, 45);
    private static final Color TITLE_COLOR  = new Color(255, 70, 80);
    private static final Color TITLE_GLOW   = new Color(200, 30, 40, 100);
    private static final Color STAT_LABEL   = new Color(120, 130, 160);
    private static final Color STAT_VAL     = new Color(200, 215, 240);
    private static final Color WAVE_VAL     = new Color(100, 200, 255);
    private static final Color SCORE_VAL    = new Color(255, 210, 60);
    private static final Color DIVIDER_COL  = new Color(160, 30, 50, 100);
    private static final Color SEL_BG       = new Color(0, 50, 110, 220);
    private static final Color UNSEL_BG     = new Color(12, 8, 18, 200);
    private static final Color SEL_BORDER   = new Color(80, 160, 255, 220);
    private static final Color UNSEL_BORDER = new Color(60, 40, 70, 150);
    private static final Color HINT_COLOR   = new Color(80, 85, 110);

    public enum Action { NONE, RETRY, EXIT }

    private boolean active        = false;
    private int     waveReached   = 1;
    private int     finalScore    = 0;
    private int     selectedIdx   = 0;
    private Action  pendingAction = Action.NONE;
    private long    showTime      = 0L;
    private static final long LOCK_MILLIS     = 800L;
    private static final long FADE_IN_MILLIS  = 600L;  // duración del fade-in interno

    public boolean isActive()           { return active; }
    public Action  getPendingAction()   { return pendingAction; }
    public void    clearPendingAction() { pendingAction = Action.NONE; }

    public void show(int waveReached) { show(waveReached, 0); }

    public void show(int waveReached, int score) {
        if (this.active) return;
        this.waveReached   = waveReached;
        this.finalScore    = score;
        this.active        = true;
        this.selectedIdx   = 0;
        this.pendingAction = Action.NONE;
        this.showTime      = System.currentTimeMillis();
    }

    /** Dibuja con el alpha del Graphics2D ya configurado externamente (para fade externo del Renderer). */
    public void drawFade(Graphics2D g, int viewW, int viewH, int waveReached, int score) {
        this.waveReached = waveReached;
        this.finalScore  = score;
        boolean wasActive = this.active;
        this.active = true;
        drawContent(g, viewW, viewH, 1.0f);
        this.active = wasActive;
    }

    public void hide() { active = false; }

    public Action handleKey(int keyCode) {
        if (!active) return Action.NONE;
        if (System.currentTimeMillis() - showTime < LOCK_MILLIS) return Action.NONE;

        switch (keyCode) {
            case java.awt.event.KeyEvent.VK_LEFT,
                 java.awt.event.KeyEvent.VK_A -> selectedIdx = 0;
            case java.awt.event.KeyEvent.VK_RIGHT,
                 java.awt.event.KeyEvent.VK_D -> selectedIdx = 1;
            case java.awt.event.KeyEvent.VK_R  -> { pendingAction = Action.RETRY;  return Action.RETRY; }
            case java.awt.event.KeyEvent.VK_ESCAPE -> { pendingAction = Action.EXIT; return Action.EXIT; }
            case java.awt.event.KeyEvent.VK_ENTER  -> {
                pendingAction = (selectedIdx == 0) ? Action.RETRY : Action.EXIT;
                return pendingAction;
            }
        }
        return Action.NONE;
    }

    public void draw(Graphics2D g, int viewW, int viewH) {
        if (!active) return;

        // Calcular alpha de fade-in propio
        long elapsed = System.currentTimeMillis() - showTime;
        float fadeAlpha = Math.min(1.0f, (float) elapsed / FADE_IN_MILLIS);

        drawContent(g, viewW, viewH, fadeAlpha);
    }

    // *** PRIVATE ***

    private void drawContent(Graphics2D g, int viewW, int viewH, float masterAlpha) {
        var origComposite = g.getComposite();
        var origHints     = g.getRenderingHints();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        // ── OVERLAY OSCURO con fade-in ────────────────────────────
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.82f * masterAlpha));
        GradientPaint bgGrad = new GradientPaint(0, 0, new Color(10, 2, 6, 230),
                0, viewH, new Color(4, 1, 4, 230));
        g.setPaint(bgGrad);
        g.fillRect(0, 0, viewW, viewH);

        // ── PANEL CENTRAL ────────────────────────────────────────
        int panelW = Math.min(560, viewW - 60);
        int panelH = 330;
        int panelX = (viewW - panelW) / 2;
        int panelY = (viewH - panelH) / 2;

        // Sombra
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f * masterAlpha));
        g.setColor(Color.BLACK);
        g.fillRoundRect(panelX + 5, panelY + 5, panelW, panelH, 22, 22);

        // Fondo gradiente
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.97f * masterAlpha));
        GradientPaint panelGrad = new GradientPaint(panelX, panelY, PANEL_BG, panelX, panelY + panelH, PANEL_BG2);
        g.setPaint(panelGrad);
        g.fillRoundRect(panelX, panelY, panelW, panelH, 20, 20);

        // Glow borde rojo exterior
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f * masterAlpha));
        g.setColor(GLOW_COLOR);
        g.setStroke(new BasicStroke(6f));
        g.drawRoundRect(panelX - 2, panelY - 2, panelW + 4, panelH + 4, 22, 22);

        // Borde principal
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, masterAlpha));
        g.setColor(BORDER_COLOR);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(panelX, panelY, panelW, panelH, 20, 20);

        // Línea accent superior roja
        GradientPaint accentLine = new GradientPaint(
                panelX + 40, panelY + 3, new Color(200, 40, 60, 0),
                panelX + panelW / 2, panelY + 3, new Color(220, 60, 80, 180));
        g.setPaint(accentLine);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(panelX + 40, panelY + 3, panelX + panelW - 40, panelY + 3);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, masterAlpha));

        int cx = panelX + panelW / 2;
        int y  = panelY + 58;

        // ── GAME OVER ────────────────────────────────────────────
        g.setFont(new Font("Monospaced", Font.BOLD, 44));
        FontMetrics fm = g.getFontMetrics();
        String goText = "GAME OVER";
        // Glow
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f * masterAlpha));
        g.setColor(TITLE_GLOW);
        g.setFont(new Font("Monospaced", Font.BOLD, 46));
        g.drawString(goText, cx - g.getFontMetrics().stringWidth(goText) / 2 - 1, y + 2);
        // Texto
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, masterAlpha));
        g.setFont(new Font("Monospaced", Font.BOLD, 44));
        fm = g.getFontMetrics();
        g.setColor(TITLE_COLOR);
        g.drawString(goText, cx - fm.stringWidth(goText) / 2, y);

        // ── ESTADÍSTICAS ─────────────────────────────────────────
        y += 40;
        // Fila: oleada
        drawStatRow(g, panelX, y, panelW, "OLEADA ALCANZADA", String.valueOf(waveReached), WAVE_VAL, masterAlpha);
        y += 28;
        // Fila: puntuación
        drawStatRow(g, panelX, y, panelW, "PUNTUACIÓN FINAL", String.format("%06d", finalScore), SCORE_VAL, masterAlpha);

        // ── SEPARADOR ────────────────────────────────────────────
        y += 22;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, masterAlpha));
        GradientPaint div = new GradientPaint(panelX + 30, y, new Color(160, 30, 50, 0),
                cx, y, DIVIDER_COL);
        g.setPaint(div);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(panelX + 30, y, panelX + panelW - 30, y);

        // ── BOTONES ──────────────────────────────────────────────
        y += 30;
        String[]  btnLabels  = { "REINTENTAR", "SALIR" };
        String[]  btnHints   = { "R  /  ENTER", "ESC" };
        Color[]   btnColors  = { new Color(60, 140, 255), new Color(200, 60, 80) };

        int bW = 178, bH = 48, bGap = 20;
        int totalBW = 2 * bW + bGap;
        int bx0 = cx - totalBW / 2;

        for (int i = 0; i < 2; i++) {
            int bx = bx0 + i * (bW + bGap);
            boolean sel = (selectedIdx == i);

            // Sombra
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f * masterAlpha));
            g.setColor(Color.BLACK);
            g.fillRoundRect(bx + 2, y + 2, bW, bH, 12, 12);

            // Fondo
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (sel ? 0.92f : 0.72f) * masterAlpha));
            if (sel) {
                GradientPaint btnG = new GradientPaint(bx, y, SEL_BG, bx, y + bH,
                        new Color(btnColors[i].getRed() / 4, btnColors[i].getGreen() / 4,
                                  btnColors[i].getBlue() / 4, 220));
                g.setPaint(btnG);
            } else {
                g.setColor(UNSEL_BG);
            }
            g.fillRoundRect(bx, y, bW, bH, 12, 12);

            // Borde (del color del botón si seleccionado)
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, masterAlpha));
            Color bdrCol = sel ? btnColors[i] : UNSEL_BORDER;
            g.setColor(new Color(bdrCol.getRed(), bdrCol.getGreen(), bdrCol.getBlue(),
                    sel ? 210 : 100));
            g.setStroke(new BasicStroke(sel ? 1.5f : 1f));
            g.drawRoundRect(bx, y, bW, bH, 12, 12);

            // Texto principal
            g.setFont(new Font("Monospaced", Font.BOLD, 14));
            fm = g.getFontMetrics();
            g.setColor(sel ? btnColors[i].brighter() : new Color(140, 145, 165));
            String lbl = btnLabels[i];
            g.drawString(lbl, bx + (bW - fm.stringWidth(lbl)) / 2, y + 20);

            // Tecla hint pequeña
            g.setFont(new Font("Monospaced", Font.PLAIN, 10));
            fm = g.getFontMetrics();
            g.setColor(sel ? new Color(btnColors[i].getRed(), btnColors[i].getGreen(),
                    btnColors[i].getBlue(), 180) : new Color(80, 85, 110));
            String hint = btnHints[i];
            g.drawString(hint, bx + (bW - fm.stringWidth(hint)) / 2, y + 35);
        }

        // Hint navegación
        y += bH + 16;
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        fm = g.getFontMetrics();
        g.setColor(new Color(HINT_COLOR.getRed(), HINT_COLOR.getGreen(),
                HINT_COLOR.getBlue(), (int)(255 * masterAlpha * 0.7f)));
        String nav = "←/→  navegar     ENTER  confirmar";
        g.drawString(nav, cx - fm.stringWidth(nav) / 2, y);

        g.setComposite(origComposite);
        g.setRenderingHints(origHints);
    }

    private void drawStatRow(Graphics2D g, int panelX, int y, int panelW,
                             String label, String value, Color valueColor, float alpha) {
        int cx = panelX + panelW / 2;

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // Fondo fila tenue
        g.setColor(new Color(255, 255, 255, 8));
        g.fillRoundRect(panelX + 30, y - 14, panelW - 60, 20, 6, 6);

        // Label izquierda
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(STAT_LABEL);
        g.drawString(label, panelX + 44, y);

        // Valor derecha
        g.setFont(new Font("Monospaced", Font.BOLD, 16));
        fm = g.getFontMetrics();
        g.setColor(valueColor);
        g.drawString(value, panelX + panelW - 44 - fm.stringWidth(value), y);
    }
}
