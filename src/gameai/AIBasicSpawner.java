package gameai;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Random;
import java.util.Set;

import engine.controller.ports.WorldManager;
import engine.generators.AbstractIAGenerator;
import engine.world.ports.WorldDefinition;

/**
 * Gestiona las oleadas de enemigos: spawna naves desde los bordes,
 * las dirige hacia el jugador cada tick y avanza la oleada cuando
 * todas las naves son destruidas. En oleadas especiales (5 y 10)
 * aparece un miniboss y un boss con sistema de HP múltiple.
 */
public class AIBasicSpawner extends AbstractIAGenerator {

    // --- Mundo ---
    private static final double WORLD_SIZE = 4500d;

    // --- Enemigos normales ---
    private static final double BASE_SPEED      = 200d;
    private static final double SPEED_INCREMENT = 35d;
    private static final int    WAVE_SIZE_START = 2;
    private static final long   BETWEEN_SPAWN_MS  = 900L;
    private static final long   BETWEEN_WAVES_MS  = 2000L;
    private static final double ENEMY_THRUST    = 950d;
    private static final double ENEMY_SIZE      = 60d;
    private static final long   STEER_INTERVAL_MS = 16L;
    private static final int    SCORE_PER_KILL  = 100;
    private static final double MAX_SPEED_MULT  = 1.35;
    private static final double BRAKE_FORCE     = 500d;
    private static final String ENEMY_ASSET     = "spaceship_03";

    // --- Miniboss (oleada 5) ---
    private static final int    MINIBOSS_WAVE  = 5;
    private static final double MINIBOSS_SIZE  = 120d;
    private static final double MINIBOSS_SPEED = 160d;
    private static final double MINIBOSS_THRUST= 1200d;
    private static final double MINIBOSS_BRAKE = 700d;
    private static final String MINIBOSS_ASSET = "spaceship_07";
    private static final int    SCORE_MINIBOSS = 1000;
    private static final int    MINIBOSS_HP    = 15;

    // --- Boss final (oleada 10) ---
    private static final int    BOSS_WAVE  = 10;
    private static final double BOSS_SIZE  = 200d;
    private static final double BOSS_SPEED = 120d;
    private static final double BOSS_THRUST= 1600d;
    private static final double BOSS_BRAKE = 900d;
    private static final String BOSS_ASSET = "spaceship_06";
    private static final int    SCORE_BOSS = 5000;
    private static final int    BOSS_HP    = 30;

    private enum WaveState { SPAWNING, WAITING_CLEAR, PAUSING }

    private final Random rnd = new Random();

    private int       difficulty      = 2;
    private boolean   difficultySet   = false;
    private int       currentWave     = 1;
    private int       enemiesThisWave = WAVE_SIZE_START;
    private int       spawnedInWave   = 0;
    private WaveState waveState       = WaveState.SPAWNING;
    private long      lastActionTime  = 0L;
    private long      lastSteerTime   = 0L;

    private double[] posXBuf = new double[64];
    private double[] posYBuf = new double[64];

    private final Set<String>         activeEnemyIds = new LinkedHashSet<>();
    private final Map<String, double[]> lastKnownPos = new HashMap<>();

    private String bossId        = null;
    private int    bossHpCurrent = 0;
    private int    bossHpMax     = 0;

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

    @Override
    protected void onActivate() {
        this.lastActionTime = System.currentTimeMillis();
    }

    @Override
    protected void onTick() {
        long now = System.currentTimeMillis();

        if (this.worldEvolver.isIntroActive()) return;

        if (!difficultySet) {
            difficulty    = this.worldEvolver.getIntroDifficulty();
            difficultySet = true;
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
                    waveState = WaveState.WAITING_CLEAR;
                    return;
                } else {
                    id = spawnEnemy();
                }

                if (id != null) activeEnemyIds.add(id);
                spawnedInWave++;
                lastActionTime = now;
                if (spawnedInWave >= enemiesThisWave) waveState = WaveState.WAITING_CLEAR;
            }

            case WAITING_CLEAR -> {
                if (activeEnemyIds.isEmpty()) {
                    bossId = null;
                    currentWave++;
                    enemiesThisWave = (currentWave == MINIBOSS_WAVE || currentWave == BOSS_WAVE)
                            ? 1
                            : WAVE_SIZE_START + (currentWave - 1) * difficulty;
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

    // ---- Lógica interna ----

    /** Velocidad base de los enemigos normales escalada por oleada y dificultad. */
    private double currentSpeed() {
        return (BASE_SPEED + (currentWave - 1) * SPEED_INCREMENT) * (0.7 + (difficulty - 1) * 0.3);
    }

    private boolean isBossId(String id) { return id != null && id.equals(bossId); }

    /** Dirige cada nave hacia el jugador; aplica freno si supera la velocidad máxima. */
    private void steerEnemies(long now) {
        if (activeEnemyIds.isEmpty() || now - lastSteerTime < STEER_INTERVAL_MS) return;
        lastSteerTime = now;

        String playerId = this.worldEvolver.getLocalPlayerId();
        if (playerId == null) return;
        double[] pos = this.worldEvolver.getPlayerPosition(playerId);
        if (pos == null) return;

        double normalMaxSpeed = currentSpeed() * MAX_SPEED_MULT;

        for (String enemyId : activeEnemyIds) {
            boolean isBoss  = isBossId(enemyId);
            double maxSpd   = isBoss ? (currentWave == BOSS_WAVE ? BOSS_SPEED  : MINIBOSS_SPEED)  * 1.3 : normalMaxSpeed;
            double thrust   = isBoss ? (currentWave == BOSS_WAVE ? BOSS_THRUST : MINIBOSS_THRUST)       : ENEMY_THRUST;
            double brake    = isBoss ? (currentWave == BOSS_WAVE ? BOSS_BRAKE  : MINIBOSS_BRAKE)        : BRAKE_FORCE;

            if (this.worldEvolver.getBodySpeed(enemyId) > maxSpd)
                this.worldEvolver.brakeBody(enemyId, brake * 0.6);
            else
                this.worldEvolver.steerBodyToward(enemyId, pos[0], pos[1], thrust);
        }
    }

    /** Actualiza el buffer de posiciones de enemigos y lo envía al renderer para las flechas. */
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

    /**
     * Detecta enemigos muertos y los elimina de la lista activa.
     * Para el boss: aplica daño al HP y lo re-spawna si aún tiene vida,
     * o lo elimina definitivamente al llegar a 0. Suma puntuación en ambos casos.
     */
    private void pruneDeadEnemies() {
        List<double[]> explosions = new ArrayList<>();
        List<String>   toRemove   = new ArrayList<>();
        int bonusScore = 0;

        for (String id : activeEnemyIds) {
            if (!this.worldEvolver.isBodyDead(id)) continue;

            double[] pos = lastKnownPos.get(id);

            if (id.equals(bossId)) {
                boolean isBossWave = (currentWave == BOSS_WAVE);
                // Clampear a máx 2 para evitar que misiles propulsados generen múltiples hits
                int dmg = Math.min(2, Math.max(1, this.worldEvolver.pollBossDamage()));
                bossHpCurrent = Math.max(0, bossHpCurrent - dmg);

                if (bossHpCurrent > 0) {
                    this.worldEvolver.setBossHealth(bossHpCurrent, bossHpMax, isBossWave);
                    if (pos != null)
                        this.worldEvolver.spawnExplosion(pos[0], pos[1], isBossWave ? 100.0 : 70.0, 0.5);

                    double spawnX = pos != null ? pos[0] : WORLD_SIZE / 2.0;
                    double spawnY = pos != null ? pos[1] : WORLD_SIZE / 2.0;
                    double speed  = isBossWave ? BOSS_SPEED : MINIBOSS_SPEED;
                    double diffMult = 0.7 + (difficulty - 1) * 0.3;
                    double dx = WORLD_SIZE / 2.0 - spawnX, dy = WORLD_SIZE / 2.0 - spawnY;
                    double len = Math.sqrt(dx * dx + dy * dy);
                    if (len < 1) { dx = 1; dy = 0; len = 1; }

                    String newId = this.worldEvolver.addDynamicBodyAndGetId(
                            isBossWave ? BOSS_ASSET : MINIBOSS_ASSET,
                            isBossWave ? BOSS_SIZE  : MINIBOSS_SIZE,
                            spawnX, spawnY,
                            dx / len * speed * diffMult, dy / len * speed * diffMult,
                            0, 0, Math.toDegrees(Math.atan2(dy, dx)), 0, 0, 0);

                    if (newId != null) {
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

                bonusScore += isBossWave ? SCORE_BOSS : SCORE_MINIBOSS;
                if (gameRules != null) gameRules.unregisterBoss(id);
                bossId        = null;
                bossHpCurrent = 0;
                this.worldEvolver.clearBossHealth();

                // Victoria: el boss final ha sido derrotado
                if (isBossWave) {
                    if (pos != null)
                        this.worldEvolver.spawnExplosion(pos[0], pos[1], 300.0, 1.5);
                    this.worldEvolver.addScore(bonusScore);
                    this.worldEvolver.showVictory();
                    this.stop(); // detener el spawner
                    return;
                }
            }

            if (pos != null) explosions.add(new double[]{pos[0], pos[1]});
            toRemove.add(id);
        }

        if (toRemove.isEmpty()) return;

        toRemove.forEach(id -> { activeEnemyIds.remove(id); lastKnownPos.remove(id); });

        int normalKills = toRemove.size() - (bonusScore > 0 ? 1 : 0);
        this.worldEvolver.addScore(normalKills * SCORE_PER_KILL * currentWave * difficulty + bonusScore);

        for (double[] pos : explosions) {
            boolean wasBoss = bonusScore > 0 && explosions.size() == 1;
            this.worldEvolver.spawnExplosion(pos[0], pos[1],
                    wasBoss ? (currentWave == BOSS_WAVE ? 300.0 : 200.0) : 140.0, 1.2);
        }
    }

    /** Spawna una nave desde un borde aleatorio apuntando al centro del mundo. */
    private String spawnFromBorder(String assetId, double size, double speed) {
        double margin = 300d;
        double center = WORLD_SIZE / 2.0;
        int border = rnd.nextInt(4);
        double spawnX, spawnY;
        switch (border) {
            case 0  -> { spawnX = margin + rnd.nextDouble() * (WORLD_SIZE - 2*margin); spawnY = margin; }
            case 1  -> { spawnX = margin + rnd.nextDouble() * (WORLD_SIZE - 2*margin); spawnY = WORLD_SIZE - margin; }
            case 2  -> { spawnX = WORLD_SIZE - margin; spawnY = margin + rnd.nextDouble() * (WORLD_SIZE - 2*margin); }
            default -> { spawnX = margin;              spawnY = margin + rnd.nextDouble() * (WORLD_SIZE - 2*margin); }
        }
        double dx = center - spawnX, dy = center - spawnY;
        double len = Math.sqrt(dx*dx + dy*dy);
        return this.worldEvolver.addDynamicBodyAndGetId(
                assetId, size, spawnX, spawnY,
                dx / len * speed, dy / len * speed,
                0, 0, Math.toDegrees(Math.atan2(dy, dx)), 0, 0, 0);
    }

    private String spawnEnemy() {
        return spawnFromBorder(ENEMY_ASSET, ENEMY_SIZE, currentSpeed());
    }

    private String spawnMiniboss() {
        String id = spawnFromBorder(MINIBOSS_ASSET, MINIBOSS_SIZE, MINIBOSS_SPEED * (0.7 + (difficulty - 1) * 0.3));
        if (id != null) {
            bossHpMax = bossHpCurrent = MINIBOSS_HP;
            this.worldEvolver.setBossHealth(bossHpCurrent, bossHpMax, false);
            if (gameRules != null) gameRules.registerBoss(id);
        }
        return id;
    }

    private String spawnBoss() {
        String id = spawnFromBorder(BOSS_ASSET, BOSS_SIZE, BOSS_SPEED * (0.7 + (difficulty - 1) * 0.3));
        if (id != null) {
            bossHpMax = bossHpCurrent = BOSS_HP;
            this.worldEvolver.setBossHealth(bossHpCurrent, bossHpMax, true);
            if (gameRules != null) gameRules.registerBoss(id);
        }
        return id;
    }
}

