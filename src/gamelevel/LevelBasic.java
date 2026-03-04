package gamelevel;

import java.util.ArrayList;

import engine.controller.ports.WorldManager;
import engine.generators.AbstractLevelGenerator;
import engine.world.ports.DefEmitterDTO;
import engine.world.ports.DefItem;
import engine.world.ports.DefItemDTO;
import engine.world.ports.WorldDefinition;

public class LevelBasic extends AbstractLevelGenerator {

    public LevelBasic(WorldManager worldManager, WorldDefinition worldDef) {
        super(worldManager, worldDef);
    }

    @Override
    protected void createDecorators() {
        for (DefItem def : this.getWorldDefinition().spaceDecorators)
            this.addDecoratorIntoTheGame(this.defItemToDTO(def));
    }

    @Override
    protected void createDynamics() {}

    @Override
    protected void createStatics() {
        for (DefItem def : this.getWorldDefinition().gravityBodies)
            this.addStaticIntoTheGame(this.defItemToDTO(def));
    }

    @Override
    protected void createPlayers() {
        WorldDefinition worldDef = this.getWorldDefinition();
        ArrayList<DefEmitterDTO> weapons = worldDef.weapons;
        ArrayList<DefEmitterDTO> trails  = worldDef.trailEmitters;
        for (DefItem def : worldDef.spaceships) {
            DefItemDTO body = this.defItemToDTO(def);
            this.addLocalPlayerIntoTheGame(body, weapons, trails);
        }
    }
}
