package engine.model.physics.implementations;

import static java.lang.System.nanoTime;

import engine.model.physics.core.AbstractPhysicsEngine;
import engine.model.physics.ports.PhysicsValuesDTO;
import engine.utils.profiling.impl.BodyProfiler;

public class BasicPhysicsEngine extends AbstractPhysicsEngine {

    // region Fields
    private final BodyProfiler profiler;
    // endregion

    // region Constructors
    public BasicPhysicsEngine(PhysicsValuesDTO dto1, PhysicsValuesDTO dto2, PhysicsValuesDTO dto3, BodyProfiler profiler) {
        super(dto1, dto2, dto3);
        this.profiler = profiler;
    }
    // endregion

    // *** PUBLIC ***

    @Override
    public void angularAccelerationInc(double angularAcc) {
        PhysicsValuesDTO old = this.getPhysicsValues();
        
        // Update nextPhyValues instead of creating new DTO
        nextPhyValues.update(
                old.timeStamp,
                old.posX, old.posY, old.angle,
                old.size,
                old.speedX, old.speedY,
                old.accX, old.accY,
                old.angularSpeed,
                old.angularAcc + angularAcc,
                old.thrust);
        
        this.setPhysicsValues(nextPhyValues);
    }

    @Override
    public PhysicsValuesDTO calcNewPhysicsValues() {
        long dtStart = this.profiler.startInterval();
        PhysicsValuesDTO phyVals = this.getPhysicsValues();
        long now = nanoTime();
        long elapsedNanos = now - phyVals.timeStamp;
        double dt = ((double) elapsedNanos) / 1_000_000_000.0d;

        // Si dt es anómalo (motor estaba pausado, lag, etc.) resetear el timestamp
        // y no aplicar física en este tick — el siguiente tick tendrá dt normal.
        if (dt <= 0.0 || dt > 0.05) {
            phyVals.timeStamp = now;
            this.profiler.stopInterval("PHYSICS_DT", dtStart);
            return phyVals; // devolver estado sin cambios
        }
        this.profiler.stopInterval("PHYSICS_DT", dtStart);

        return integrateMRUA(phyVals, dt);
    }

    @Override
    public boolean isThrusting() {
        PhysicsValuesDTO phyValues = this.getPhysicsValues();
        return phyValues.thrust != 0.0d;
    }

    // region Rebounds
    @Override
    public void reboundInEast(PhysicsValuesDTO phyValues,
            double worldDim_x, double worldDim_y) {

        // New speed: horizontal component flipped, vertical preserved
        double speedX = -phyValues.speedX;
        double speedY = phyValues.speedY;

        // New position: snapped to the east boundary (slightly inside)
        double posX = 0.0001d;
        double posY = phyValues.posY;
        double angle = phyValues.angle;

        // Acceleration is preserved
        double accX = phyValues.accX;
        double accY = phyValues.accY;

        // Update nextPhyValues instead of creating new DTO
        nextPhyValues.update(
                phyValues.timeStamp,
                posX, posY, angle,
                phyValues.size,
                speedX, speedY,
                accX, accY,
                phyValues.angularSpeed, phyValues.angularSpeed,
                phyValues.thrust);

        this.setPhysicsValues(nextPhyValues);
    }

    @Override
    public void reboundInWest(PhysicsValuesDTO phyValues,
            double worldDim_x, double worldDim_y) {

        // New speed: horizontal component flipped, vertical preserved
        double speedX = -phyValues.speedX;
        double speedY = phyValues.speedY;

        // New position: snapped to the east boundary (slightly inside)
        double posX = worldDim_x - 0.0001;
        double posY = phyValues.posY;
        double angle = phyValues.angle;

        // Acceleration is preserved
        double accX = phyValues.accX;
        double accY = phyValues.accY;

        // Update nextPhyValues instead of creating new DTO
        nextPhyValues.update(
                phyValues.timeStamp,
                posX, posY, angle,
                phyValues.size,
                speedX, speedY,
                accX, accY,
                phyValues.angularSpeed, phyValues.angularSpeed,
                phyValues.thrust);

        this.setPhysicsValues(nextPhyValues);
    }

    @Override
    public void reboundInNorth(PhysicsValuesDTO phyValues, double worldDim_x, double worldDim_y) {

        // New speed: horizontal component flipped, vertical preserved
        double speedX = phyValues.speedX;
        double speedY = -phyValues.speedY;

        // New position: snapped to the east boundary (slightly inside)
        double posX = phyValues.posX;
        double posY = 0.0001;
        double angle = phyValues.angle;

        // Acceleration is preserved
        double accX = phyValues.accX;
        double accY = phyValues.accY;

        // Update nextPhyValues instead of creating new DTO
        nextPhyValues.update(
                phyValues.timeStamp,
                posX, posY, angle,
                phyValues.size,
                speedX, speedY,
                accX, accY,
                phyValues.angularSpeed, phyValues.angularSpeed,
                phyValues.thrust);

        this.setPhysicsValues(nextPhyValues);
    }

    @Override
    public void reboundInSouth(PhysicsValuesDTO phyValues, double worldDim_x, double worldDim_y) {

        // New speed: horizontal component flipped, vertical preserved
        double speedX = phyValues.speedX;
        double speedY = -phyValues.speedY;

        // New position: snapped to the east boundary (slightly inside)
        double posX = phyValues.posX;
        double posY = worldDim_y - 0.0001;
        double angle = phyValues.angle;

        // Acceleration is preserved
        double accX = phyValues.accX;
        double accY = phyValues.accY;

        // Update nextPhyValues instead of creating new DTO
        nextPhyValues.update(
                phyValues.timeStamp,
                posX, posY, angle,
                phyValues.size,
                speedX, speedY,
                accX, accY,
                phyValues.angularSpeed, phyValues.angularSpeed,
                phyValues.thrust);

        this.setPhysicsValues(nextPhyValues);
    }
    // endregion

    @Override
    public void setAngularSpeed(double angularSpeed) {
        PhysicsValuesDTO old = this.getPhysicsValues();
        
        // Update nextPhyValues instead of creating new DTO
        nextPhyValues.update(
                old.timeStamp,
                old.posX, old.posY, old.angle,
                old.size,
                old.speedX, old.speedY,
                old.accX, old.accY,
                angularSpeed,
                old.angularAcc,
                old.thrust);
        
        this.setPhysicsValues(nextPhyValues);
    }

    // *** PRIVATES ***

    private static final double MAX_LINEAR_SPEED  = 600.0; // unidades/s máximas
    private static final double MAX_ANGULAR_SPEED = 220.0; // grados/s máximos — velocidad máxima original
    /** Damping lineal: factor de retención por segundo (0.97 → pierde ~3% vel/s sin thrust) */
    private static final double LINEAR_DAMPING    = 0.97;
    /** Damping angular: frena la inercia de giro al soltar la tecla */
    private static final double ANGULAR_DAMPING   = 0.85;

    private PhysicsValuesDTO integrateMRUA(PhysicsValuesDTO phyVals, double dt) {
        // Applying thrust according actual angle.
        // Si thrust != 0 → calcular aceleración desde angle+thrust (propulsión normal).
        // Si thrust == 0 → usar accX/accY del DTO tal cual (pueden venir de brake()).
        long thrustStart = this.profiler.startInterval();
        double accX;
        double accY;
        if (phyVals.thrust != 0.0d) {
            double angleRad = Math.toRadians(phyVals.angle);
            accX = Math.cos(angleRad) * phyVals.thrust;
            accY = Math.sin(angleRad) * phyVals.thrust;
        } else {
            accX = phyVals.accX; // freno u otras fuerzas externas
            accY = phyVals.accY;
        }
        this.profiler.stopInterval("PHYSICS_THRUST", thrustStart);

        long linearStart = this.profiler.startInterval();
        // v1 = v0 + a*dt  (con damping lineal suave)
        double oldSpeedX = phyVals.speedX;
        double oldSpeedY = phyVals.speedY;
        double dampFactor = Math.pow(LINEAR_DAMPING, dt);
        double newSpeedX = (oldSpeedX + accX * dt) * dampFactor;
        double newSpeedY = (oldSpeedY + accY * dt) * dampFactor;

        // Clamp velocidad lineal máxima
        double speed = Math.sqrt(newSpeedX * newSpeedX + newSpeedY * newSpeedY);
        if (speed > MAX_LINEAR_SPEED) {
            double scale = MAX_LINEAR_SPEED / speed;
            newSpeedX *= scale;
            newSpeedY *= scale;
        }

        // avg_speed = (v0 + v1) / 2
        double avgSpeedX = (oldSpeedX + newSpeedX) * 0.5;
        double avgSpeedY = (oldSpeedY + newSpeedY) * 0.5;

        // x1 = x0 + v_avg * dt
        double newPosX = phyVals.posX + avgSpeedX * dt;
        double newPosY = phyVals.posY + avgSpeedY * dt;
        this.profiler.stopInterval("PHYSICS_LINEAR", linearStart);

        long angularStart = this.profiler.startInterval();
        // w1 = w0 + α*dt  (con damping angular para dar inercia natural al giro)
        double oldAngularSpeed = phyVals.angularSpeed;
        double angularDampFactor = Math.pow(ANGULAR_DAMPING, dt);
        double newAngularSpeed = (oldAngularSpeed + phyVals.angularAcc * dt) * angularDampFactor;

        // Si la aceleración es opuesta a la velocidad y cruzamos el 0 → parar en 0
        double angularAccForDto = phyVals.angularAcc;
        if (oldAngularSpeed != 0 && phyVals.angularAcc != 0) {
            if (Math.signum(oldAngularSpeed) != Math.signum(newAngularSpeed)) {
                newAngularSpeed = 0.0;
                angularAccForDto = 0.0; // detener la aceleración para no rebotar
            }
        }

        // Clamp velocidad angular máxima
        if (newAngularSpeed > MAX_ANGULAR_SPEED)  newAngularSpeed =  MAX_ANGULAR_SPEED;
        if (newAngularSpeed < -MAX_ANGULAR_SPEED) newAngularSpeed = -MAX_ANGULAR_SPEED;

        // θ1 = θ0 + w0*dt + 0.5*α*dt^2
        double newAngle = (phyVals.angle
                + phyVals.angularSpeed * dt
                + 0.5d * newAngularSpeed * dt * dt) % 360;
        this.profiler.stopInterval("PHYSICS_ANGULAR", angularStart);

        long dtoStart = this.profiler.startInterval();
        long newTimeStamp = phyVals.timeStamp + (long) (dt * 1_000_000_000.0d);

        // accX/accY: si vienen de thrust se recalculan cada tick automáticamente.
        // Si vienen de fuerzas externas (freno), se consumen una vez y se limpian.
        double storedAccX = (phyVals.thrust != 0.0) ? accX : 0.0;
        double storedAccY = (phyVals.thrust != 0.0) ? accY : 0.0;

        // Update nextPhyValues instead of creating new DTO
        nextPhyValues.update(
                newTimeStamp,
                newPosX, newPosY, newAngle,
                phyVals.size,
                newSpeedX, newSpeedY,
                storedAccX, storedAccY,
                newAngularSpeed,
                angularAccForDto,
                phyVals.thrust
        );
        this.profiler.stopInterval("PHYSICS_DTO", dtoStart);

        return nextPhyValues;
    }
}
