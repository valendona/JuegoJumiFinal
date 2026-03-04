package gameworld;

import engine.assets.ports.AssetType;
import engine.model.bodies.ports.BodyType;
import engine.utils.helpers.DoubleVector;
import engine.world.core.AbstractWorldDefinitionProvider;
import engine.world.ports.DefEmitterDTO;

public final class RandomWorldDefinitionProvider extends AbstractWorldDefinitionProvider {

	// *** CONSTRUCTORS ***

	public RandomWorldDefinitionProvider(DoubleVector worldDimension, ProjectAssets assets) {
		super(worldDimension, assets);
	}

	// *** PROTECTED (alphabetical order) ***

	@Override
	protected void define() {
		double density = 100d;

		// region Background
		this.setBackgroundStatic("back_19");
		// endregion

		// region Gravity bodies — planetas, lunas y sol ajustados al mundo 4500x4500
		this.addGravityBody("sun_03",    500,  500,  280);
		this.addGravityBody("planet_07", 3800, 700,  200);
		this.addGravityBody("moon_03",   3500, 3500, 100);
		this.addGravityBodyAnywhereRandomAsset(2, AssetType.PLANET, density, 80, 180);
		this.addGravityBodyAnywhereRandomAsset(2, AssetType.MOON,   density, 50, 100);
		// endregion

		// region Dynamic bodies
		this.registerAsset("spaceship_03");    // enemigo normal
		this.registerAsset("spaceship_07");    // miniboss (oleada 5)
		this.registerAsset("spaceship_06");    // boss (oleada 10)
		this.registerAsset("explosion_sheet"); // spritesheet de explosión
		// endregion

		// region Players — nave del jugador en el centro del mundo (spaceship_01 siempre)
		this.addSpaceship("spaceship_01", 2250, 2250, 75);
		// endregion

		// region Weapons — bala básica y misiles
		this.addWeaponPresetBulletRandomAsset(AssetType.BULLET);


		this.addWeaponPresetMissileLauncherRandomAsset(AssetType.MISSILE);
		// endregion
	}
}
