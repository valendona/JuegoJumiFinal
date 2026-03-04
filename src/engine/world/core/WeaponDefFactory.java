package engine.world.core;

import engine.model.bodies.ports.BodyType;
import engine.world.ports.DefEmitterDTO;

public final class WeaponDefFactory {

    // *** PUBLIC STATICS ***

    public static DefEmitterDTO create(
            // Projectile params
            String assetId,
            double projectileSize,
            double projectileSpeed,
            double projectileThrust,
            double projectileThrustDuration,
            double projectileMass,
            double projectileMaxLifetime,

            // Firing params
            int burstSize,
            int burstFireRate,
            int fireRate,
            boolean unlimitedAmmo,
            int maxAmmo,
            double reloadTime) {

        // Fail-fast validations (keep them light but useful)
        if (projectileSize <= 0)
            throw new IllegalArgumentException("projectileSize must be > 0");
        if (projectileMass <= 0)
            throw new IllegalArgumentException("projectileMass must be > 0");
        if (projectileMaxLifetime <= 0)
            throw new IllegalArgumentException("projectileMaxLifetime must be > 0");
        if (burstSize < 0)
            throw new IllegalArgumentException("burstSize must be >= 0");
        if (fireRate < 0)
            throw new IllegalArgumentException("fireRate must be >= 0");
        if (maxAmmo <= 0)
            throw new IllegalArgumentException("maxAmmo must be > 0");
        if (reloadTime < 0)
            throw new IllegalArgumentException("reloadTime must be >= 0");

        // If burstSize > 1, burstFireRate should be > 0 (otherwise burst is
        // meaningless).
        if (burstSize > 1 && burstFireRate <= 0) {
            throw new IllegalArgumentException("burstFireRate must be > 0 when burstSize > 1");
        }

        return new DefEmitterDTO(
                assetId,
                0d,
                0d,
                true,
                projectileThrust,
                projectileThrustDuration,
                projectileMass,
                projectileMaxLifetime,
                projectileSize,
                projectileSpeed,
                BodyType.PROJECTILE,

                burstFireRate,
                burstSize,
                fireRate,
                0d,
                0d,
                reloadTime,
                unlimitedAmmo,
                maxAmmo,
                false,
                false);
    }

    public static DefEmitterDTO createPresetedBulletBasic(String assetId) {
        return create(
                assetId,
                20.0d,   // tamaño proyectil
                650.0d,  // velocidad
                0.0d,    // thrust
                0.0d,    // thrustDuration
                1.0d,    // masa
                2,       // maxLifetime segundos

                0,       // burstSize
                0,       // burstFireRate
                4,       // fireRate: 4 disparos/segundo
                true,    // unlimitedAmmo
                1,       // maxAmmo (ignorado con unlimited)
                0.0);    // reloadTime
    }

    public static DefEmitterDTO createPresetedBurst(String assetId) {
        return create(
                assetId,
                15.0d,
                1000.0d,
                0.0d,
                0.0d,
                0.8d,
                1,

                10,
                300,
                50,
                false,
                100,
                5.0);
    }

    public static DefEmitterDTO createPresetedMissileLauncher(String assetId) {
        return create(
                assetId,
                60.0,    // tamaño proyectil (mayor que bala → 2 de daño en boss)
                4000.0,  // velocidad
                880.0,   // thrust
                1.8d,    // thrustDuration
                100.0d,  // masa (daño 2 — se distingue por tamaño ≥ 50)
                5,       // maxLifetime segundos

                0,       // burstSize
                0,       // burstFireRate
                1,       // fireRate: 1 disparo/segundo
                true,    // unlimitedAmmo
                1,       // maxAmmo (ignorado con unlimited)
                0.0);    // reloadTime (sin recarga)
    }

    public static DefEmitterDTO createPresetedMineLauncher(String assetId) {
        return create(
                assetId,
                48.0,
                -100.0d,
                0.0,
                0.0d,
                10.0d,
                60,

                0,
                0,
                10,
                false,
                10,
                10.0);
    }
}
