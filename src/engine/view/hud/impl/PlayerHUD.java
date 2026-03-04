package engine.view.hud.impl;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import engine.model.bodies.ports.PlayerDTO;

/**
 * PlayerHUD — Panel inferior izquierdo.
 * Muestra: Vidas (corazones), Boost y arma activa.
 */
public class PlayerHUD {

    // Paleta de colores retro-espacial
    private static final Color BG_COLOR      = new Color(8, 10, 24, 210);
    private static final Color BORDER_COLOR  = new Color(80, 130, 255, 160);
    private static final Color GLOW_COLOR    = new Color(60, 100, 255, 40);
    private static final Color LABEL_COLOR   = new Color(120, 140, 200);
    private static final Color HEART_FULL    = new Color(240, 60, 80);
    private static final Color HEART_GLOW    = new Color(255, 80, 100, 80);
    private static final Color HEART_EMPTY   = new Color(60, 20, 30, 200);
    private static final Color BOOST_HI      = new Color(80, 220, 255);
    private static final Color BOOST_LO      = new Color(255, 80, 50);
    private static final Color BOOST_EMPTY   = new Color(30, 50, 70);
    private static final Color WEAPON_COLOR  = new Color(160, 255, 200);
    private static final Color WEAPON_ACTIVE = new Color(0, 220, 140);
    private static final Color DIVIDER       = new Color(60, 80, 160, 100);

    private static final int PAD      = 14;
    private static final int PANEL_W  = 240;
    private static final int MAX_HP   = 3;
    private static final String[] WEAPON_NAMES  = { "CAÑÓN", "MISILES" };
    private static final String[] WEAPON_ICONS  = { "◈", "◉" };

    public void draw(Graphics2D g, PlayerDTO data, int viewW) {
        if (data == null) return;

        var origComposite = g.getComposite();
        var origHints     = g.getRenderingHints();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        int panelH = PAD + 28 + 8 + 22 + 8 + 24 + PAD;
        int panelX = 14;
        int panelY = 14;

        // Sombra exterior
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
        g.setColor(Color.BLACK);
        g.fillRoundRect(panelX + 3, panelY + 3, PANEL_W + 4, panelH + 4, 16, 16);

        // Fondo con degradado sutil
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));
        GradientPaint bgGrad = new GradientPaint(
                panelX, panelY,            new Color(12, 16, 40, 230),
                panelX, panelY + panelH,   new Color(6, 8, 22, 230));
        g.setPaint(bgGrad);
        g.fillRoundRect(panelX, panelY, PANEL_W, panelH, 14, 14);

        // Borde con glow
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        g.setColor(GLOW_COLOR);
        g.setStroke(new BasicStroke(4f));
        g.drawRoundRect(panelX - 1, panelY - 1, PANEL_W + 2, panelH + 2, 15, 15);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.setColor(BORDER_COLOR);
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(panelX, panelY, PANEL_W, panelH, 14, 14);

        int y = panelY + PAD;

        // ── VIDA ──────────────────────────────────────────────────
        drawSectionLabel(g, panelX + PAD, y + 10, "VIDA");
        drawHearts(g, panelX + PAD + 52, y - 2, data.playerHp);
        y += 28;

        // Línea divisora
        g.setColor(DIVIDER);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(panelX + PAD, y, panelX + PANEL_W - PAD, y);
        y += 8;

        // ── BOOST ─────────────────────────────────────────────────
        double boost = Math.max(0, Math.min(1, data.boostEnergy));
        drawSectionLabel(g, panelX + PAD, y + 12, "BOOST");
        drawBoostBar(g, panelX + PAD + 52, y + 2, PANEL_W - PAD * 2 - 52, 18, boost);
        y += 22;

        // Línea divisora
        g.setColor(DIVIDER);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(panelX + PAD, y + 6, panelX + PANEL_W - PAD, y + 6);
        y += 14;

        // ── ARMA ──────────────────────────────────────────────────
        drawSectionLabel(g, panelX + PAD, y + 12, "ARMA");
        drawWeapon(g, panelX + PAD + 52, y, data.activeWeapon);

        g.setComposite(origComposite);
        g.setRenderingHints(origHints);
    }

    private void drawSectionLabel(Graphics2D g, int x, int y, String text) {
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.setColor(LABEL_COLOR);
        g.drawString(text, x, y);
    }

    private void drawHearts(Graphics2D g, int startX, int y, int hp) {
        int size = 22;
        int gap  = 8;
        for (int i = 0; i < MAX_HP; i++) {
            boolean full = (i < hp);
            int hx = startX + i * (size + gap);
            if (full) {
                // Glow bajo el corazón lleno
                g.setColor(HEART_GLOW);
                g.fillOval(hx - 3, y - 1, size + 6, size + 6);
            }
            drawHeart(g, hx, y, size, full ? HEART_FULL : HEART_EMPTY, full);
        }
    }

    private void drawHeart(Graphics2D g, int x, int y, int s, Color color, boolean filled) {
        // Corazón usando path bezier para mejor aspecto
        GeneralPath path = new GeneralPath();
        float cx = x + s * 0.5f;
        float cy = y + s * 0.5f;
        float r  = s * 0.28f;
        // Corazón: dos círculos arriba + V abajo
        path.moveTo(cx, y + s * 0.95f);
        path.curveTo(x, cy, x, y + r, x + r, y + r);
        path.curveTo(cx - r * 0.3f, y + r * 0.1f, cx, y + r * 0.9f, cx, y + r * 0.9f);
        path.curveTo(cx, y + r * 0.9f, cx + r * 0.3f, y + r * 0.1f, x + s - r, y + r);
        path.curveTo(x + s, y + r, x + s, cy, cx, y + s * 0.95f);
        path.closePath();

        if (filled) {
            GradientPaint gp = new GradientPaint(x, y, color.brighter(), x, y + s, color.darker().darker());
            g.setPaint(gp);
            g.fill(path);
        } else {
            g.setColor(HEART_EMPTY);
            g.fill(path);
        }
        g.setStroke(new BasicStroke(filled ? 1.2f : 1f));
        g.setColor(filled ? color.brighter() : new Color(80, 20, 30, 120));
        g.draw(path);
    }

    private void drawBoostBar(Graphics2D g, int x, int y, int w, int h, double value) {
        // Fondo
        g.setColor(BOOST_EMPTY);
        g.fillRoundRect(x, y, w, h, 6, 6);

        // Relleno con gradiente cyan→azul (o rojo si bajo)
        int fillW = (int)(w * value);
        if (fillW > 1) {
            Color lo = value < 0.25 ? BOOST_LO : BOOST_HI.darker();
            Color hi = value < 0.25 ? BOOST_LO.brighter() : BOOST_HI;
            GradientPaint gp = new GradientPaint(x, y, lo, x + fillW, y, hi);
            g.setPaint(gp);
            g.fillRoundRect(x, y, fillW, h, 6, 6);

            // Brillo en la parte superior
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            g.setColor(Color.WHITE);
            g.fillRoundRect(x + 1, y + 1, fillW - 2, h / 2, 4, 4);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }

        // Borde
        g.setColor(new Color(80, 140, 200, 150));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(x, y, w, h, 6, 6);

        // Texto % pequeño
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.setColor(value < 0.25 ? BOOST_LO : BOOST_HI);
        String pct = (int)(value * 100) + "%";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(pct, x + w - fm.stringWidth(pct) - 3, y + h - 2);
    }

    private void drawWeapon(Graphics2D g, int x, int y, int activeWeapon) {
        int boxW = PANEL_W - PAD * 2 - 52;
        int boxH = 22;

        // Fondo cápsula del arma activa
        g.setColor(new Color(0, 60, 40, 180));
        g.fillRoundRect(x, y, boxW, boxH, 8, 8);
        g.setColor(WEAPON_ACTIVE);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(x, y, boxW, boxH, 8, 8);

        String icon = (activeWeapon >= 0 && activeWeapon < WEAPON_ICONS.length)
                ? WEAPON_ICONS[activeWeapon] : "◈";
        String name = (activeWeapon >= 0 && activeWeapon < WEAPON_NAMES.length)
                ? WEAPON_NAMES[activeWeapon] : "-";

        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        int totalW = fm.stringWidth(icon + " " + name);
        int tx = x + (boxW - totalW) / 2;

        g.setColor(WEAPON_ACTIVE);
        g.drawString(icon, tx, y + boxH - 5);
        g.setColor(WEAPON_COLOR);
        g.drawString(" " + name, tx + fm.stringWidth(icon), y + boxH - 5);
    }
}
