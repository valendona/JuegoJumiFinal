package gameworld;

import engine.assets.ports.AssetType;
import engine.utils.helpers.DoubleVector;
import engine.world.core.AbstractWorldDefinitionProvider;

public final class RandomWorldDefinitionProvider extends AbstractWorldDefinitionProvider {

	public RandomWorldDefinitionProvider(DoubleVector worldDimension, ProjectAssets assets) {
		super(worldDimension, assets);
	}

	@Override
	protected void define() {
		double density = 100d;

		this.setBackgroundStatic("back_19");

		this.addGravityBody("sun_03",    500,  500,  280);
		this.addGravityBody("planet_07", 3800, 700,  200);
		this.addGravityBody("moon_03",   3500, 3500, 100);
		this.addGravityBodyAnywhereRandomAsset(2, AssetType.PLANET, density, 80, 180);
		this.addGravityBodyAnywhereRandomAsset(2, AssetType.MOON,   density, 50, 100);

		this.registerAsset("spaceship_03");    // enemigo normal
		this.registerAsset("spaceship_07");    // miniboss
		this.registerAsset("spaceship_06");    // boss final
		this.registerAsset("explosion_sheet");

		this.addSpaceship("spaceship_01", 2250, 2250, 75);

		this.addWeaponPresetBulletRandomAsset(AssetType.BULLET);
		this.addWeaponPresetMissileLauncherRandomAsset(AssetType.MISSILE);
	}
}
