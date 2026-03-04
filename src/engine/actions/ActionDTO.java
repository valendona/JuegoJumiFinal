package engine.actions;

import engine.events.domain.ports.eventtype.DomainEvent;
import engine.model.bodies.ports.BodyType;

public class ActionDTO {
    final public String bodyId;
    final public BodyType bodyType;
    final public ActionType type;
    final public DomainEvent relatedEvent;
    /** Daño asociado a la acción (1=bala, 2=misil). 0 si no aplica. */
    final public int damage;

    public ActionDTO(String bodyId, BodyType bodyType, ActionType type, DomainEvent relatedEvent) {
        this.bodyId = bodyId;
        this.bodyType = bodyType;
        this.type = type;
        this.relatedEvent = relatedEvent;
        this.damage = 1;
    }

    public ActionDTO(String bodyId, BodyType bodyType, ActionType type, DomainEvent relatedEvent, int damage) {
        this.bodyId = bodyId;
        this.bodyType = bodyType;
        this.type = type;
        this.relatedEvent = relatedEvent;
        this.damage = damage;
    }
}