package engine.view.hud.impl;

import java.awt.*;
import java.awt.geom.*;

/** PauseHUD — Pantalla de pausa con estética espacial coherente. */
public class PauseHUD {

    private static final Color PANEL_BG     = new Color(10, 14, 36, 240);
    private static final Color PANEL_BG2    = new Color(6, 8, 22, 240);
    private static final Color BORDER_COLOR = new Color(80, 130, 255, 160);
    private static final Color GLOW_COLOR   = new Color(60, 100, 255, 35);
    private static final Color TITLE_COLOR  = new Color(255, 210, 60);
    private static final Color TITLE_GLOW   = new Color(200, 160, 20, 80);
    private static final Color UNSEL_BG     = new Color(12, 16, 40, 200);
    private static final Color UNSEL_BORDER = new Color(50, 70, 120, 150);
    private static final Color HINT_COLOR   = new Color(80, 90, 120);

    private static final Color[] BTN_COLORS = {
        new Color(60, 200, 120),   // Reanudar — verde
        new Color(100, 160, 255),  // Reiniciar — azul
        new Color(200, 70, 70)     // Salir — rojo
    };

    public enum Action { NONE, RESUME, RESTART, EXIT }

    private boolean active      = false;
    private int     selectedIdx = 0;

    public boolean isActive()  { return active; }
    public void show()         { active = true; selectedIdx = 0; }
    public void hide()         { active = false; }
    public void toggle()       { if (active) hide(); else show(); }

    public Action handleKey(int keyCode) {
        if (!active) return Action.NONE;
        switch (keyCode) {
            case java.awt.event.KeyEvent.VK_LEFT:
            case java.awt.event.KeyEvent.VK_A:
                selectedIdx = (selectedIdx + 2) % 3;
                return Action.NONE;
            case java.awt.event.KeyEvent.VK_RIGHT:
            case java.awt.event.KeyEvent.VK_D:
                selectedIdx = (selectedIdx + 1) % 3;
                return Action.NONE;
            case java.awt.event.KeyEvent.VK_ESCAPE:
            case java.awt.event.KeyEvent.VK_P:
                active = false;
                return Action.RESUME;
            case java.awt.event.KeyEvent.VK_ENTER:
                return confirm();
        }
        return Action.NONE;
    }

    private Action confirm() {
        active = false;
        return switch (selectedIdx) {
            case 0 -> Action.RESUME;
            case 1 -> Action.RESTART;
            default -> Action.EXIT;
        };
    }

    public void draw(Graphics2D g, int viewW, int viewH) {
        if (!active) return;

        var origComposite = g.getComposite();
        var origHints     = g.getRenderingHints();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        // Overlay semitransparente
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.65f));
        GradientPaint bgGrad = new GradientPaint(0, 0, new Color(4, 6, 18, 200),
                0, viewH, new Color(6, 4, 20, 200));
        g.setPaint(bgGrad);
        g.fillRect(0, 0, viewW, viewH);

        // ── PANEL ────────────────────────────────────────────────
        int panelW = Math.min(520, viewW - 60);
        int panelH = 260;
        int panelX = (viewW - panelW) / 2;
        int panelY = (viewH - panelH) / 2;

        // Sombra
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        g.setColor(Color.BLACK);
        g.fillRoundRect(panelX + 5, panelY + 5, panelW, panelH, 22, 22);

        // Fondo gradiente
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.97f));
        GradientPaint panelGrad = new GradientPaint(panelX, panelY, PANEL_BG, panelX, panelY + panelH, PANEL_BG2);
        g.setPaint(panelGrad);
        g.fillRoundRect(panelX, panelY, panelW, panelH, 20, 20);

        // Glow
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        g.setColor(GLOW_COLOR);
        g.setStroke(new BasicStroke(5f));
        g.drawRoundRect(panelX - 2, panelY - 2, panelW + 4, panelH + 4, 22, 22);

        // Borde
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.setColor(BORDER_COLOR);
        g.setStroke(new BasicStroke(1.3f));
        g.drawRoundRect(panelX, panelY, panelW, panelH, 20, 20);

        // Línea accent amarilla
        GradientPaint accentLine = new GradientPaint(
                panelX + 40, panelY + 3, new Color(200, 160, 20, 0),
                panelX + panelW / 2, panelY + 3, new Color(220, 180, 40, 180));
        g.setPaint(accentLine);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(panelX + 40, panelY + 3, panelX + panelW - 40, panelY + 3);

        int cx = viewW / 2;
        int y  = panelY + 54;

        // ── TÍTULO ───────────────────────────────────────────────
        g.setFont(new Font("Monospaced", Font.BOLD, 32));
        FontMetrics fm = g.getFontMetrics();
        String title = "PAUSA";
        // Glow
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        g.setColor(TITLE_GLOW);
        g.setFont(new Font("Monospaced", Font.BOLD, 34));
        g.drawString(title, cx - g.getFontMetrics().stringWidth(title) / 2 - 1, y + 1);
        // Texto
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.setFont(new Font("Monospaced", Font.BOLD, 32));
        fm = g.getFontMetrics();
        g.setColor(TITLE_COLOR);
        g.drawString(title, cx - fm.stringWidth(title) / 2, y);

        // Divisor
        y += 22;
        GradientPaint div = new GradientPaint(panelX + 30, y, new Color(200, 160, 20, 0),
                cx, y, new Color(200, 160, 20, 80));
        g.setPaint(div);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(panelX + 30, y, panelX + panelW - 30, y);
        y += 32;

        // ── BOTONES ──────────────────────────────────────────────
        String[] labels   = { "REANUDAR", "REINICIAR", "SALIR" };
        String[] hints    = { "ESC/P",    "—",         "—" };
        int bW = 136, bH = 50, bGap = 14;
        int totalBW = 3 * bW + 2 * bGap;
        int bx0 = cx - totalBW / 2;

        for (int i = 0; i < 3; i++) {
            int bx = bx0 + i * (bW + bGap);
            boolean sel = (selectedIdx == i);
            Color bColor = BTN_COLORS[i];

            // Sombra
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
            g.setColor(Color.BLACK);
            g.fillRoundRect(bx + 2, y + 2, bW, bH, 12, 12);

            // Fondo
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, sel ? 0.92f : 0.72f));
            if (sel) {
                GradientPaint btnG = new GradientPaint(bx, y, new Color(0, 40, 90, 220),
                        bx, y + bH, new Color(bColor.getRed() / 5, bColor.getGreen() / 5,
                                bColor.getBlue() / 5, 220));
                g.setPaint(btnG);
            } else {
                g.setColor(UNSEL_BG);
            }
            g.fillRoundRect(bx, y, bW, bH, 12, 12);

            // Borde
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            Color bdr = sel ? bColor : UNSEL_BORDER;
            g.setColor(new Color(bdr.getRed(), bdr.getGreen(), bdr.getBlue(), sel ? 210 : 100));
            g.setStroke(new BasicStroke(sel ? 1.5f : 1f));
            g.drawRoundRect(bx, y, bW, bH, 12, 12);

            // Label
            g.setFont(new Font("Monospaced", Font.BOLD, 13));
            fm = g.getFontMetrics();
            g.setColor(sel ? bColor.brighter() : new Color(140, 145, 165));
            g.drawString(labels[i], bx + (bW - fm.stringWidth(labels[i])) / 2, y + 20);

            // Hint tecla
            g.setFont(new Font("Monospaced", Font.PLAIN, 10));
            fm = g.getFontMetrics();
            g.setColor(sel ? new Color(bColor.getRed(), bColor.getGreen(), bColor.getBlue(), 160)
                           : new Color(70, 80, 110));
            g.drawString(hints[i], bx + (bW - fm.stringWidth(hints[i])) / 2, y + 36);
        }

        // Hint navegación
        y += bH + 18;
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        fm = g.getFontMetrics();
        g.setColor(HINT_COLOR);
        String nav = "←/→  navegar     ENTER  confirmar     ESC  reanudar";
        g.drawString(nav, cx - fm.stringWidth(nav) / 2, y);

        g.setComposite(origComposite);
        g.setRenderingHints(origHints);
    }
}
