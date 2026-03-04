package engine.view.hud.impl;

import java.awt.*;
import java.awt.geom.*;
import java.util.Random;

/**
 * IntroHUD — Pantalla de inicio con estética espacial retro.
 */
public class IntroHUD {

    // Paleta espacial coherente con PlayerHUD / WaveHUD
    private static final Color BG_DEEP      = new Color(4, 6, 18);
    private static final Color PANEL_BG     = new Color(10, 14, 36, 235);
    private static final Color PANEL_BG2    = new Color(6, 8, 22, 235);
    private static final Color BORDER_COLOR = new Color(80, 130, 255, 160);
    private static final Color GLOW_COLOR   = new Color(60, 100, 255, 35);
    private static final Color TITLE_COLOR  = new Color(100, 200, 255);
    private static final Color TITLE_GLOW   = new Color(60, 140, 255, 80);
    private static final Color SUBTITLE_COL = new Color(140, 160, 220);
    private static final Color SECTION_COL  = new Color(80, 130, 255);
    private static final Color KEY_COLOR    = new Color(255, 210, 60);
    private static final Color DESC_COLOR   = new Color(200, 210, 230);
    private static final Color DIVIDER_COL  = new Color(60, 80, 160, 100);
    private static final Color BLINK_COLOR  = new Color(100, 220, 255);
    private static final Color SEL_BG       = new Color(0, 50, 120, 220);
    private static final Color SEL_BORDER   = new Color(80, 180, 255, 230);
    private static final Color UNSEL_BG     = new Color(12, 16, 40, 200);
    private static final Color UNSEL_BORDER = new Color(50, 70, 120, 160);

    public static final int DIFFICULTY_EASY   = 1;
    public static final int DIFFICULTY_NORMAL = 2;
    public static final int DIFFICULTY_HARD   = 3;

    private boolean active     = true;
    private int     difficulty = DIFFICULTY_NORMAL;
    private final long startTime = System.currentTimeMillis();

    // Estrellas de fondo
    private static final int STAR_COUNT = 180;
    private final int[]   starX, starY;
    private final float[] starSize, starAlpha;

    private int[] btnX = new int[3];
    private int[] btnY = new int[3];
    private int   btnW = 150, btnH = 36;

    public IntroHUD() {
        Random rnd = new Random(42);
        starX     = new int[STAR_COUNT];
        starY     = new int[STAR_COUNT];
        starSize  = new float[STAR_COUNT];
        starAlpha = new float[STAR_COUNT];
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i]     = rnd.nextInt(1920);
            starY[i]     = rnd.nextInt(1080);
            starSize[i]  = 0.5f + rnd.nextFloat() * 2.0f;
            starAlpha[i] = 0.2f + rnd.nextFloat() * 0.7f;
        }
    }

    public boolean isActive()  { return active; }
    public int getDifficulty() { return difficulty; }

    public boolean handleKey(int keyCode) {
        if (!active) return false;
        switch (keyCode) {
            case java.awt.event.KeyEvent.VK_1 -> difficulty = DIFFICULTY_EASY;
            case java.awt.event.KeyEvent.VK_2 -> difficulty = DIFFICULTY_NORMAL;
            case java.awt.event.KeyEvent.VK_3 -> difficulty = DIFFICULTY_HARD;
            case java.awt.event.KeyEvent.VK_LEFT,
                 java.awt.event.KeyEvent.VK_A -> difficulty = Math.max(DIFFICULTY_EASY, difficulty - 1);
            case java.awt.event.KeyEvent.VK_RIGHT,
                 java.awt.event.KeyEvent.VK_D -> difficulty = Math.min(DIFFICULTY_HARD, difficulty + 1);
            case java.awt.event.KeyEvent.VK_ENTER,
                 java.awt.event.KeyEvent.VK_SPACE -> { active = false; return true; }
        }
        return false;
    }

    public boolean handleMouseClick(int mx, int my) {
        if (!active) return false;
        for (int i = 0; i < 3; i++) {
            if (mx >= btnX[i] && mx <= btnX[i] + btnW &&
                my >= btnY[i] && my <= btnY[i] + btnH) {
                difficulty = i + 1;
                return false;
            }
        }
        return false;
    }

    public void draw(Graphics2D g, int viewW, int viewH) {
        if (!active) return;

        var origComposite = g.getComposite();
        var origHints     = g.getRenderingHints();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        long now = System.currentTimeMillis();

        // ── FONDO ────────────────────────────────────────────────
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        GradientPaint bgGrad = new GradientPaint(0, 0, BG_DEEP, 0, viewH, new Color(6, 4, 20));
        g.setPaint(bgGrad);
        g.fillRect(0, 0, viewW, viewH);

        // Estrellas
        for (int i = 0; i < STAR_COUNT; i++) {
            int sx = starX[i] * viewW / 1920;
            int sy = starY[i] * viewH / 1080;
            float twinkle = starAlpha[i] * (0.7f + 0.3f * (float) Math.abs(Math.sin((now / 1200.0 + i * 0.4))));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, twinkle));
            g.setColor(Color.WHITE);
            float s = starSize[i];
            g.fill(new Ellipse2D.Float(sx - s / 2, sy - s / 2, s, s));
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        // ── PANEL CENTRAL ────────────────────────────────────────
        int panelW = Math.min(720, viewW - 60);
        int panelH = 530;
        int panelX = (viewW - panelW) / 2;
        int panelY = (viewH - panelH) / 2;

        // Sombra panel
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        g.setColor(Color.BLACK);
        g.fillRoundRect(panelX + 5, panelY + 5, panelW, panelH, 22, 22);

        // Fondo panel gradiente
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.97f));
        GradientPaint panelGrad = new GradientPaint(panelX, panelY, PANEL_BG, panelX, panelY + panelH, PANEL_BG2);
        g.setPaint(panelGrad);
        g.fillRoundRect(panelX, panelY, panelW, panelH, 20, 20);

        // Glow borde exterior
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        g.setColor(GLOW_COLOR);
        g.setStroke(new BasicStroke(5f));
        g.drawRoundRect(panelX - 2, panelY - 2, panelW + 4, panelH + 4, 22, 22);

        // Borde principal
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.setColor(BORDER_COLOR);
        g.setStroke(new BasicStroke(1.3f));
        g.drawRoundRect(panelX, panelY, panelW, panelH, 20, 20);

        // Línea decorativa superior (accent)
        GradientPaint accentLine = new GradientPaint(
                panelX + 40, panelY + 3, new Color(80, 130, 255, 0),
                panelX + panelW / 2, panelY + 3, new Color(100, 180, 255, 200));
        g.setPaint(accentLine);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(panelX + 40, panelY + 3, panelX + panelW - 40, panelY + 3);

        int cx = panelX + panelW / 2;
        int y  = panelY + 56;

        // ── TÍTULO ───────────────────────────────────────────────
        g.setFont(new Font("Monospaced", Font.BOLD, 38));
        FontMetrics fm = g.getFontMetrics();
        String title = "ASTEROID SURVIVOR";
        // Glow del título
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        g.setColor(TITLE_GLOW);
        g.setFont(new Font("Monospaced", Font.BOLD, 40));
        g.drawString(title, cx - g.getFontMetrics().stringWidth(title) / 2 - 1, y + 1);
        // Título principal
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.setFont(new Font("Monospaced", Font.BOLD, 38));
        fm = g.getFontMetrics();
        g.setColor(TITLE_COLOR);
        g.drawString(title, cx - fm.stringWidth(title) / 2, y);

        // Subtítulo
        y += 30;
        g.setFont(new Font("Monospaced", Font.ITALIC, 14));
        fm = g.getFontMetrics();
        g.setColor(SUBTITLE_COL);
        String sub = "Sobrevive a las oleadas de naves enemigas";
        g.drawString(sub, cx - fm.stringWidth(sub) / 2, y);

        // ── SEPARADOR ────────────────────────────────────────────
        y += 16;
        drawDivider(g, panelX, y, panelW);

        // ── CONTROLES ────────────────────────────────────────────
        y += 22;
        drawSectionTitle(g, cx, y, "CONTROLES");
        y += 24;

        int colKey  = panelX + 44;
        int colDesc = panelX + 220;
        int step    = 26;
        drawControl(g, colKey, colDesc, y,          "W / ↑",         "Empuje adelante");
        drawControl(g, colKey, colDesc, y + step,   "S / ↓",         "Frenar");
        drawControl(g, colKey, colDesc, y + step*2, "A / ←   D / →", "Rotar nave");
        drawControl(g, colKey, colDesc, y + step*3, "ESPACIO",        "Disparar");
        drawControl(g, colKey, colDesc, y + step*4, "TAB",            "Cambiar arma");
        drawControl(g, colKey, colDesc, y + step*5, "SHIFT",          "Boost");

        // ── SEPARADOR ────────────────────────────────────────────
        y += step * 6 + 10;
        drawDivider(g, panelX, y, panelW);

        // ── DIFICULTAD ───────────────────────────────────────────
        y += 22;
        drawSectionTitle(g, cx, y, "DIFICULTAD");
        y += 30;

        String[] labels     = { "FÁCIL", "NORMAL", "DIFÍCIL" };
        String[] subLabels  = { "+1 nave/oleada", "+2 naves/oleada", "+3 naves/oleada" };
        Color[]  diffColors = {
            new Color(60, 200, 100),
            new Color(100, 180, 255),
            new Color(255, 80, 80)
        };
        int[] diffs = { DIFFICULTY_EASY, DIFFICULTY_NORMAL, DIFFICULTY_HARD };
        int gap = 16;
        int totalBtnW = 3 * btnW + 2 * gap;
        int bx0 = cx - totalBtnW / 2;

        for (int i = 0; i < 3; i++) {
            int bx = bx0 + i * (btnW + gap);
            boolean sel = (difficulty == diffs[i]);
            btnX[i] = bx; btnY[i] = y - 4;

            // Sombra botón
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));
            g.setColor(Color.BLACK);
            g.fillRoundRect(bx + 2, y - 2, btnW, btnH, 10, 10);

            // Fondo botón
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, sel ? 0.95f : 0.75f));
            if (sel) {
                GradientPaint btnGrad = new GradientPaint(bx, y, SEL_BG,
                        bx, y + btnH, new Color(0, 30, 80, 220));
                g.setPaint(btnGrad);
            } else {
                g.setColor(UNSEL_BG);
            }
            g.fillRoundRect(bx, y - 4, btnW, btnH, 10, 10);

            // Borde botón (del color de dificultad si seleccionado)
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            Color bdrColor = sel ? diffColors[i] : UNSEL_BORDER;
            g.setColor(new Color(bdrColor.getRed(), bdrColor.getGreen(), bdrColor.getBlue(),
                    sel ? 220 : 120));
            g.setStroke(new BasicStroke(sel ? 1.5f : 1f));
            g.drawRoundRect(bx, y - 4, btnW, btnH, 10, 10);

            // Texto principal
            g.setFont(new Font("Monospaced", Font.BOLD, 13));
            fm = g.getFontMetrics();
            g.setColor(sel ? diffColors[i] : new Color(140, 150, 180));
            String lbl = (i + 1) + " — " + labels[i];
            g.drawString(lbl, bx + (btnW - fm.stringWidth(lbl)) / 2, y + 11);

            // Subtexto pequeño
            g.setFont(new Font("Monospaced", Font.PLAIN, 9));
            fm = g.getFontMetrics();
            g.setColor(sel ? new Color(diffColors[i].getRed(), diffColors[i].getGreen(),
                    diffColors[i].getBlue(), 180) : new Color(90, 100, 130));
            g.drawString(subLabels[i], bx + (btnW - fm.stringWidth(subLabels[i])) / 2, y + 24);
        }

        // ── SEPARADOR ────────────────────────────────────────────
        y += btnH + 14;
        drawDivider(g, panelX, y, panelW);

        // ── ENTER para comenzar (parpadeo) ───────────────────────
        y += 28;
        float blinkAlpha = 0.5f + 0.5f * (float) Math.abs(Math.sin(now / 600.0));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, blinkAlpha));
        g.setFont(new Font("Monospaced", Font.BOLD, 17));
        fm = g.getFontMetrics();
        String hint = "▶   ENTER  para comenzar   ◀";
        // Sombra texto
        g.setColor(new Color(0, 0, 0, 120));
        g.drawString(hint, cx - fm.stringWidth(hint) / 2 + 1, y + 1);
        g.setColor(BLINK_COLOR);
        g.drawString(hint, cx - fm.stringWidth(hint) / 2, y);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        g.setComposite(origComposite);
        g.setRenderingHints(origHints);
    }

    // *** PRIVATE ***

    private void drawDivider(Graphics2D g, int panelX, int y, int panelW) {
        GradientPaint gp = new GradientPaint(
                panelX + 30, y, new Color(60, 80, 160, 0),
                panelX + panelW / 2, y, new Color(80, 120, 220, 160));
        g.setPaint(gp);
        g.setStroke(new BasicStroke(1f));
        g.drawLine(panelX + 30, y, panelX + panelW - 30, y);
    }

    private void drawSectionTitle(Graphics2D g, int cx, int y, String text) {
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(SECTION_COL);
        g.drawString(text, cx - fm.stringWidth(text) / 2, y);
    }

    private void drawControl(Graphics2D g, int colKey, int colDesc, int y, String key, String desc) {
        // Cápsula de fondo para la tecla
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        int kw = fm.stringWidth(key) + 14;
        g.setColor(new Color(20, 30, 70, 180));
        g.fillRoundRect(colKey - 5, y - 13, kw, 18, 6, 6);
        g.setColor(new Color(60, 90, 180, 140));
        g.setStroke(new BasicStroke(0.8f));
        g.drawRoundRect(colKey - 5, y - 13, kw, 18, 6, 6);
        g.setColor(KEY_COLOR);
        g.drawString(key, colKey, y);

        g.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g.setColor(DESC_COLOR);
        g.drawString(desc, colDesc, y);
    }
}
