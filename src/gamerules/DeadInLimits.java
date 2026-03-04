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

public class DeadInLimits implements ActionsGenerator {

    @Override
    public void provideActions(List<DomainEvent> domainEvents, List<ActionDTO> actions) {
        if (domainEvents != null)
            for (DomainEvent event : domainEvents)
                applyGameRules(event, actions);
    }

    private void applyGameRules(DomainEvent event, List<ActionDTO> actions) {
        switch (event) {
            case LimitEvent e ->
                actions.add(new ActionDTO(e.primaryBodyRef.id(), e.primaryBodyRef.type(), ActionType.DIE, event));
            case LifeOver e ->
                actions.add(new ActionDTO(e.primaryBodyRef.id(), e.primaryBodyRef.type(), ActionType.DIE, event));
            case EmitEvent e -> {
                ActionType action = (e.type == DomainEventType.EMIT_REQUESTED)
                        ? ActionType.SPAWN_BODY : ActionType.SPAWN_PROJECTILE;
                actions.add(new ActionDTO(e.primaryBodyRef.id(), e.primaryBodyRef.type(), action, event));
            }
            case CollisionEvent ignored -> {}
            default -> {}
        }
    }
}
