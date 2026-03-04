import engine.controller.impl.Controller;
import engine.controller.ports.ActionsGenerator;
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
		// region Graphics configuration
		System.setProperty("sun.java2d.uiScale", "1.0");
		System.setProperty("sun.java2d.opengl", "false");
		System.setProperty("sun.java2d.d3d", "true");
		System.setProperty("sun.java2d.ddoffscreen", "false");
		// endregion

		startGame();
	}

	/** Arranca (o reinicia) una partida completa en un hilo propio. */
	public static void startGame() {
		new Thread(() -> {
			// Tamaño real de la pantalla — la ventana se maximizará a este tamaño
			Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
			DoubleVector viewDimension  = new DoubleVector(screen.width, screen.height);
			DoubleVector worldDimension = new DoubleVector(4500, 4500);

			int maxBodies = 1500;
			int maxAsteroidCreationDelay = 4;

			ProjectAssets projectAssets = new ProjectAssets();
			gamerules.AsteroidSurvivor gameRules = new gamerules.AsteroidSurvivor();

			WorldDefinitionProvider worldProv = new gameworld.RandomWorldDefinitionProvider(
					worldDimension, projectAssets);

			Controller controller = new Controller(
					worldDimension, viewDimension, maxBodies,
					new View(), new Model(worldDimension, maxBodies),
					gameRules);

			controller.activate();
			controller.setRestartCallback(Main::startGame);

			WorldDefinition worldDef = worldProv.provide();
			new gamelevel.LevelBasic(controller, worldDef);
			new gameai.AIBasicSpawner(controller, worldDef, maxAsteroidCreationDelay)
					.withGameRules(gameRules)
					.activate();

		}, "GameStartThread").start();
	}
}
