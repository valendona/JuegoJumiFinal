package engine.view.hud.impl;

import java.awt.*;
import java.awt.geom.*;

/**
 * BossHUD — Barra de vida del boss/miniboss, centrada en la parte inferior.
 */
public class BossHUD {

    private static final Color BG_COLOR        = new Color(8, 10, 24, 215);
    private static final Color BORDER_BOSS     = new Color(220, 50, 50, 220);
    private static final Color BORDER_MINIBOSS = new Color(180, 60, 240, 220);
    private static final Color GLOW_BOSS       = new Color(200, 30, 30, 50);
    private static final Color GLOW_MINIBOSS   = new Color(160, 40, 220, 50);
    private static final Color BAR_BOSS_HI     = new Color(255, 80, 60);
    private static final Color BAR_BOSS_LO     = new Color(180, 30, 20);
    private static final Color BAR_MINI_HI     = new Color(200, 80, 255);
    private static final Color BAR_MINI_LO     = new Color(120, 30, 180);
    private static final Color BAR_CRITICAL     = new Color(255, 130, 0);
    private static final Color SEGMENT_COLOR   = new Color(0, 0, 0, 100);
    private static final Color LABEL_COLOR     = new Color(220, 220, 220);

    private int     hpCurrent = 0;
    private int     hpMax     = 1;
    private boolean isBoss    = false;

    public void set(int current, int max, boolean isBoss) {
        this.hpCurrent = Math.max(0, current);
        this.hpMax     = Math.max(1, max);
        this.isBoss    = isBoss;
    }

    public void clear()         { this.hpCurrent = 0; }
    public boolean isVisible()  { return hpCurrent > 0; }

    public void draw(Graphics2D g, int viewW, int viewH) {
        if (!isVisible()) return;

        var origComposite = g.getComposite();
        var origHints     = g.getRenderingHints();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        double ratio = (double) hpCurrent / hpMax;
        boolean critical = ratio < 0.25;

        Color borderColor = isBoss ? BORDER_BOSS     : BORDER_MINIBOSS;
        Color glowColor   = isBoss ? GLOW_BOSS       : GLOW_MINIBOSS;
        Color barHi       = critical ? BAR_CRITICAL  : (isBoss ? BAR_BOSS_HI : BAR_MINI_HI);
        Color barLo       = critical ? BAR_CRITICAL.darker() : (isBoss ? BAR_BOSS_LO : BAR_MINI_LO);

        // Pulso lento cuando está en estado crítico
        float pulse = 1.0f;
        if (critical) {
            pulse = 0.65f + 0.35f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 350.0));
        }

        int barW   = Math.min(620, viewW - 100);
        int barH   = 24;
        int panelW = barW + 90;
        int panelH = barH + 44;
        int panelX = (viewW - panelW) / 2;
        int panelY = viewH - panelH - 24;

        // Sombra
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        g.setColor(Color.BLACK);
        g.fillRoundRect(panelX + 4, panelY + 4, panelW, panelH, 18, 18);

        // Fondo
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));
        GradientPaint bgGrad = new GradientPaint(
                panelX, panelY,           new Color(14, 6, 10, 228),
                panelX, panelY + panelH,  new Color(6, 4, 8, 228));
        g.setPaint(bgGrad);
        g.fillRoundRect(panelX, panelY, panelW, panelH, 16, 16);

        // Glow externo pulsante
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f * pulse));
        g.setColor(glowColor);
        g.setStroke(new BasicStroke(6f));
        g.drawRoundRect(panelX - 2, panelY - 2, panelW + 4, panelH + 4, 18, 18);

        // Borde principal pulsante
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (0.7f + 0.3f * pulse)));
        g.setColor(borderColor);
        g.setStroke(new BasicStroke(1.8f));
        g.drawRoundRect(panelX, panelY, panelW, panelH, 16, 16);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        // Etiqueta
        String label = isBoss ? "✦  JEFE FINAL  ✦" : "◆  MINI-JEFE  ◆";
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        // Sombra texto
        g.setColor(new Color(0, 0, 0, 160));
        g.drawString(label, (viewW - fm.stringWidth(label)) / 2 + 1, panelY + 20 + 1);
        g.setColor(new Color(borderColor.getRed(), borderColor.getGreen(),
                borderColor.getBlue(), (int)(220 * (0.75f + 0.25f * pulse))));
        g.drawString(label, (viewW - fm.stringWidth(label)) / 2, panelY + 20);

        // Barra
        int barX = panelX + (panelW - barW) / 2;
        int barY = panelY + 26;

        // Fondo barra
        g.setColor(new Color(20, 8, 10, 220));
        g.fillRoundRect(barX, barY, barW, barH, 8, 8);

        // Relleno con gradiente
        int fillW = (int)(barW * ratio);
        if (fillW > 2) {
            GradientPaint barGrad = new GradientPaint(barX, barY, barHi, barX + fillW, barY, barLo);
            g.setPaint(barGrad);
            g.fillRoundRect(barX, barY, fillW, barH, 8, 8);

            // Brillo superior
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
            g.setColor(Color.WHITE);
            g.fillRoundRect(barX + 1, barY + 1, fillW - 2, barH / 2, 5, 5);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }

        // Segmentos divisores
        if (hpMax > 1 && hpMax <= 30) {
            g.setColor(SEGMENT_COLOR);
            g.setStroke(new BasicStroke(1.5f));
            for (int i = 1; i < hpMax; i++) {
                int sx = barX + (int)((double) i / hpMax * barW);
                g.drawLine(sx, barY + 3, sx, barY + barH - 3);
            }
        }

        // Borde barra pulsante
        g.setColor(new Color(borderColor.getRed(), borderColor.getGreen(),
                borderColor.getBlue(), (int)(180 * pulse)));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(barX, barY, barW, barH, 8, 8);

        g.setComposite(origComposite);
        g.setRenderingHints(origHints);
    }
}
