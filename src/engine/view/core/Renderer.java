package engine.view.core;

import java.awt.AlphaComposite;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import engine.controller.mappers.DynamicRenderableMapper;
import engine.controller.ports.EngineState;
import engine.utils.images.ImageCache;
import engine.utils.images.Images;
import engine.utils.profiling.impl.RendererProfiler;
import engine.utils.helpers.DoubleVector;
import engine.view.renderables.impl.ExplosionRenderable;
import engine.view.renderables.ports.RenderDTO;
import engine.view.hud.impl.InstrumentationHUD;
import engine.view.hud.impl.GameOverHUD;
import engine.view.hud.impl.VictoryHUD;
import engine.view.hud.impl.IntroHUD;
import engine.view.hud.impl.PauseHUD;
import engine.view.hud.impl.PlayerHUD;
import engine.view.hud.impl.ScoreHUD;
import engine.view.hud.impl.WaveHUD;
import engine.view.hud.impl.SpatialGridHUD;
import engine.view.hud.impl.SystemHUD;
import engine.view.renderables.impl.DynamicRenderable;
import engine.view.renderables.impl.Renderable;
import engine.utils.pooling.PoolMDTO;
import engine.view.renderables.ports.DynamicRenderDTO;
import engine.view.renderables.ports.PlayerRenderDTO;
import engine.view.renderables.ports.RenderDTO;
import engine.view.renderables.ports.RenderMetricsDTO;
import engine.view.renderables.ports.SpatialGridStatisticsRenderDTO;

import java.awt.Toolkit;

/**
 * Renderer
 * --------
 *
 * Active rendering loop responsible for drawing the current frame to the
 * screen. This class owns the rendering thread and performs all drawing using
 * a BufferStrategy-based back buffer.
 *
 * Architectural role
 * ------------------
 * The Renderer is a pull-based consumer of visual snapshots provided by the
 * View.
 * It never queries or mutates the model directly.
 *
 * Rendering is decoupled from simulation through immutable snapshot DTOs
 * (EntityInfoDTO / DBodyInfoDTO), ensuring that rendering remains deterministic
 * and free of model-side race conditions.
 *
 * Threading model
 * ---------------
 * - A dedicated render thread drives the render loop (Runnable).
 * - Rendering is active only while the engine state is ALIVE.
 * - The loop terminates cleanly when the engine reaches STOPPED.
 *
 * Data access patterns
 * --------------------
 * Three different renderable collections are used, each with a consciously
 * chosen
 * concurrency strategy based on update frequency and thread ownership:
 *
 * 1) Dynamic bodies (DBodies)
 * - Stored in a plain HashMap.
 * - Updated and rendered exclusively by the render thread.
 * - No concurrent access → no synchronization required.
 *
 * 2) Static bodies (SBodies)
 * - Rarely updated, potentially from non-render threads
 * (model → controller → view).
 * - Stored using a copy-on-write strategy:
 * * Updates create a new Map instance.
 * * The reference is swapped atomically via a volatile field.
 * - The render thread only reads stable snapshots.
 *
 * 3) Decorators
 * - Same access pattern as static bodies.
 * - Uses the same copy-on-write + atomic swap strategy.
 *
 * This design avoids locks, minimizes contention, and guarantees that the
 * render thread always iterates over a fully consistent snapshot.
 *
 * Frame tracking
 * --------------
 * A monotonically increasing frame counter (currentFrame) is used to:
 * - Track renderable liveness.
 * - Remove obsolete renderables deterministically.
 *
 * Each update method captures a local frame snapshot to ensure internal
 * consistency, even if the global frame counter advances later.
 *
 * Rendering pipeline
 * ------------------
 * Per frame:
 * 1) Background is rendered to a VolatileImage for fast blitting.
 * 2) Decorators are drawn.
 * 3) Static bodies are drawn.
 * 4) Dynamic bodies are updated and drawn.
 * 5) HUD elements (FPS) are rendered last.
 *
 * Alpha compositing is used to separate opaque background rendering from
 * transparent entities.
 *
 * Performance considerations
 * --------------------------
 * - Triple buffering via BufferStrategy.
 * - VolatileImage used for background caching.
 * - Target frame rate ~60 FPS (16 ms delay).
 * - FPS is measured using a rolling one-second window.
 *
 * Design goals
 * ------------
 * - Deterministic rendering.
 * - Zero blocking in the render loop.
 * - Clear ownership of mutable state.
 * - Explicit, documented concurrency decisions.
 *
 * This class is intended to behave as a low-level rendering component suitable
 * for a small game engine rather than a UI-centric Swing renderer.
 */
public class Renderer extends Canvas implements Runnable {

    // region Constants
    private static final int REFRESH_DELAY_IN_MILLIS = 1; //
    private static final long MONITORING_PERIOD_NS = 750_000_000L;
    // endregion

    // region Fields
    private DoubleVector viewDimension;
    private View view;
    private int delayInMillis = 5;
    private long currentFrame = 0;
    private Thread thread;

    private BufferedImage background;
    private Images images;
    private ImageCache imagesCache;
    private VolatileImage viBackground;
    private final PlayerHUD playerHUD = new PlayerHUD();
    private final SystemHUD systemHUD = new SystemHUD();
    private final SpatialGridHUD spatialGridHUD = new SpatialGridHUD();
    private final InstrumentationHUD instrumentationHUD = new InstrumentationHUD();
    private final IntroHUD introHUD = new IntroHUD();
    private final GameOverHUD gameOverHUD = new GameOverHUD();
    private final VictoryHUD victoryHUD = new VictoryHUD();
    private final WaveHUD waveHUD = new WaveHUD();
    private final ScoreHUD scoreHUD = new ScoreHUD();
    private final PauseHUD pauseHUD = new PauseHUD();
    private final engine.view.hud.impl.BossHUD bossHUD = new engine.view.hud.impl.BossHUD();
    volatile int currentWave = 1;
    private final RendererProfiler rendererProfiler = new RendererProfiler(MONITORING_PERIOD_NS);

    private double cameraX = 0.0d;
    private double cameraY = 0.0d;
    private double maxCameraClampY;
    private double maxCameraClampX;
    private double backgroundScrollSpeedX = 0.4;
    private double backgroundScrollSpeedY = 0.4;

    private final ArrayList<String> visibleEntityIds = new ArrayList<>(1600);
    private final int[] scratchIdxBuffer = new int[1600];

    // Posiciones de enemigos para dibujar flechas (actualizado por AIBasicSpawner)
    private volatile double[] enemyArrowXs  = new double[0];
    private volatile double[] enemyArrowYs  = new double[0];
    private volatile int      enemyArrowCount = 0;

    private final Map<String, DynamicRenderable> dynamicRenderables = new ConcurrentHashMap<>(2500);
    private PoolMDTO<DynamicRenderDTO> dynamicRenderDtoPool;
    private DynamicRenderableMapper dynamicRenderMapper;
    private volatile Map<String, Renderable> staticRenderables = new ConcurrentHashMap<>(100);
    /** Mapa separado para explosiones — NO se toca en updateStaticRenderables */
    private final Map<String, ExplosionRenderable> explosionRenderables = new ConcurrentHashMap<>(32);

    /** Timestamp en ms cuando el jugador murió; -1 = vivo */
    private volatile long playerDeathTimeMs = -1L;
    /** Delay antes de mostrar el GameOver (ms) */
    private static final long DEATH_DELAY_MS  = 1100L;
    /** Duración del fade-in del GameOver (ms) — no usada, el HUD gestiona su propio fade */
    private static final long FADE_DURATION_MS = 400L;
    /** Si true, la cámara está congelada (jugador muerto o en game over) */
    private boolean cameraFrozen = false;
    // endregion

    // region Constructors
    public Renderer(View view) {
        this.view = view;

        this.setIgnoreRepaint(true);
        this.setCameraClampLimits();
    }
    // endregion

    // *** PUBLICS ***

    public boolean activate() {
        if (this.viewDimension == null) {
            throw new IllegalArgumentException("View dimensions not setted");
        }

        // Esperar a que el canvas sea displayable (ventana creada)
        while (!this.isDisplayable()) {
            try { Thread.sleep(this.delayInMillis); }
            catch (InterruptedException e) { throw new IllegalArgumentException(e.getMessage()); }
        }

        // Esperar a que el canvas tenga tamaño real (post-maximización de Swing)
        // Esto evita el fallo de BufferStrategy con pantalla blanca en Windows
        int attempts = 0;
        while ((this.getWidth() <= 0 || this.getHeight() <= 0) && attempts < 200) {
            try { Thread.sleep(10); }
            catch (InterruptedException e) { throw new IllegalArgumentException(e.getMessage()); }
            attempts++;
        }

        // Usar el tamaño real del canvas si ya tiene dimensiones, si no usar viewDimension
        int realW = this.getWidth();
        int realH = this.getHeight();
        if (realW > 0 && realH > 0) {
            this.viewDimension = new DoubleVector(realW, realH);
            System.out.println("Renderer: using real canvas size " + realW + "x" + realH);
        } else {
            System.out.println("Renderer: using viewDimension " + (int)this.viewDimension.x + "x" + (int)this.viewDimension.y);
        }

        this.setCameraClampLimits();

        // Initialize DTO pooling
        this.dynamicRenderDtoPool = new PoolMDTO<>(
                () -> new DynamicRenderDTO(null, 0, 0, 0, 0, 0L, 0, 0, 0, 0, 0L));
        this.dynamicRenderMapper = new DynamicRenderableMapper(this.dynamicRenderDtoPool);

        this.thread = new Thread(this);
        this.thread.setName("Renderer");
        this.thread.setPriority(Thread.NORM_PRIORITY + 2);
        this.thread.start();

        System.out.println("Renderer: Activated");
        return true;
    }

    // region adders (add***)
    public void addStaticRenderable(String entityId, String assetId) {
        Renderable renderable = new Renderable(entityId, assetId, this.imagesCache, this.currentFrame);
        this.staticRenderables.put(entityId, renderable);
    }

    public void addExplosionRenderable(String entityId, double posX, double posY, double size) {
        RenderDTO dto = new RenderDTO(entityId, posX, posY, 0, size, System.nanoTime());
        ExplosionRenderable renderable = new ExplosionRenderable(dto, this.images, this.currentFrame);
        this.explosionRenderables.put(entityId, renderable);
    }

    public void addDynamicRenderable(String entityId, String assetId) {
        DynamicRenderable renderable = new DynamicRenderable(entityId, assetId, this.imagesCache, this.currentFrame);
        this.dynamicRenderables.put(entityId, renderable);
    }
    // endregion

    // region getters (get***)
    public Renderable getLocalPlayerRenderable() {
        String localPlayerId = this.view.getLocalPlayerId();

        if (localPlayerId == null || localPlayerId.isEmpty()) {
            return null; // ======= No player to follow =======>>
        }
        Renderable renderableLocalPlayer = this.dynamicRenderables.get(this.view.getLocalPlayerId());
        return renderableLocalPlayer;
    }

    /**
     * Get render metrics for HUD display
     */
    public RenderMetricsDTO getRenderMetrics() {
        return new RenderMetricsDTO(
                this.rendererProfiler.getAvgDrawBackgroundMs(),
                this.rendererProfiler.getAvgDrawStaticMs(),
                this.rendererProfiler.getAvgDrawDynamicMs(),
                this.rendererProfiler.getAvgQueryDynamicMs(),
                this.rendererProfiler.getAvgPaintDynamicMs(),
                this.rendererProfiler.getAvgDrawHudsMs(),
                this.rendererProfiler.getAvgDrawMs(),
                this.rendererProfiler.getAvgUpdateMs(),
                this.rendererProfiler.getAvgFrameMs());
    }
    // endregion

    // region notifiers (notify***)
    public void notifyDynamicIsDead(String entityId) {
        // Si está en la lista de "eliminar con delay" (boss re-spawn), no borrar aún
        if (pendingRemovalIds.contains(entityId)) return;
        this.dynamicRenderables.remove(entityId);
    }

    /** IDs de renderables que NO deben eliminarse inmediatamente (boss re-spawn en curso) */
    private final Set<String> pendingRemovalIds = ConcurrentHashMap.newKeySet();

    /** Proteger el renderable de un boss para que no desaparezca durante el re-spawn */
    public void protectBossRenderable(String oldId, String newId) {
        // El viejo ID queda en el mapa hasta que el nuevo aparezca o pasen 10 frames
        pendingRemovalIds.add(oldId);
        // Programa la limpieza del viejo ID tras un breve delay en un hilo separado
        new Thread(() -> {
            try { Thread.sleep(80); } catch (InterruptedException ignored) {}
            pendingRemovalIds.remove(oldId);
            dynamicRenderables.remove(oldId);
        }, "BossRenderCleanup").start();
    }
    // endregion

    // region setters (set***)
    public void setImages(BufferedImage background, Images images) {
        this.background = background;
        this.viBackground = null;

        this.images = images;
        this.imagesCache = new ImageCache(this.getGraphicsConfSafe(), this.images);
    }

    public void setViewDimension(DoubleVector viewDim) {
        this.viewDimension = viewDim;
        this.setCameraClampLimits();
        this.setPreferredSize(new Dimension((int) this.viewDimension.x, (int) this.viewDimension.y));
    }

    /** Expone el IntroHUD para que View pueda enrutar teclas a él. */
    public IntroHUD getIntroHUD() { return this.introHUD; }

    /** Expone el GameOverHUD para que View pueda enrutar teclas a él. */
    public GameOverHUD getGameOverHUD() { return this.gameOverHUD; }

    /** Expone el VictoryHUD para que View pueda enrutar teclas a él. */
    public VictoryHUD getVictoryHUD() { return this.victoryHUD; }

    /** Muestra la pantalla de victoria con fade-in. */
    public void showVictory(int score) {
        this.cameraFrozen = true;
        this.victoryHUD.show(score);
    }

    /** Notifica al renderer que el jugador ha muerto: inicia el delay antes del GameOver. */
    public void notifyPlayerIsDead(int waveReached) {
        this.playerDeathTimeMs = System.currentTimeMillis();
        this._deathWave  = waveReached;
        this._deathScore = this.scoreHUD.getScore();
        // Congelar la cámara exactamente donde está ahora — no moverla más
        this.cameraFrozen = true;
    }
    private volatile int _deathWave  = 1;
    private volatile int _deathScore = 0;

    /** Actualiza la oleada actual (para mostrarla en el GameOverHUD). */
    public void setCurrentWave(int wave) {
        this.currentWave = wave;
        this.waveHUD.setWave(wave);
    }

    /** Actualiza los enemigos restantes en el WaveHUD. */
    public void setEnemiesInfo(int alive, int total) {
        this.waveHUD.setEnemiesLeft(alive);
        this.waveHUD.setEnemiesTotal(total);
    }

    /** Muestra un anuncio de nueva oleada centrado en pantalla. */
    public void announceWave(String text) {
        this.waveHUD.announce(text, 2500L);
    }

    public void addScore(int pts) {
        this.scoreHUD.addScore(pts);
        this.waveHUD.setScore(this.scoreHUD.getScore());
        this.waveHUD.setHighScore(this.scoreHUD.getHighScore());
        this.waveHUD.addScorePopup(pts);
    }
    public int  getScore()            { return this.scoreHUD.getScore(); }
    public int  getHighScore()        { return this.scoreHUD.getHighScore(); }
    public void resetScore()          { this.scoreHUD.reset(); this.waveHUD.setScore(0); }
    public PauseHUD getPauseHUD()     { return this.pauseHUD; }
    public void togglePause()         { this.pauseHUD.toggle(); }

    /** Actualiza la barra de vida del boss/miniboss. */
    public void setBossHealth(int current, int max, boolean isBoss) {
        this.bossHUD.set(current, max, isBoss);
    }

    /** Oculta la barra de vida del boss. */
    public void clearBossHealth() {
        this.bossHUD.clear();
    }

    /** Recibe las posiciones world de los enemigos vivos para pintar flechas indicadoras. */
    public void setEnemyPositions(double[] xs, double[] ys, int count) {
        this.enemyArrowXs    = xs;
        this.enemyArrowYs    = ys;
        this.enemyArrowCount = count;
    }

    // endregion

    public void updateStaticRenderables(ArrayList<RenderDTO> renderablesData) {
        if (renderablesData == null) return;

        Map<String, Renderable> newRenderables = new java.util.concurrent.ConcurrentHashMap<>(this.staticRenderables);

        if (renderablesData.isEmpty()) {
            newRenderables.clear();
            this.staticRenderables = newRenderables;
            return;
        }

        long cFrame = this.currentFrame;
        for (RenderDTO renderableData : renderablesData) {
            String entityId = renderableData.entityId;
            if (entityId == null || entityId.isEmpty()) continue;
            Renderable renderable = newRenderables.get(entityId);
            if (renderable == null) {
                throw new IllegalStateException("Renderer: Static renderable not found: " + entityId);
            }
            renderable.update(renderableData, cFrame);
        }

        newRenderables.entrySet().removeIf(e -> e.getValue().getLastFrameSeen() != cFrame);
        this.staticRenderables = newRenderables;
    }

    // *** PRIVATES ***

    // region drawers (draw***)
    private void drawDynamicRenderable(Graphics2D g) {
        // Measure spatial query
        // Margen extra para evitar parpadeo de entidades en los bordes de la vista
        final double QUERY_MARGIN = 200.0;
        long queryStart = this.rendererProfiler.startInterval();
        ArrayList<String> visibleIds = this.view.queryEntitiesInRegion(
                this.cameraX - QUERY_MARGIN, this.cameraX + this.viewDimension.x + QUERY_MARGIN,
                this.cameraY - QUERY_MARGIN, this.cameraY + this.viewDimension.y + QUERY_MARGIN,
                this.scratchIdxBuffer,
                this.visibleEntityIds);
        this.rendererProfiler.stopInterval(RendererProfiler.METRIC_QUERY_DYNAMIC, queryStart);

        // Measure paint loop
        long paintStart = this.rendererProfiler.startInterval();
        for (String entityId : visibleIds) {
            DynamicRenderable renderable = this.dynamicRenderables.get(entityId);
            if (renderable != null) {
                renderable.paint(g, this.currentFrame);
            }
        }
        this.rendererProfiler.stopInterval(RendererProfiler.METRIC_PAINT_DYNAMIC, paintStart);
    }

    private void drawHUDs(Graphics2D g) {
        int w = (int) this.viewDimension.x;
        int h = (int) this.viewDimension.y;

        // Pantalla de inicio
        if (this.introHUD.isActive()) {
            this.introHUD.draw(g, w, h);
            return;
        }

        // --- Secuencia de muerte del jugador ---
        if (playerDeathTimeMs > 0 && !this.gameOverHUD.isActive()) {
            long elapsed = System.currentTimeMillis() - playerDeathTimeMs;

            if (elapsed < DEATH_DELAY_MS) {
                // Pausa dramática: overlay negro que se va oscureciendo
                float alpha = Math.min(0.5f, (float) elapsed / DEATH_DELAY_MS * 0.5f);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g.setColor(java.awt.Color.BLACK);
                g.fillRect(0, 0, w, h);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                this.waveHUD.draw(g, w, h);
                return;
            }

            // Delay completado → activar GameOver (el propio HUD gestiona su fade-in interno)
            this.gameOverHUD.show(_deathWave, _deathScore);
        }

        // Pantalla de Game Over activa
        if (this.gameOverHUD.isActive()) {
            this.gameOverHUD.draw(g, w, h);
            return;
        }

        // Pantalla de Victoria activa
        if (this.victoryHUD.isActive()) {
            this.victoryHUD.draw(g, w, h);
            return;
        }

        // Pausa
        if (this.pauseHUD.isActive()) {
            this.waveHUD.draw(g, w, h);
            this.pauseHUD.draw(g, w, h);
            return;
        }

        // HUDs de juego normales
        long fps = this.rendererProfiler.getLastFps();
        double avgDrawMs = this.rendererProfiler.getAvgDrawMs();

        this.systemHUD.draw(g,
                fps,
                String.format("%.0f", avgDrawMs) + " ms",
                this.imagesCache == null ? 0 : this.imagesCache.size(),
                String.format("%.0f", this.imagesCache == null ? 0 : this.imagesCache.getHitsPercentage()) + "%",
                this.view.getEntityAliveQuantity(),
                this.view.getEntityDeadQuantity(),
                this.currentFrame);

        PlayerRenderDTO playerData = this.view.getLocalPlayerRenderData();
        if (playerData != null) {
            this.playerHUD.draw(g, playerData.toPlayerDTO(), w);
        }

        // Panel superior: oleada + enemigos + puntuación
        this.waveHUD.draw(g, w, h);
        // Barra de vida boss/miniboss
        this.bossHUD.draw(g, w, h);

        // Flechas de enemigos fuera de pantalla
        this.drawEnemyArrows(g);

        SpatialGridStatisticsRenderDTO spatialGridStats = this.view.getSpatialGridStatistics();
        if (spatialGridStats != null) {
            this.spatialGridHUD.draw(g, spatialGridStats.toObjectArray());
        }
    }

    /**
     * Dibuja flechas en los bordes de la pantalla apuntando a enemigos fuera de la vista.
     * Las flechas son rojas translúcidas con un triángulo relleno.
     */
    private void drawEnemyArrows(Graphics2D g) {
        double[] xs    = this.enemyArrowXs;
        double[] ys    = this.enemyArrowYs;
        int      count = this.enemyArrowCount;
        if (count == 0 || xs == null || ys == null) return;

        int viewW = (int) this.viewDimension.x;
        int viewH = (int) this.viewDimension.y;

        // Centro de la pantalla (coordenadas pantalla)
        double screenCX = viewW * 0.5;
        double screenCY = viewH * 0.5;

        // Margen desde el borde donde se pinta la flecha
        int margin = 48;

        var origComposite = g.getComposite();
        var origTransform = g.getTransform();

        for (int i = 0; i < count && i < xs.length && i < ys.length; i++) {
            // Convertir posición world a posición pantalla
            double screenX = xs[i] - this.cameraX;
            double screenY = ys[i] - this.cameraY;

            // Si está dentro de la pantalla, no dibujar flecha
            if (screenX >= margin && screenX <= viewW - margin
                    && screenY >= margin && screenY <= viewH - margin) {
                continue;
            }

            // Ángulo hacia el enemigo desde el centro de la pantalla
            double dx    = screenX - screenCX;
            double dy    = screenY - screenCY;
            double angle = Math.atan2(dy, dx);

            // Punto en el borde de la pantalla (clamped)
            double edgeX = screenCX + Math.cos(angle) * (screenCX - margin);
            double edgeY = screenCY + Math.sin(angle) * (screenCY - margin);
            edgeX = Math.max(margin, Math.min(viewW - margin, edgeX));
            edgeY = Math.max(margin, Math.min(viewH - margin, edgeY));

            // Distancia en tiles para escala del alpha (más lejos = más transparente)
            double dist = Math.sqrt(dx * dx + dy * dy);
            float alpha = Math.max(0.4f, Math.min(1.0f, (float)(300.0 / Math.max(300.0, dist))));

            // Dibujar flecha (triángulo relleno + borde)
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.setTransform(origTransform);
            g.translate(edgeX, edgeY);
            g.rotate(angle);

            // Triángulo: vértice adelante, base atrás
            int[] arrowX = { 18,  -10, -10 };
            int[] arrowY = {  0,  -9,   9 };
            g.setColor(new Color(255, 50, 50));
            g.fillPolygon(arrowX, arrowY, 3);
            g.setColor(new Color(255, 180, 180));
            g.drawPolygon(arrowX, arrowY, 3);
        }

        g.setComposite(origComposite);
        g.setTransform(origTransform);
    }

    private void drawStaticRenderables(Graphics2D g) {
        // Decoradores normales
        Map<String, Renderable> renderables = this.staticRenderables;
        for (Renderable renderable : renderables.values()) {
            if (this.isVisible(renderable)) {
                renderable.paint(g, this.currentFrame);
            }
        }
        // Explosiones (mapa independiente, nunca afectado por swaps de staticRenderables)
        this.explosionRenderables.entrySet().removeIf(e -> e.getValue().isFinished());
        for (ExplosionRenderable exp : this.explosionRenderables.values()) {
            exp.paint(g, this.currentFrame);
        }
    }

    private boolean isVisible(Renderable renderable) {
        RenderDTO renderData = renderable.getRenderData();
        if (renderData == null) {
            return false;
        }

        double viewW = this.viewDimension.x;
        double viewH = this.viewDimension.y;

        double camLeft = this.cameraX;
        double camTop = this.cameraY;
        double camRight = camLeft + viewW;
        double camBottom = camTop + viewH;

        double half = renderData.size * 0.5d;
        if (renderable.getImage() != null) {
            double halfW = renderable.getImage().getWidth(null) * 0.5d;
            double halfH = renderable.getImage().getHeight(null) * 0.5d;
            half = Math.max(halfW, halfH);
        }

        double minX = renderData.posX - half;
        double maxX = renderData.posX + half;
        double minY = renderData.posY - half;
        double maxY = renderData.posY + half;

        if (maxX < camLeft || minX > camRight) {
            return false; // ==== Out of horizontal bounds ======>>
        }

        if (maxY < camTop || minY > camBottom) {
            return false; // ==== Out of vertical bounds ======>>
        }

        return true;
    }

    private void drawScene(BufferStrategy bs) {
        Graphics2D gg;

        do {
            gg = (Graphics2D) bs.getDrawGraphics();
            try {
                // 1) BACKGROUND
                long bgStart = this.rendererProfiler.startInterval();
                gg.setComposite(AlphaComposite.Src); // Opaque
                this.drawTiledBackground(gg);
                gg.drawImage(this.viBackground, 0, 0, null);
                this.rendererProfiler.stopInterval(RendererProfiler.METRIC_DRAW_BACKGROUND, bgStart);

                // 2) WORLD (translated due camera)
                gg.setComposite(AlphaComposite.SrcOver); // With transparency
                AffineTransform defaultTransform = gg.getTransform();
                gg.translate(-this.cameraX, -this.cameraY);

                // Draw static renderables
                long staticStart = this.rendererProfiler.startInterval();
                this.drawStaticRenderables(gg);
                this.rendererProfiler.stopInterval(RendererProfiler.METRIC_DRAW_STATIC, staticStart);

                // Draw dynamic renderables
                long dynamicStart = this.rendererProfiler.startInterval();
                this.drawDynamicRenderable(gg);
                this.rendererProfiler.stopInterval(RendererProfiler.METRIC_DRAW_DYNAMIC, dynamicStart);

                gg.setTransform(defaultTransform);

                // 3) HUD (on top of everything)
                long hudsStart = this.rendererProfiler.startInterval();
                gg.setComposite(AlphaComposite.SrcOver); // With transparency
                this.drawHUDs(gg);
                this.rendererProfiler.stopInterval(RendererProfiler.METRIC_DRAW_HUDS, hudsStart);

            } finally {
                gg.dispose();
            }

            bs.show();
            Toolkit.getDefaultToolkit().sync();
        } while (bs.contentsLost());
    }

    private void drawTiledBackground(Graphics2D g) {
        if (this.background == null || this.viewDimension == null)
            return;

        final int viewW = (int) this.viewDimension.x;
        final int viewH = (int) this.viewDimension.y;
        if (viewW <= 0 || viewH <= 0)
            return;

        final int tileW = this.background.getWidth(null);
        final int tileH = this.background.getHeight(null);
        if (tileW <= 0 || tileH <= 0)
            return;

        final double scrollX = this.cameraX * this.backgroundScrollSpeedX;
        final double scrollY = this.cameraY * this.backgroundScrollSpeedY;

        // Tile offset in [-(tile-1)..0], stable with negatives
        final int offX = -Math.floorMod((int) Math.floor(scrollX), tileW);
        final int offY = -Math.floorMod((int) Math.floor(scrollY), tileH);

        // Start 1 tile before to ensure full coverage
        final int startX = offX - tileW;
        final int startY = offY - tileH;
        for (int x = startX; x < viewW + tileW; x += tileW) {
            for (int y = startY; y < viewH + tileH; y += tileH) {
                g.drawImage(this.background, x, y, null);
            }
        }
    }
    // endregion

    // region getters (get***)
    private GraphicsConfiguration getGraphicsConfSafe() {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc == null) {
            gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration();
        }

        return gc;
    }

    private VolatileImage getVIBackground() {
        this.viBackground = this.getVolatileImage(
                this.viBackground,
                this.background,
                new Dimension((int) this.viewDimension.x, (int) this.viewDimension.y));

        return this.viBackground;

    }

    private VolatileImage getVolatileImage(
            VolatileImage vi, BufferedImage src, Dimension dim) {

        GraphicsConfiguration gc = this.getGraphicsConfSafe();

        if (vi == null || vi.getWidth() != dim.width || vi.getHeight() != dim.height
                || vi.validate(gc) == VolatileImage.IMAGE_INCOMPATIBLE) {
            // New volatile image
            vi = gc.createCompatibleVolatileImage(dim.width, dim.height, Transparency.OPAQUE);
        }

        int val;
        do {
            val = vi.validate(gc);
            if (val != VolatileImage.IMAGE_OK || vi.contentsLost()) {
                Graphics2D g = vi.createGraphics();
                g.drawImage(src, 0, 0, dim.width, dim.height, null);
                g.dispose();
            }
        } while (vi.contentsLost());

        return vi;
    }
    // endregion

    // region setters (set***)
    private void setCameraClampLimits() {
        DoubleVector woldDim = this.view.getWorldDimension();

        if (woldDim == null || this.viewDimension == null) {
            this.maxCameraClampX = 0.0;
            this.maxCameraClampY = 0.0;
            return; // ======= No world or view dimensions info ======= >>
        }

        this.maxCameraClampX = Math.max(0.0, woldDim.x - this.viewDimension.x);
        this.maxCameraClampY = Math.max(0.0, woldDim.y - this.viewDimension.y);
    }
    // endregion

    // region updaters (update***)
    private void updateCamera() {
        // No mover la cámara si está congelada (muerte del jugador / game over)
        if (this.cameraFrozen) return;

        Renderable localPlayerRenderable = this.getLocalPlayerRenderable();
        DoubleVector worldDim = this.view.getWorldDimension();

        if (localPlayerRenderable == null || this.viewDimension == null || worldDim == null) {
            return; // ======== No player or data to follow =======>>
        }

        RenderDTO playerData = localPlayerRenderable.getRenderData();

        if (playerData == null) {
            return; // ======== Player renderable not yet updated =======>>
        }

        double playerX = playerData.posX - this.cameraX;
        double playerY = playerData.posY - this.cameraY;

        double desiredX;
        double desiredY;

        double minX = this.viewDimension.x * 0.3;
        double maxX = this.viewDimension.x * 0.7;
        double minY = this.viewDimension.y * 0.3;
        double maxY = this.viewDimension.y * 0.7;

        if (playerX < minX) {
            desiredX = playerData.posX - minX;
        } else if (playerX > maxX) {
            desiredX = playerData.posX - maxX;
        } else {
            desiredX = playerData.posX - (playerX);
        }

        if (playerY < minY) {
            desiredY = playerData.posY - minY;
        } else if (playerY > maxY) {
            desiredY = playerData.posY - maxY;
        } else {
            desiredY = playerData.posY - (playerY);
        }

        // Lerp suave: la cámara sigue al jugador gradualmente para evitar saltos bruscos
        // Factor 0.18 → suave pero sin retraso perceptible a 60fps
        final double CAMERA_LERP = 0.18;
        this.cameraX += (desiredX - this.cameraX) * CAMERA_LERP;
        this.cameraY += (desiredY - this.cameraY) * CAMERA_LERP;

        // Clamp when camera goes out of world limits
        this.cameraX = clamp(cameraX, 0.0, this.maxCameraClampX);
        this.cameraY = clamp(cameraY, 0.0, this.maxCameraClampY);
    }

    private void updateDynamicRenderables(ArrayList<DynamicRenderDTO> renderablesData) {
        if (renderablesData == null || renderablesData.isEmpty()) {
            // If no objects are alive this frame, clear the snapshot entirely
            for (DynamicRenderable renderable : this.dynamicRenderables.values()) {
                DynamicRenderDTO dto = (DynamicRenderDTO) renderable.getRenderData();
                if (dto != null) {
                    this.dynamicRenderDtoPool.release(dto);
                }
            }
            this.dynamicRenderables.clear();
            return; // ========= Nothing to render by the moment ... =========>>
        }

        // Update or create a renderable associated with each DBodyRenderInfoDTO
        long cFrame = this.currentFrame;
        for (DynamicRenderDTO renderableData : renderablesData) {
            String entityId = renderableData.entityId;
            if (entityId == null || entityId.isEmpty()) {
                this.dynamicRenderDtoPool.release(renderableData);
                continue;
            }

            DynamicRenderable renderable = this.dynamicRenderables.get(entityId);
            if (renderable != null) {
                // Existing renderable → update its snapshot and sprite if needed
                DynamicRenderDTO current = (DynamicRenderDTO) renderable.getRenderData();
                if (current == null) {
                    DynamicRenderDTO pooled = this.dynamicRenderDtoPool.acquire();
                    pooled.updateFrom(renderableData);
                    this.dynamicRenderDtoPool.release(renderableData);
                    renderable.update(pooled, cFrame);
                } else {
                    renderable.update(renderableData, cFrame);
                    this.dynamicRenderDtoPool.release(renderableData);
                }
            } else {
                this.dynamicRenderDtoPool.release(renderableData);
            }
        }

        // Remove renderables not updated this frame (i.e., objects no longer alive)
        this.dynamicRenderables.entrySet().removeIf(entry -> {
            DynamicRenderable renderable = entry.getValue();
            if (renderable.getLastFrameSeen() == cFrame) {
                return false;
            }

            DynamicRenderDTO dto = (DynamicRenderDTO) renderable.getRenderData();
            if (dto != null) {
                this.dynamicRenderDtoPool.release(dto);
            }

            return true;
        });
    }
    // endregion

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private void renderFrame(Graphics2D g) {
        CanvasMetrics metrics = this.getCanvasMetrics();
        ViewportTransform transform = this.calculateViewportTransform(metrics);

        this.clearCanvas(g, metrics);

        Graphics2D worldG = (Graphics2D) g.create();
        try {
            this.applyWorldTransform(worldG, transform);
            this.drawWorld(worldG);
        } finally {
            worldG.dispose();
        }
    }

    private void applyWorldTransform(Graphics2D g, ViewportTransform transform) {
        g.translate(transform.offsetX, transform.offsetY);
        g.scale(transform.scale, transform.scale);
    }

    private void clearCanvas(Graphics2D g, CanvasMetrics metrics) {
        g.setComposite(AlphaComposite.Src); // Opaque
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, metrics.width, metrics.height);
    }

    private ViewportTransform calculateViewportTransform(CanvasMetrics metrics) {
        double scaleX = metrics.width / this.viewDimension.x;
        double scaleY = metrics.height / this.viewDimension.y;
        double scale = Math.min(scaleX, scaleY);

        double scaledWidth = this.viewDimension.x * scale;
        double scaledHeight = this.viewDimension.y * scale;
        double offsetX = (metrics.width - scaledWidth) * 0.5d;
        double offsetY = (metrics.height - scaledHeight) * 0.5d;

        return new ViewportTransform(scale, offsetX, offsetY);
    }

    private CanvasMetrics getCanvasMetrics() {
        int canvasWidth = this.getWidth();
        int canvasHeight = this.getHeight();
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            canvasWidth = (int) this.viewDimension.x;
            canvasHeight = (int) this.viewDimension.y;
        }

        return new CanvasMetrics(canvasWidth, canvasHeight);
    }

    private void drawWorld(Graphics2D g) {
        g.setComposite(AlphaComposite.Src); // Opaque
        g.drawImage(this.getVIBackground(), 0, 0, null);

        g.setComposite(AlphaComposite.SrcOver); // With transparency
        this.drawStaticRenderables(g);
        this.drawDynamicRenderable(g);
        this.drawHUDs(g);
    }

    private static final class CanvasMetrics {
        private final int width;
        private final int height;

        private CanvasMetrics(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private static final class ViewportTransform {
        private final double scale;
        private final double offsetX;
        private final double offsetY;

        private ViewportTransform(double scale, double offsetX, double offsetY) {
            this.scale = scale;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    // *** INTERFACE IMPLEMENTATIONS ***

    // region Runnable
    @Override
    public void run() {
        this.createBufferStrategy(3);
        BufferStrategy bs = getBufferStrategy();

        if (bs == null) {
            throw new IllegalStateException(
                    "Renderer: BufferStrategy creation failed (canvas too large): "
                            + (int) this.viewDimension.x + "x" + (int) this.viewDimension.y);
        }

        while (true) {
            EngineState engineState = this.view.getEngineState();
            if (engineState == EngineState.STOPPED) {
                break;
            }

            // Pantalla de inicio: solo dibujar, no actualizar simulación
            if (this.introHUD.isActive()) {
                this.drawScene(bs);
                try { Thread.sleep(16); } catch (InterruptedException ex) { throw new RuntimeException(ex); }
                continue;
            }

            // Pantalla de Game Over: solo dibujar
            if (this.gameOverHUD.isActive()) {
                this.drawScene(bs);
                try { Thread.sleep(16); } catch (InterruptedException ex) { throw new RuntimeException(ex); }
                continue;
            }

            // Pantalla de Victoria: solo dibujar
            if (this.victoryHUD.isActive()) {
                this.drawScene(bs);
                try { Thread.sleep(16); } catch (InterruptedException ex) { throw new RuntimeException(ex); }
                continue;
            }

            // Pausa: solo dibujar
            if (this.pauseHUD.isActive()) {
                this.drawScene(bs);
                try { Thread.sleep(16); } catch (InterruptedException ex) { throw new RuntimeException(ex); }
                continue;
            }

            long frameIntervalStart = 0L;
            if (engineState == EngineState.ALIVE) {
                this.currentFrame++;
                frameIntervalStart = this.rendererProfiler.startInterval();
                this.rendererProfiler.addFrame();

                long updateStart = this.rendererProfiler.startInterval();
                ArrayList<DynamicRenderDTO> renderData = this.view.snapshotRenderData(this.dynamicRenderMapper);
                this.updateDynamicRenderables(renderData);
                this.updateCamera();
                this.rendererProfiler.stopInterval(RendererProfiler.METRIC_UPDATE, updateStart);

                long drawStart = this.rendererProfiler.startInterval();
                this.drawScene(bs);
                this.rendererProfiler.stopInterval(RendererProfiler.METRIC_DRAW, drawStart);

                this.view.syncInputState();
            }

            try {
                Thread.sleep(REFRESH_DELAY_IN_MILLIS);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }

            if (engineState == EngineState.ALIVE) {
                this.rendererProfiler.stopInterval(RendererProfiler.METRIC_FRAME, frameIntervalStart);
            }
        }
    }
    // endregion
}
