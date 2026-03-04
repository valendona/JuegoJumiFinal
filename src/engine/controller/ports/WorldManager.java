package engine.controller.ports;

import engine.assets.core.AssetCatalog;
import engine.utils.helpers.DoubleVector;
import engine.world.ports.DefEmitterDTO;

public interface WorldManager {

        public void addDecorator(String assetId, double size, double posX, double posY, double angle);

        public void addDynamicBody(String assetId, double size, double posX, double posY,
                        double speedX, double speedY, double accX, double accY,
                        double angle, double angularSpeed, double angularAcc, double thrust);

        public String addDynamicBodyAndGetId(String assetId, double size, double posX, double posY,
                        double speedX, double speedY, double accX, double accY,
                        double angle, double angularSpeed, double angularAcc, double thrust);

        public void steerBodyToward(String bodyId, double targetX, double targetY, double thrust);

        /** Frena un cuerpo dinámico con la fuerza indicada (opuesta a su velocidad actual). */
        public void brakeBody(String bodyId, double brakeForce);

        /** @return la velocidad escalar actual de un cuerpo, o 0 si no existe */
        public double getBodySpeed(String bodyId);

        /** @return posición [x, y] de un cuerpo, o null si no existe */
        public double[] getBodyPosition(String bodyId);

        public String addPlayer(String assetId, double size, double posX, double posY,
                        double speedX, double speedY, double accX, double accY,
                        double angle, double angularSpeed, double angularAcc, double thrust);

        public void addStaticBody(String assetId, double size, double posX, double posY, double angle);

        public void equipTrail(
                        String playerId, DefEmitterDTO bodyEmitterDef);

        public void equipWeapon(String playerId, DefEmitterDTO bodyEmitterDef, int shootingOffset);

        public DoubleVector getWorldDimension();

        public EngineState getEngineState();

        public int getEntityAliveQuantity();

        public String getLocalPlayerId();

        public double[] getPlayerPosition(String playerId);

        /** @return true mientras la pantalla de inicio esté activa (antes de pulsar ENTER) */
        public boolean isIntroActive();

        /** @return la dificultad elegida en el IntroHUD (1=fácil, 2=normal, 3=difícil) */
        public int getIntroDifficulty();

        /** Informa al renderer de la oleada actual (se muestra en el GameOverHUD) */
        public void setCurrentWave(int wave);

        /** Actualiza el contador de enemigos en el WaveHUD. */
        public void setEnemiesInfo(int alive, int total);

        /** Muestra un anuncio de nueva oleada centrado en pantalla. */
        public void announceWave(String text);

        /** Suma puntos a la puntuación del jugador. */
        public void addScore(int pts);

        /** Reanuda el motor de física después de una pausa */
        public void engineResume();

        /** @return true si el body con ese ID está muerto o ya no existe */
        public boolean isBodyDead(String bodyId);

        public void setLocalPlayer(String playerId);

        public void loadAssets(AssetCatalog assets);

        /** Pasa al renderer las posiciones world de los enemigos vivos para dibujar flechas. */
        public void setEnemyPositions(double[] xs, double[] ys, int count);

        /**
         * Genera un decorador temporal en la posición indicada para simular una explosión.
         * @param posX  posición X en coordenadas world
         * @param posY  posición Y en coordenadas world
         * @param size  tamaño visual de la explosión
         * @param lifeSecs tiempo en segundos antes de que desaparezca
         */
        public void spawnExplosion(double posX, double posY, double size, double lifeSecs);

        /**
         * Informa al renderer la vida actual de un boss/miniboss para mostrar la barra.
         * @param current  HP actual (0 = muerto)
         * @param max      HP máximo
         * @param isBoss   true = boss final, false = miniboss
         */
        public void setBossHealth(int current, int max, boolean isBoss);

        /** Oculta la barra de vida del boss (cuando no hay boss vivo). */
        public void clearBossHealth();

        /**
         * Registra el daño del último proyectil que mató a un enemigo dinámico.
         * Lo usa AIBasicSpawner para restar el daño correcto al HP del boss.
         */
        public void reportBossDamage(int damage);

        /** @return el daño acumulado pendiente de aplicar al boss (resetea tras leer) */
        public int pollBossDamage();

        /**
         * Protege el renderable del boss viejo de ser eliminado hasta que el nuevo ID
         * esté listo — evita el parpadeo entre muerte y re-spawn del boss.
         */
        public void protectBossRenderable(String oldId, String newId);

        /** Muestra la pantalla de victoria al derrotar al boss final. */
        public void showVictory();
}
