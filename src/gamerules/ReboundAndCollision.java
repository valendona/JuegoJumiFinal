package gamerules;

import java.util.List;

import engine.actions.ActionDTO;
import engine.actions.ActionType;
import engine.controller.ports.ActionsGenerator;
import engine.events.domain.ports.DomainEventType;
import engine.events.domain.ports.eventtype.CollisionEvent;
import engine.events.domain.ports.eventtype.DomainEvent;
import engine.events.domain.ports.eventtype.EmitEvent;
import engine.events.domain.ports.eventtype.LifeOver;
import engine.events.domain.ports.eventtype.LimitEvent;
import engine.model.bodies.ports.BodyType;

public class ReboundAndCollision implements ActionsGenerator {

    @Override
    public void provideActions(List<DomainEvent> domainEvents, List<ActionDTO> actions) {
        if (domainEvents != null)
            for (DomainEvent event : domainEvents)
                applyGameRules(event, actions);
    }

    private void applyGameRules(DomainEvent event, List<ActionDTO> actions) {
        switch (event) {
            case LimitEvent e -> {
                ActionType action = switch (e.type) {
                    case REACHED_EAST_LIMIT  -> ActionType.MOVE_REBOUND_IN_EAST;
                    case REACHED_WEST_LIMIT  -> ActionType.MOVE_REBOUND_IN_WEST;
                    case REACHED_NORTH_LIMIT -> ActionType.MOVE_REBOUND_IN_NORTH;
                    case REACHED_SOUTH_LIMIT -> ActionType.MOVE_REBOUND_IN_SOUTH;
                    default                  -> ActionType.NO_MOVE;
                };
                actions.add(new ActionDTO(e.primaryBodyRef.id(), e.primaryBodyRef.type(), action, event));
            }
            case LifeOver e ->
                actions.add(new ActionDTO(e.primaryBodyRef.id(), e.primaryBodyRef.type(), ActionType.DIE, event));
            case EmitEvent e -> {
                ActionType action = (e.type == DomainEventType.EMIT_REQUESTED)
                        ? ActionType.SPAWN_BODY : ActionType.SPAWN_PROJECTILE;
                actions.add(new ActionDTO(e.primaryBodyRef.id(), e.primaryBodyRef.type(), action, event));
            }
            case CollisionEvent e -> resolveCollision(e, actions);
            default -> {}
        }
    }

    private void resolveCollision(CollisionEvent event, List<ActionDTO> actions) {
        BodyType primary   = event.primaryBodyRef.type();
        BodyType secondary = event.secondaryBodyRef.type();
        if (primary == BodyType.DECORATOR || secondary == BodyType.DECORATOR) return;
        if (event.payload.haveImmunity) return;
        actions.add(new ActionDTO(event.primaryBodyRef.id(),   primary,   ActionType.DIE, event));
        actions.add(new ActionDTO(event.secondaryBodyRef.id(), secondary, ActionType.DIE, event));
    }
}
