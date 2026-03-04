import engine.controller.impl.Controller;
import engine.model.impl.Model;
import engine.utils.helpers.DoubleVector;
import engine.view.core.View;
import engine.world.ports.WorldDefinition;
import engine.world.ports.WorldDefinitionProvider;
import gameworld.ProjectAssets;

import java.awt.Toolkit;
import java.awt.Dimension;

public class Main {

	public static void main(String[] args) {
		System.setProperty("sun.java2d.uiScale", "1.0");
		System.setProperty("sun.java2d.opengl", "false");
		System.setProperty("sun.java2d.d3d", "true");
		System.setProperty("sun.java2d.ddoffscreen", "false");
		startGame();
	}

	/** Arranca o reinicia una partida completa. Se usa también como callback de "reintentar". */
	public static void startGame() {
		new Thread(() -> {
			Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
			DoubleVector viewDimension  = new DoubleVector(screen.width, screen.height);
			DoubleVector worldDimension = new DoubleVector(4500, 4500);

			ProjectAssets projectAssets     = new ProjectAssets();
			gamerules.AsteroidSurvivor gameRules = new gamerules.AsteroidSurvivor();

			WorldDefinitionProvider worldProv = new gameworld.RandomWorldDefinitionProvider(
					worldDimension, projectAssets);

			Controller controller = new Controller(
					worldDimension, viewDimension, 1500,
					new View(), new Model(worldDimension, 1500),
					gameRules);

			controller.activate();
			controller.setRestartCallback(Main::startGame);

			WorldDefinition worldDef = worldProv.provide();
			new gamelevel.LevelBasic(controller, worldDef);
			new gameai.AIBasicSpawner(controller, worldDef, 4)
					.withGameRules(gameRules)
					.activate();

		}, "GameStartThread").start();
	}
}
