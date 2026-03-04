package engine.events.domain.ports.payloads;

public final class CollisionPayload implements DomainEventPayload {
    public final boolean haveImmunity;
    /** Daño que inflige el proyectil (1 = bala, 2 = misil). 0 si no es proyectil. */
    public final int damage;

    public CollisionPayload(boolean playerHaveImmunity) {
        this.haveImmunity = playerHaveImmunity;
        this.damage = 1;
    }

    public CollisionPayload(boolean playerHaveImmunity, int damage) {
        this.haveImmunity = playerHaveImmunity;
        this.damage = damage;
    }
}
