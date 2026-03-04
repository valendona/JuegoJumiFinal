package engine.model.bodies.impl;

import java.util.List;

import engine.events.domain.ports.BodyToEmitDTO;
import engine.model.bodies.ports.BodyEventProcessor;
import engine.model.bodies.ports.BodyType;
import engine.model.bodies.ports.PlayerDTO;
import engine.model.emitter.impl.BasicEmitter;
import engine.model.emitter.ports.EmitterConfigDto;
import engine.model.physics.ports.PhysicsEngine;
import engine.model.physics.ports.PhysicsValuesDTO;
import engine.utils.profiling.impl.BodyProfiler;
import engine.utils.spatial.core.SpatialGrid;

public class PlayerBody extends DynamicBody {

    private static final boolean PLAYERS_EXCLUSIVE = true;

    // region Fields
    private final List<String> weaponIds = new java.util.ArrayList<>(4);
    private int currentWeaponIndex = -1; // -1 = sin arma
    private double damage = 0D;
    private double energye = 1D;
    private int temperature = 1;
    private double shield = 1D;
    private int score = 0;

    // Boost (SHIFT): 0.0 = vacío, 1.0 = lleno
    private double boostEnergy   = 1.0;
    private boolean boostActive  = false;
    private static final double BOOST_DRAIN_PER_SEC  = 0.55;
    private static final double BOOST_REGEN_PER_SEC  = 0.18;
    private static final double BOOST_FORCE_MULT     = 2.2;
    private static final double BOOST_MIN_TO_ACTIVATE = 0.15;

    // HP del jugador: 3 vidas, inmunidad temporal tras recibir un golpe
    private int    playerHp            = 3;
    private static final int    MAX_HP           = 3;
    private long   lastHitTime         = 0L;
    private static final long   HIT_IMMUNITY_MS  = 2000L; // 2 s de inmunidad tras golpe

    // Giro acumulativo con volatile para comunicación segura entre hilos Vista→Modelo
    private volatile int rotateDir = 0; // -1 = izquierda, 0 = suelto, +1 = derecha
    private double currentAngularAcc = 0.0; // aceleración angular acumulada (°/s²), solo leída en onTick
    /**
     * Velocidad a la que crece la aceleración angular mientras se mantiene la tecla (°/s² por segundo).
     * Con maxAngularAcc=280: tarda 280/600 ≈ 0.47s en llegar al tope manteniendo pulsado.
     */
    private static final double ROTATE_BUILD_RATE = 2800.0;
    // endregion

    public PlayerBody(BodyEventProcessor bodyEventProcessor,
            SpatialGrid spatialGrid,
            PhysicsEngine physicsEngine,
            double maxLifeInSeconds,
            String emitterId,
            BodyProfiler profiler) {

        super(bodyEventProcessor,
                spatialGrid,
                physicsEngine,
                BodyType.PLAYER,
                maxLifeInSeconds,
                emitterId,
                profiler);

        this.setMaxThrustForce(800);
        this.setMaxAngularAcceleration(380); // techo de aceleración angular
        this.setAngularSpeed(0);
    }

    @Override
    public synchronized void activate() {
        super.activate(); // Calls AbstractBody.activate()

        this.setState(engine.model.bodies.ports.BodyState.ALIVE);
        // Threading is now handled by Model/BodyBatchManager
        // Players will be assigned to batch size 1 (exclusive) by Model
    }

    public void addWeapon(String emitterId) {
        this.weaponIds.add(emitterId);

        if (this.currentWeaponIndex < 0) {
            // Signaling existence of weapon in the spaceship
            this.currentWeaponIndex = 0;
        }
    }

    // region Getters (get***)
    public BasicEmitter getActiveWeapon() {
        if (this.currentWeaponIndex < 0 || this.currentWeaponIndex >= this.weaponIds.size()) {
            return null;
        }

        // return this.weapons.get(this.currentWeaponIndex);
        return this.getEmitter(this.weaponIds.get(this.currentWeaponIndex));

    }

    public int getActiveWeaponIndex() {
        if (this.currentWeaponIndex < 0 || this.currentWeaponIndex >= this.weaponIds.size()) {
            return -1;
        }

        return this.currentWeaponIndex;
    }

    public EmitterConfigDto getActiveWeaponConfig() {
        BasicEmitter emitter = getActiveWeapon();

        return (emitter != null) ? emitter.getConfig() : null;
    }

    public double getAmmoStatusPrimary() {
        return getAmmoStatus(0);
    }

    public double getAmmoStatusSecondary() {
        return getAmmoStatus(1);
    }

    public double getAmmoStatusMines() {
        return getAmmoStatus(2);
    }

    public double getAmmoStatusMissiles() {
        return getAmmoStatus(3);
    }

    private double getAmmoStatus(int weaponIndex) {
        if (weaponIndex < 0 || weaponIndex >= this.weaponIds.size()) {
            return 0.0d;
        }

        BasicEmitter emitter = this.getEmitter(this.weaponIds.get(weaponIndex));
        if (emitter == null) {
            return 0.0d;
        }

        if (emitter.getConfig().unlimitedBodies) {
            return 1.0d;
        }

        int maxBodies = emitter.getConfig().maxBodiesEmitted;
        if (maxBodies <= 0) {
            return 0.0d;
        }

        double ratio = emitter.getBodiesRemaining() / (double) maxBodies;
        return Math.max(0.0d, Math.min(1.0d, ratio));
    }

    public double getDamage() {
        return damage;
    }

    public PlayerDTO getData() {
        return new PlayerDTO(
                this.getBodyId(),
                "",
                this.damage,
                this.energye,
                this.shield,
                this.temperature,
                this.getActiveWeaponIndex(),
                this.getAmmoStatusPrimary(),
                this.getAmmoStatusSecondary(),
                this.getAmmoStatusMines(),
                this.getAmmoStatusMissiles(),
                this.score,
                this.boostEnergy,
                this.playerHp);
    }

    public int getPlayerHp() { return playerHp; }

    /**
     * Recibe un golpe. Descuenta 1 HP con inmunidad temporal.
     * @return true si el jugador muere (HP llega a 0), false si solo pierde 1 HP.
     */
    public boolean takeHit() {
        long now = System.currentTimeMillis();
        if (now - lastHitTime < HIT_IMMUNITY_MS) return false; // inmune, ignora
        lastHitTime = now;
        playerHp = Math.max(0, playerHp - 1);
        return playerHp <= 0;
    }

    /** @return true si el jugador está en período de inmunidad post-golpe */
    public boolean isHitImmune() {
        return (System.currentTimeMillis() - lastHitTime) < HIT_IMMUNITY_MS;
    }

    public double getEnergy() {
        return energye;
    }

    public BodyToEmitDTO getProjectileConfig() {
        if (this.currentWeaponIndex < 0 || this.currentWeaponIndex >= this.weaponIds.size()) {
            return null;
        }

        BasicEmitter emitter = this.getEmitter(this.weaponIds.get(this.currentWeaponIndex));
        if (emitter == null) {
            return null;
        }

        return emitter.getBodyToEmitConfig();
    }

    public double getShield() {
        return shield;
    }

    public int getTemperature() {
        return this.temperature;
    }
    // endregion

    public void registerFireRequest() {
        if (this.currentWeaponIndex < 0 || this.currentWeaponIndex >= this.weaponIds.size()) {
            System.out.println("> No weapon active or no weapons!");
            return;
        }

        BasicEmitter emitter = this.getEmitter(this.weaponIds.get(this.currentWeaponIndex));
        if (emitter == null) {
            // There is no weapon in this slot
            return;
        }

        emitter.registerRequest();
    }

    /** Thrust con multiplicador de boost aplicado. */
    @Override
    public void thrustMaxOn() {
        this.thurstNow(this.getMaxThrustForce() * getBoostMultiplier());
    }

    public void reverseThrust() {
        this.thurstNow(-this.getMaxThrustForce());
    }

    /** onTick: tickear boost y giro acumulativo antes de la física. */
    @Override
    public void onTick() {
        // Calcular dt desde el timestamp actual antes de la integración
        long prevNanos = this.getPhysicsEngine().getPhysicsValues().timeStamp;
        long nowNanos  = System.nanoTime();
        double dtSeconds = Math.min((nowNanos - prevNanos) / 1_000_000_000.0, 0.05);
        if (dtSeconds > 0) {
            tickBoost(dtSeconds);
            tickRotation(dtSeconds);
        }

        super.onTick();
    }

    /**
     * Acumula la aceleración angular mientras rotateDir != 0.
     * Al soltar (rotateDir == 0), resetea la aceleración a 0 inmediatamente:
     * el damping físico (ANGULAR_DAMPING en BasicPhysicsEngine) se encarga
     * de frenar la velocidad angular gradualmente.
     */
    private void tickRotation(double dt) {
        int dir = rotateDir; // leer volatile una sola vez
        double maxAcc = this.getMaxAngularAcceleration();

        if (dir != 0) {
            // Acumular aceleración en la dirección activa
            currentAngularAcc += dir * ROTATE_BUILD_RATE * dt;
            if (currentAngularAcc >  maxAcc) currentAngularAcc =  maxAcc;
            if (currentAngularAcc < -maxAcc) currentAngularAcc = -maxAcc;
        } else {
            // Tecla suelta → resetear aceleración al instante
            currentAngularAcc = 0.0;
        }
        this.setAngularAcceleration(currentAngularAcc);
    }

    /**
     * Activa el freno: aplica un thrust opuesto a la dirección de movimiento.
     * Usa la misma fuerza de propulsión máxima para frenar — frame-rate independent
     * porque integrateMRUA lo integrará con dt real.
     * Cuando la velocidad es casi nula, para en seco.
     */
    public void brake() {
        PhysicsValuesDTO cur = this.getPhysicsEngine().getPhysicsValues();
        double speed = Math.sqrt(cur.speedX * cur.speedX + cur.speedY * cur.speedY);

        if (speed < 3.0) {
            cur.speedX = 0.0;
            cur.speedY = 0.0;
            cur.thrust = 0.0;
            cur.accX   = 0.0;
            cur.accY   = 0.0;
            return;
        }

        // Fuerza de frenado suave — 0.55x la propulsión máxima
        double nx    = -cur.speedX / speed;
        double ny    = -cur.speedY / speed;
        double force = this.getMaxThrustForce() * 0.55;

        cur.accX   = nx * force;
        cur.accY   = ny * force;
        cur.thrust = 0.0;
    }

    public void brakeOff() {
        PhysicsValuesDTO cur = this.getPhysicsEngine().getPhysicsValues();
        cur.thrust = 0.0;
        cur.accX   = 0.0;
        cur.accY   = 0.0;
    }

    // *** BOOST (SHIFT) ***

    /** Activa el boost si hay suficiente energía. */
    public void boostOn() {
        if (boostEnergy >= BOOST_MIN_TO_ACTIVATE) {
            boostActive = true;
        }
    }

    /** Desactiva el boost. */
    public void boostOff() {
        boostActive = false;
    }

    /**
     * Llamar cada tick del modelo con el dt real.
     * Drena o regenera boostEnergy y aplica el multiplicador de empuje.
     */
    public void tickBoost(double dtSeconds) {
        if (boostActive && boostEnergy > 0) {
            boostEnergy -= BOOST_DRAIN_PER_SEC * dtSeconds;
            if (boostEnergy <= 0) {
                boostEnergy  = 0;
                boostActive  = false;
            }
        } else if (!boostActive) {
            boostEnergy = Math.min(1.0, boostEnergy + BOOST_REGEN_PER_SEC * dtSeconds);
        }
    }

    /** @return si el boost está activo ahora mismo */
    public boolean isBoostActive() { return boostActive && boostEnergy > 0; }

    /** @return energía de boost actual [0.0 – 1.0] */
    public double getBoostEnergy() { return boostEnergy; }

    /** @return multiplicador de thrust para aplicar al calcular la propulsión */
    public double getBoostMultiplier() {
        return (boostActive && boostEnergy > 0) ? BOOST_FORCE_MULT : 1.0;
    }

    /** Empieza a girar a la izquierda: activa el build-up de aceleración angular. */
    public void rotateLeftOn() {
        rotateDir = -1;
    }

    /** Empieza a girar a la derecha: activa el build-up de aceleración angular. */
    public void rotateRightOn() {
        rotateDir = 1;
    }

    /**
     * Suelta el giro: quita la dirección de giro.
     * tickRotation bajará currentAngularAcc a 0 gradualmente y el damping
     * del motor frenará la velocidad angular de forma natural.
     */
    public void rotateOff() {
        rotateDir = 0;
    }

    public void setDamage(double damage) {
        this.damage = damage;
    }

    public void setEnergye(double energye) {
        this.energye = energye;
    }

    public void selectNextWeapon() {
        if (this.weaponIds.size() <= 0) {
            return;
        }

        this.currentWeaponIndex++;
        this.currentWeaponIndex = this.currentWeaponIndex % this.weaponIds.size();
    }

    public void selectWeapon(int weaponIndex) {
        if (weaponIndex >= 0 && weaponIndex < this.weaponIds.size()) {
            this.currentWeaponIndex = weaponIndex;
        }
    }

    public void setShield(double shield) {
        this.shield = shield;
    }

    public void setTemperature(int temperature) {
        this.temperature = temperature;
    }

    public boolean mustFireNow(double dtSeconds) {
        if (this.currentWeaponIndex < 0 || this.currentWeaponIndex >= this.weaponIds.size()) {
            return false;
        }

        BasicEmitter emitter = this.getEmitter(this.weaponIds.get(this.currentWeaponIndex));
        if (emitter == null) {
            return false;
        }


        return emitter.mustEmitNow(dtSeconds);
    }
}