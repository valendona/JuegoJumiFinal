package gameai;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

import engine.controller.ports.WorldManager;
import engine.generators.AbstractIAGenerator;
import engine.world.ports.WorldDefinition;

/**
 * AIBasicSpawner — Naves enemigas que persiguen al jugador por oleadas.
 *
 * Cada oleada spawna N naves desde los bordes. En cada tick la IA
 * aplica thrust a cada nave viva en dirección al jugador (seguimiento).
 * Cuando las naves van demasiado rápido, aplican freno para mantener
 * una velocidad controlada, haciendo el gameplay más desafiante.
 * La oleada avanza solo cuando todas las naves enemigas son destruidas.
 */
public class AIBasicSpawner extends AbstractIAGenerator {

    // region Constantes
    private static final double WORLD_SIZE          = 4500d;

    // Enemigos normales
    private static final double BASE_SPEED          = 200d;
    private static final double SPEED_INCREMENT     = 35d;
    private static final int    WAVE_SIZE_START     = 2;
    private static final int    WAVE_SIZE_INCREMENT = 2; // usado solo en normal (dificultad 2)
    private static final long   BETWEEN_SPAWN_MS    = 900L;
    private static final long   BETWEEN_WAVES_MS    = 2000L;
    private static final double ENEMY_THRUST        = 950d;
    private static final double ENEMY_SIZE          = 60d;
    private static final long   STEER_INTERVAL_MS   = 16L;
    private static final int    SCORE_PER_KILL       = 100;
    private static final double MAX_SPEED_MULT      = 1.35;
    private static final double BRAKE_FORCE         = 500d;
    private static final String ENEMY_ASSET         = "spaceship_03";

    // Miniboss (oleada 5)
    private static final int    MINIBOSS_WAVE       = 5;
    private static final double MINIBOSS_SIZE       = 120d;
    private static final double MINIBOSS_SPEED      = 160d;
    private static final double MINIBOSS_THRUST     = 1200d;
    private static final double MINIBOSS_BRAKE      = 700d;
    private static final String MINIBOSS_ASSET      = "spaceship_07";
    private static final int    SCORE_MINIBOSS      = 1000;
    private static final int    MINIBOSS_HP         = 15;

    // Boss (oleada 10)
    private static final int    BOSS_WAVE           = 10;
    private static final double BOSS_SIZE           = 200d;
    private static final double BOSS_SPEED          = 120d;
    private static final double BOSS_THRUST         = 1600d;
    private static final double BOSS_BRAKE          = 900d;
    private static final String BOSS_ASSET          = "spaceship_06";
    private static final int    SCORE_BOSS          = 5000;
    private static final int    BOSS_HP             = 30;
    // endregion

    private enum WaveState { SPAWNING, WAITING_CLEAR, PAUSING }

    // region Fields
    private final Random rnd = new Random();

    private int       difficulty      = 2;
    private boolean   difficultySet   = false;
    private int       currentWave     = 1;
    private int       enemiesThisWave = WAVE_SIZE_START;
    private int       spawnedInWave   = 0;
    private WaveState waveState       = WaveState.SPAWNING;
    private long      lastActionTime  = 0L;
    private long      lastSteerTime   = 0L;

    // Buffers reutilizables para enviar posiciones al renderer
    private double[] posXBuf = new double[64];
    private double[] posYBuf = new double[64];

    private final Set<String> activeEnemyIds = new LinkedHashSet<>();
    /** Última posición conocida de cada enemigo vivo, actualizada cada tick */
    private final java.util.Map<String, double[]> lastKnownPos = new java.util.HashMap<>();
    /** ID del boss/miniboss actual si hay uno vivo */
    private String bossId = null;

    // HP actual del boss/miniboss (solo válido cuando bossId != null)
    private int bossHpCurrent = 0;
    private int bossHpMax     = 0;
    /** Último conteo de activos del boss para detectar golpes (el boss recibe un golpe cuando
     *  el motor lo "mata" y nosotros lo revivimos con HP reducido). Como el motor no tiene
     *  HP parcial para enemigos usamos un tick de detección por colisión de proyectil. */
    private final java.util.Map<String, Integer> enemyHitCount = new java.util.HashMap<>();
    // endregion

    /** Referencia a las reglas de juego para registrar inmunidad de boss a obstáculos */
    private gamerules.AsteroidSurvivor gameRules = null;

    public AIBasicSpawner(WorldManager worldEvolver, WorldDefinition worldDefinition, int maxCreationDelay) {
        super(worldEvolver, worldDefinition, maxCreationDelay);
    }

    /** Inyecta las reglas de juego para que el boss sea inmune a obstáculos. */
    public AIBasicSpawner withGameRules(gamerules.AsteroidSurvivor rules) {
        this.gameRules = rules;
        return this;
    }

    @Override
    protected String getThreadName() { return "AIBasicSpawner"; }

    /** Establece la dificultad (1=fácil, 2=normal, 3=difícil). */
    public void setDifficulty(int d) { this.difficulty = Math.max(1, Math.min(3, d)); }

    @Override
    protected void onActivate() {
        this.lastActionTime = System.currentTimeMillis();
    }

    @Override
    protected void onTick() {
        long now = System.currentTimeMillis();

        if (this.worldEvolver.isIntroActive()) return;

        if (!difficultySet) {
            this.difficulty    = this.worldEvolver.getIntroDifficulty();
            this.difficultySet = true;
        }

        sendEnemyPositions();
        pruneDeadEnemies();
        this.worldEvolver.setCurrentWave(currentWave);
        this.worldEvolver.setEnemiesInfo(activeEnemyIds.size(), enemiesThisWave);
        steerEnemies(now);

        switch (waveState) {

            case SPAWNING -> {
                if (now - lastActionTime < BETWEEN_SPAWN_MS) return;

                boolean isBossWave     = (currentWave == BOSS_WAVE);
                boolean isMinibossWave = (currentWave == MINIBOSS_WAVE);

                String id;
                if (isBossWave && spawnedInWave == 0) {
                    id = spawnBoss();
                    bossId = id;
                } else if (isMinibossWave && spawnedInWave == 0) {
                    id = spawnMiniboss();
                    bossId = id;
                } else if (isBossWave || isMinibossWave) {
                    // En oleadas especiales solo 1 enemigo (el jefe)
                    waveState = WaveState.WAITING_CLEAR;
                    return;
                } else {
                    id = spawnEnemy();
                }

                if (id != null) activeEnemyIds.add(id);
                spawnedInWave++;
                lastActionTime = now;
                if (spawnedInWave >= enemiesThisWave) {
                    waveState = WaveState.WAITING_CLEAR;
                }
            }

            case WAITING_CLEAR -> {
                if (activeEnemyIds.isEmpty()) {
                    bossId = null;
                    currentWave++;
                    // Oleadas de jefes: solo 1 enemigo
                    if (currentWave == MINIBOSS_WAVE || currentWave == BOSS_WAVE) {
                        enemiesThisWave = 1;
                    } else {
                        enemiesThisWave = WAVE_SIZE_START + (currentWave - 1) * waveSizeIncrement();
                    }
                    spawnedInWave  = 0;
                    waveState      = WaveState.PAUSING;
                    lastActionTime = now;
                    String label = (currentWave == BOSS_WAVE)     ? "¡¡ BOSS - OLEADA " + currentWave + " !!" :
                                   (currentWave == MINIBOSS_WAVE) ? "¡ MINIBOSS - OLEADA " + currentWave + " !" :
                                                                     "¡OLEADA " + currentWave + "!";
                    this.worldEvolver.announceWave(label);
                }
            }

            case PAUSING -> {
                if (now - lastActionTime >= BETWEEN_WAVES_MS) {
                    waveState      = WaveState.SPAWNING;
                    lastActionTime = now;
                }
            }
        }
    }

    // *** PRIVATE ***

    /** Devuelve cuántos enemigos se añaden por oleada según la dificultad:
     *  fácil=1, normal=2, difícil=3. */
    private int waveSizeIncrement() {
        return difficulty; // 1 → fácil, 2 → normal, 3 → difícil
    }

    private double currentSpeed() {
        double diffMult = 0.7 + (difficulty - 1) * 0.3;
        return (BASE_SPEED + (currentWave - 1) * SPEED_INCREMENT) * diffMult;
    }

    private boolean isBossId(String id) { return id != null && id.equals(bossId); }

    private void steerEnemies(long now) {
        if (activeEnemyIds.isEmpty()) return;
        if (now - lastSteerTime < STEER_INTERVAL_MS) return;
        lastSteerTime = now;

        String playerId = this.worldEvolver.getLocalPlayerId();
        if (playerId == null) return;
        double[] pos = this.worldEvolver.getPlayerPosition(playerId);
        if (pos == null) return;

        double normalMaxSpeed = currentSpeed() * MAX_SPEED_MULT;

        for (String enemyId : activeEnemyIds) {
            double speed = this.worldEvolver.getBodySpeed(enemyId);
            boolean isBoss = isBossId(enemyId);

            double maxSpd   = isBoss ? (currentWave == BOSS_WAVE ? BOSS_SPEED     : MINIBOSS_SPEED)     * 1.3 : normalMaxSpeed;
            double thrust   = isBoss ? (currentWave == BOSS_WAVE ? BOSS_THRUST    : MINIBOSS_THRUST)         : ENEMY_THRUST;
            double brake    = isBoss ? (currentWave == BOSS_WAVE ? BOSS_BRAKE     : MINIBOSS_BRAKE)          : BRAKE_FORCE;

            if (speed > maxSpd) {
                this.worldEvolver.brakeBody(enemyId, brake * 0.6);
            } else {
                this.worldEvolver.steerBodyToward(enemyId, pos[0], pos[1], thrust);
            }
        }
    }

    private void sendEnemyPositions() {
        int n = activeEnemyIds.size();
        if (posXBuf.length < n) { posXBuf = new double[n + 8]; posYBuf = new double[n + 8]; }
        int i = 0;
        for (String enemyId : activeEnemyIds) {
            double[] p = this.worldEvolver.getBodyPosition(enemyId);
            if (p != null) {
                posXBuf[i] = p[0]; posYBuf[i] = p[1]; i++;
                double[] cached = lastKnownPos.get(enemyId);
                if (cached == null) lastKnownPos.put(enemyId, new double[]{p[0], p[1]});
                else { cached[0] = p[0]; cached[1] = p[1]; }
            }
        }
        this.worldEvolver.setEnemyPositions(posXBuf, posYBuf, i);
    }

    private void pruneDeadEnemies() {
        java.util.List<double[]> explosions = new java.util.ArrayList<>();
        java.util.List<String>   toRemove   = new java.util.ArrayList<>();
        int bonusScore = 0;

        for (String id : activeEnemyIds) {
            if (!this.worldEvolver.isBodyDead(id)) continue;

            double[] pos = lastKnownPos.get(id);

            // ¿Es el boss/miniboss?
            if (id.equals(bossId)) {
                boolean isBossWave = (currentWave == BOSS_WAVE);
                // Consumir daño acumulado; clampear a máx 2 por hit para evitar
                // que misiles con thrust generen múltiples colisiones en el mismo impacto
                int rawDmg = this.worldEvolver.pollBossDamage();
                int dmg = Math.min(2, Math.max(1, rawDmg));
                bossHpCurrent = Math.max(0, bossHpCurrent - dmg);

                if (bossHpCurrent > 0) {
                    // Aún tiene HP → explosión de impacto y re-spawn
                    this.worldEvolver.setBossHealth(bossHpCurrent, bossHpMax, isBossWave);
                    if (pos != null) {
                        this.worldEvolver.spawnExplosion(pos[0], pos[1],
                                isBossWave ? 100.0 : 70.0, 0.5);
                    }
                    double spawnX   = pos != null ? pos[0] : WORLD_SIZE / 2.0;
                    double spawnY   = pos != null ? pos[1] : WORLD_SIZE / 2.0;
                    String asset    = isBossWave ? BOSS_ASSET  : MINIBOSS_ASSET;
                    double size     = isBossWave ? BOSS_SIZE   : MINIBOSS_SIZE;
                    double speed    = isBossWave ? BOSS_SPEED  : MINIBOSS_SPEED;
                    double diffMult = 0.7 + (difficulty - 1) * 0.3;
                    double cx = WORLD_SIZE / 2.0, cy = WORLD_SIZE / 2.0;
                    double dx = cx - spawnX, dy = cy - spawnY;
                    double len = Math.sqrt(dx * dx + dy * dy);
                    if (len < 1) { dx = 1; dy = 0; len = 1; }
                    double vx    = dx / len * speed * diffMult;
                    double vy    = dy / len * speed * diffMult;
                    double angle = Math.toDegrees(Math.atan2(dy, dx));
                    String newId = this.worldEvolver.addDynamicBodyAndGetId(
                            asset, size, spawnX, spawnY, vx, vy, 0, 0, angle, 0, 0, 0);
                    if (newId != null) {
                        // Proteger renderable viejo hasta que el nuevo esté listo → sin parpadeo
                        this.worldEvolver.protectBossRenderable(id, newId);
                        activeEnemyIds.add(newId);
                        if (gameRules != null) { gameRules.unregisterBoss(id); gameRules.registerBoss(newId); }
                        bossId = newId;
                        lastKnownPos.put(newId, new double[]{spawnX, spawnY});
                    }
                    toRemove.add(id);
                    lastKnownPos.remove(id);
                    continue;
                }

                // HP llegó a 0 → muere definitivamente
                bonusScore += isBossWave ? SCORE_BOSS : SCORE_MINIBOSS;
                if (gameRules != null) gameRules.unregisterBoss(id);
                bossId        = null;
                bossHpCurrent = 0;
                this.worldEvolver.clearBossHealth();
            }

            // Muerte definitiva (enemigo normal o boss sin HP)
            if (pos != null) explosions.add(new double[]{pos[0], pos[1]});
            toRemove.add(id);
        }

        if (toRemove.isEmpty()) return;

        for (String id : toRemove) { activeEnemyIds.remove(id); lastKnownPos.remove(id); }

        int normalKills = toRemove.size() - (bonusScore > 0 ? 1 : 0);
        int normalPts   = Math.max(0, normalKills) * SCORE_PER_KILL * currentWave * difficulty;
        this.worldEvolver.addScore(normalPts + bonusScore);

        for (double[] pos : explosions) {
            boolean wasBoss = bonusScore > 0 && explosions.size() == 1;
            double expSize  = wasBoss ? (currentWave == BOSS_WAVE ? 300.0 : 200.0) : 140.0;
            this.worldEvolver.spawnExplosion(pos[0], pos[1], expSize, 1.2);
        }
    }

    /** Spawna desde un borde aleatorio apuntando al centro. */
    private String spawnFromBorder(String assetId, double size, double speed, double angularSpeed) {
        double margin = 300d;
        double center = WORLD_SIZE / 2.0;
        int border = rnd.nextInt(4);
        double spawnX, spawnY;
        switch (border) {
            case 0  -> { spawnX = margin + rnd.nextDouble() * (WORLD_SIZE - 2*margin); spawnY = margin; }
            case 1  -> { spawnX = margin + rnd.nextDouble() * (WORLD_SIZE - 2*margin); spawnY = WORLD_SIZE - margin; }
            case 2  -> { spawnX = WORLD_SIZE - margin; spawnY = margin + rnd.nextDouble() * (WORLD_SIZE - 2*margin); }
            default -> { spawnX = margin; spawnY = margin + rnd.nextDouble() * (WORLD_SIZE - 2*margin); }
        }
        double dx = center - spawnX, dy = center - spawnY;
        double len = Math.sqrt(dx*dx + dy*dy);
        double vx = dx / len * speed, vy = dy / len * speed;
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        return this.worldEvolver.addDynamicBodyAndGetId(
                assetId, size, spawnX, spawnY, vx, vy, 0, 0, angle, angularSpeed, 0, 0);
    }

    private String spawnEnemy() {
        return spawnFromBorder(ENEMY_ASSET, ENEMY_SIZE, currentSpeed(), 0);
    }

    private String spawnMiniboss() {
        double diffMult = 0.7 + (difficulty - 1) * 0.3;
        String id = spawnFromBorder(MINIBOSS_ASSET, MINIBOSS_SIZE, MINIBOSS_SPEED * diffMult, 0);
        if (id != null) {
            bossHpMax     = MINIBOSS_HP;
            bossHpCurrent = MINIBOSS_HP;
            this.worldEvolver.setBossHealth(bossHpCurrent, bossHpMax, false);
            if (gameRules != null) gameRules.registerBoss(id);
        }
        return id;
    }

    private String spawnBoss() {
        double diffMult = 0.7 + (difficulty - 1) * 0.3;
        String id = spawnFromBorder(BOSS_ASSET, BOSS_SIZE, BOSS_SPEED * diffMult, 0);
        if (id != null) {
            bossHpMax     = BOSS_HP;
            bossHpCurrent = BOSS_HP;
            this.worldEvolver.setBossHealth(bossHpCurrent, bossHpMax, true);
            if (gameRules != null) gameRules.registerBoss(id);
        }
        return id;
    }
}

