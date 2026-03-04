package engine.view.renderables.ports;

import engine.model.bodies.ports.PlayerDTO;

public class PlayerRenderDTO {
    public final String entityId;
    public final String playerName;
    public final double damage;
    public final double energy;
    public final double shield;
    public final int temperature;
    public final int activeWeapon;
    public final double primaryAmmoStatus;
    public final double secondaryAmmoStatus;
    public final double minesStatus;
    public final double missilesStatus;
    public final double boostEnergy;
    /** Vidas del jugador (1-3) */
    public final int playerHp;

    public PlayerRenderDTO(
            String entityId, String playerName,
            double damage, double energy, double shield,
            int temperature, int activeWeapon,
            double primaryAmmoStatus, double secondaryAmmoStatus,
            double minesStatus, double missilesStatus,
            double boostEnergy, int playerHp) {

        this.entityId = entityId;
        this.playerName = playerName;
        this.damage = damage;
        this.energy = energy;
        this.shield = shield;
        this.temperature = temperature;
        this.activeWeapon = activeWeapon;
        this.primaryAmmoStatus = primaryAmmoStatus;
        this.secondaryAmmoStatus = secondaryAmmoStatus;
        this.minesStatus = minesStatus;
        this.missilesStatus = missilesStatus;
        this.boostEnergy = boostEnergy;
        this.playerHp = playerHp;
    }

    public PlayerDTO toPlayerDTO() {
        return new PlayerDTO(entityId, playerName, damage, energy, shield,
                temperature, activeWeapon,
                primaryAmmoStatus, secondaryAmmoStatus, minesStatus, missilesStatus,
                0, boostEnergy, playerHp);
    }

    public Object[] toObjectArray() {
        return new Object[] {
                entityId, playerName, damage, energy, shield,
                temperature, activeWeapon,
                primaryAmmoStatus, secondaryAmmoStatus, minesStatus, missilesStatus,
                boostEnergy, playerHp
        };
    }
}
