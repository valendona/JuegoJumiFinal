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

/**
 * AsteroidSurvivor — Reglas del juego "Asteroid Survivor"
 */
public class AsteroidSurvivor implements ActionsGenerator {

    /** IDs de bosses/minibosses: inmunes a colisiones con obstáculos (GRAVITY) */
    private final Set<String> bossIds = ConcurrentHashMap.newKeySet();

    public void registerBoss(String id)   { if (id != null) bossIds.add(id); }
    public void unregisterBoss(String id) { if (id != null) bossIds.remove(id); }

    // *** INTERFACE IMPLEMENTATIONS ***

    @Override
    public void provideActions(List<DomainEvent> domainEvents, List<ActionDTO> actions) {
        if (domainEvents != null) {
            for (DomainEvent event : domainEvents) {
                this.applyGameRules(event, actions);
            }
        }
    }

    // *** PRIVATE ***

    private void applyGameRules(DomainEvent event, List<ActionDTO> actions) {
        switch (event) {

            // region Límites — proyectiles mueren; el jugador y dinámicos rebotan
            case LimitEvent limitEvent -> {
                BodyType bt = limitEvent.primaryBodyRef.type();

                // Los proyectiles y misiles mueren al salir del mapa
                if (bt == BodyType.PROJECTILE) {
                    actions.add(new ActionDTO(
                            limitEvent.primaryBodyRef.id(), bt,
                            ActionType.DIE, event));
                    break;
                }

                // El resto rebota
                ActionType action = switch (limitEvent.type) {
                    case REACHED_EAST_LIMIT  -> ActionType.MOVE_REBOUND_IN_EAST;
                    case REACHED_WEST_LIMIT  -> ActionType.MOVE_REBOUND_IN_WEST;
                    case REACHED_NORTH_LIMIT -> ActionType.MOVE_REBOUND_IN_NORTH;
                    case REACHED_SOUTH_LIMIT -> ActionType.MOVE_REBOUND_IN_SOUTH;
                    default                  -> ActionType.NO_MOVE;
                };
                actions.add(new ActionDTO(
                        limitEvent.primaryBodyRef.id(), bt,
                        action, event));
            }
            // endregion

            // region Vida agotada — el cuerpo muere
            case LifeOver e ->
                actions.add(new ActionDTO(
                        e.primaryBodyRef.id(), e.primaryBodyRef.type(),
                        ActionType.DIE, event));
            // endregion

            // region Emisión — disparo o spawn de cuerpo
            case EmitEvent e -> {
                ActionType action = (e.type == DomainEventType.EMIT_REQUESTED)
                        ? ActionType.SPAWN_BODY
                        : ActionType.SPAWN_PROJECTILE;
                actions.add(new ActionDTO(
                        e.primaryBodyRef.id(), e.primaryBodyRef.type(),
                        action, event));
            }
            // endregion

            // region Colisión — lógica central del juego
            case CollisionEvent e -> resolveCollision(e, actions);
            // endregion

            default -> { /* Eventos no gestionados: ignorar */ }
        }
    }

    private void resolveCollision(CollisionEvent event, List<ActionDTO> actions) {
        BodyType primary   = event.primaryBodyRef.type();
        BodyType secondary = event.secondaryBodyRef.type();

        // Ignorar colisiones con DECORATOR
        if (primary == BodyType.DECORATOR || secondary == BodyType.DECORATOR) {
            return;
        }

        // Inmunidad de disparo propio: el proyectil no daña a quien lo disparó
        if (event.payload.haveImmunity) {
            return;
        }

        boolean primaryIsPlayer   = (primary   == BodyType.PLAYER);
        boolean secondaryIsPlayer = (secondary == BodyType.PLAYER);
        boolean anyPlayer = primaryIsPlayer || secondaryIsPlayer;

        boolean primaryIsStatic   = (primary   == BodyType.GRAVITY);
        boolean secondaryIsStatic = (secondary == BodyType.GRAVITY);

        // Los estáticos (planetas, lunas) son indestructibles
        if (primaryIsStatic || secondaryIsStatic) {
            // Si el otro cuerpo es un boss → ignorar colisión (el boss es inmune a obstáculos)
            String dynamicId = primaryIsStatic ? event.secondaryBodyRef.id() : event.primaryBodyRef.id();
            if (bossIds.contains(dynamicId)) return;

            // Solo muere el cuerpo no-estático (proyectil o dinámico)
            if (!primaryIsStatic) {
                actions.add(new ActionDTO(
                        event.primaryBodyRef.id(), primary, ActionType.DIE, event));
            }
            if (!secondaryIsStatic) {
                actions.add(new ActionDTO(
                        event.secondaryBodyRef.id(), secondary, ActionType.DIE, event));
            }
            return;
        }

        if (anyPlayer) {
            // Identificar cuál es el jugador y cuál el otro cuerpo
            String playerId  = primaryIsPlayer   ? event.primaryBodyRef.id()   : event.secondaryBodyRef.id();
            BodyType playerT = primaryIsPlayer   ? primary                      : secondary;
            String otherId   = primaryIsPlayer   ? event.secondaryBodyRef.id() : event.primaryBodyRef.id();
            BodyType otherT  = primaryIsPlayer   ? secondary                   : primary;

            // El jugador pierde 1 HP (PLAYER_HIT); si llega a 0 muere en el Model
            actions.add(new ActionDTO(playerId, playerT, ActionType.PLAYER_HIT, event));

            // El cuerpo que golpeó al jugador siempre muere (proyectil, nave enemiga, etc.)
            if (otherT != BodyType.GRAVITY) {
                actions.add(new ActionDTO(otherId, otherT, ActionType.DIE, event, event.payload.damage));
            }
        } else {
            // Proyectil vs Enemigo dinámico → mueren ambos, daño según proyectil
            int dmg = event.payload.damage;
            actions.add(new ActionDTO(
                    event.primaryBodyRef.id(), primary, ActionType.DIE, event, dmg));
            actions.add(new ActionDTO(
                    event.secondaryBodyRef.id(), secondary, ActionType.DIE, event, dmg));
        }
    }
}


