package gamerules;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

public class AsteroidSurvivor implements ActionsGenerator {

    /** IDs de bosses registrados como inmunes a colisiones con obstáculos (GRAVITY). */
    private final Set<String> bossIds = ConcurrentHashMap.newKeySet();

    public void registerBoss(String id)   { if (id != null) bossIds.add(id); }
    public void unregisterBoss(String id) { if (id != null) bossIds.remove(id); }

    @Override
    public void provideActions(List<DomainEvent> domainEvents, List<ActionDTO> actions) {
        if (domainEvents != null) {
            for (DomainEvent event : domainEvents) {
                applyGameRules(event, actions);
            }
        }
    }

    private void applyGameRules(DomainEvent event, List<ActionDTO> actions) {
        switch (event) {
            case LimitEvent e -> {
                BodyType bt = e.primaryBodyRef.type();
                if (bt == BodyType.PROJECTILE) {
                    actions.add(new ActionDTO(e.primaryBodyRef.id(), bt, ActionType.DIE, event));
                    break;
                }
                ActionType action = switch (e.type) {
                    case REACHED_EAST_LIMIT  -> ActionType.MOVE_REBOUND_IN_EAST;
                    case REACHED_WEST_LIMIT  -> ActionType.MOVE_REBOUND_IN_WEST;
                    case REACHED_NORTH_LIMIT -> ActionType.MOVE_REBOUND_IN_NORTH;
                    case REACHED_SOUTH_LIMIT -> ActionType.MOVE_REBOUND_IN_SOUTH;
                    default                  -> ActionType.NO_MOVE;
                };
                actions.add(new ActionDTO(e.primaryBodyRef.id(), bt, action, event));
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

        boolean primaryIsPlayer   = primary   == BodyType.PLAYER;
        boolean secondaryIsPlayer = secondary == BodyType.PLAYER;
        boolean primaryIsStatic   = primary   == BodyType.GRAVITY;
        boolean secondaryIsStatic = secondary == BodyType.GRAVITY;

        if (primaryIsStatic || secondaryIsStatic) {
            String dynamicId = primaryIsStatic ? event.secondaryBodyRef.id() : event.primaryBodyRef.id();
            if (bossIds.contains(dynamicId)) return; // bosses son inmunes a obstáculos
            if (!primaryIsStatic)
                actions.add(new ActionDTO(event.primaryBodyRef.id(), primary, ActionType.DIE, event));
            if (!secondaryIsStatic)
                actions.add(new ActionDTO(event.secondaryBodyRef.id(), secondary, ActionType.DIE, event));
            return;
        }

        if (primaryIsPlayer || secondaryIsPlayer) {
            String playerId = primaryIsPlayer ? event.primaryBodyRef.id() : event.secondaryBodyRef.id();
            BodyType playerT = primaryIsPlayer ? primary : secondary;
            String otherId  = primaryIsPlayer ? event.secondaryBodyRef.id() : event.primaryBodyRef.id();
            BodyType otherT = primaryIsPlayer ? secondary : primary;
            actions.add(new ActionDTO(playerId, playerT, ActionType.PLAYER_HIT, event));
            if (otherT != BodyType.GRAVITY)
                actions.add(new ActionDTO(otherId, otherT, ActionType.DIE, event, event.payload.damage));
        } else {
            int dmg = event.payload.damage;
            actions.add(new ActionDTO(event.primaryBodyRef.id(),   primary,   ActionType.DIE, event, dmg));
            actions.add(new ActionDTO(event.secondaryBodyRef.id(), secondary, ActionType.DIE, event, dmg));
        }
    }
}
