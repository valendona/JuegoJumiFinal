# Guía completa del proyecto — MVCGameEngine

> Documento de referencia para entender la arquitectura, los paquetes y las clases del proyecto.  
> El juego implementado es **Asteroid Survivor**: una nave espacial del jugador sobrevive oleadas de naves enemigas, con un miniboss en la oleada 5 y un boss final en la oleada 10.

---

## 1. Visión general — Patrón MVC

El proyecto sigue el patrón **Modelo-Vista-Controlador (MVC)** de forma estricta:

```
┌──────────┐   acciones del jugador    ┌────────────┐   reglas del juego   ┌────────┐
│   VIEW   │ ────────────────────────► │ CONTROLLER │ ───────────────────► │ MODEL  │
│          │ ◄──────────────────────── │            │ ◄─────────────────── │        │
└──────────┘   datos de render (DTOs)  └────────────┘   eventos de dominio  └────────┘
```

| Capa | Responsabilidad |
|---|---|
| **Model** | Simula la física del mundo: posiciones, velocidades, colisiones, ciclo de vida de los cuerpos. No sabe nada de gráficos ni de input. |
| **View** | Dibuja el estado del juego en pantalla y captura el teclado. No accede al model directamente. |
| **Controller** | Intermediario. Traduce las pulsaciones de teclas en acciones del modelo, y el estado del modelo en datos de render para la vista. También aplica las **reglas del juego**. |

La comunicación entre capas se hace **exclusivamente mediante DTOs** (Data Transfer Objects) — objetos simples sin lógica que transportan datos de una capa a otra. Esto hace que cada capa sea independiente y testeable por separado.

---

## 2. Estructura de paquetes

```
src/
├── Main.java                        ← Punto de entrada
├── engine/                          ← Motor reutilizable (no específico del juego)
│   ├── actions/                     ← Comandos que se ejecutan sobre los cuerpos
│   ├── assets/                      ← Catálogo de imágenes y recursos
│   ├── controller/                  ← Capa Controller del MVC
│   ├── events/                      ← Sistema de eventos de dominio
│   ├── generators/                  ← Generadores de niveles e IA
│   ├── model/                       ← Capa Model del MVC (física, cuerpos)
│   ├── utils/                       ← Utilidades: imágenes, threading, spatial grid...
│   ├── view/                        ← Capa View del MVC (renderizado, HUDs)
│   └── world/                       ← Definición del mundo (configuración inicial)
├── gameai/                          ← IA específica del juego
├── gamelevel/                       ← Nivel específico del juego
├── gamerules/                       ← Reglas de colisión específicas del juego
└── gameworld/                       ← Assets y configuración del mundo del juego
```

---

## 3. Punto de entrada — `Main.java`

`Main` es la clase que arranca el programa. Contiene dos métodos:

- **`main()`** — Configura el sistema gráfico de Java (desactivar escalado, activar D3D) y llama a `startGame()`.
- **`startGame()`** — Método estático que se puede llamar también al **reiniciar** la partida. Crea todos los componentes del juego en un hilo separado (`GameStartThread`) y los conecta entre sí:

```
1. Crea ProjectAssets    → catálogo de todas las imágenes disponibles
2. Crea AsteroidSurvivor → reglas de colisión
3. Crea RandomWorldDefinitionProvider → define el mundo (planetas, armas, nave del jugador)
4. Crea Controller       → el cerebro que conecta todo
5. controller.activate() → arranca la ventana y los hilos
6. Crea LevelBasic       → spawna los planetas y la nave del jugador
7. Crea AIBasicSpawner   → lanza las oleadas de enemigos
```

---

## 4. Paquete `engine/actions`

Define los **comandos** que se pueden ejecutar sobre un cuerpo físico.

### `ActionType.java` (enum)
Lista de todas las acciones posibles:
- Movimiento: `MOVE_REBOUND_IN_EAST/WEST/NORTH/SOUTH`, `NO_MOVE`
- Vida: `DIE`, `PLAYER_HIT`, `EXPLODE_IN_FRAGMENTS`
- Spawn: `SPAWN_BODY`, `SPAWN_PROJECTILE`

### `ActionDTO.java`
Objeto que empaqueta una acción para ser ejecutada: contiene el ID del cuerpo destinatario, su tipo, la acción a realizar, el evento que la originó y el daño (opcional). Es el "sobre" que viaja desde las reglas del juego hasta el Model.

---

## 5. Paquete `engine/assets`

Gestiona el catálogo de imágenes del juego.

### `AssetCatalog.java`
Registro central de todos los assets. Cada asset se registra con:
- Un **ID lógico** (ej. `"spaceship_01"`) que el código usa
- Un **nombre de fichero** (ej. `"spaceship-01.png"`)
- Un **tipo** (`AssetType`: SPACESHIP, PLANET, BULLET, etc.)
- Una **intensidad** (`AssetIntensity`: HIGH, MEDIUM, LOW) que indica la frecuencia de uso

Cuando el `LevelBasic` arranca, llama a `controller.loadAssets(catalog)` que carga todas las imágenes del catálogo en memoria para que el renderer pueda usarlas.

---

## 6. Paquete `engine/controller`

### 6.1 `ports/EngineState.java` (enum)
Estado global del motor: `STARTING → ALIVE → PAUSED → STOPPED`. El renderer, los runners de física y el spawner de IA comprueban este estado en cada tick.

### 6.2 `ports/ActionsGenerator.java` (interfaz)
Interfaz que deben implementar las **reglas del juego**. Tiene un único método:
```java
void provideActions(List<DomainEvent> events, List<ActionDTO> actions)
```
El Model genera eventos (colisión, límite alcanzado, vida agotada...), el Controller se los pasa al `ActionsGenerator`, y éste devuelve las acciones a ejecutar. Así las reglas del juego están completamente separadas del motor.

### 6.3 `ports/WorldManager.java` (interfaz)
Interfaz que expone el Controller al `AIBasicSpawner` y al `LevelBasic`. Agrupa todos los métodos que necesitan para manipular el mundo: `addDynamicBody`, `steerBodyToward`, `brakeBody`, `spawnExplosion`, `showVictory`, etc. El spawner y el level nunca tienen una referencia directa al `Controller` concreto — solo conocen esta interfaz.

### 6.4 `impl/Controller.java`
**El coordinador central del juego.** Implementa `WorldManager` y `DomainEventProcessor`.

Responsabilidades principales:
- Recibe acciones del jugador desde la View (`playerThrustOn`, `playerFireWeapon`...) y las delega al Model.
- Recibe eventos de dominio del Model (`notifyDynamicKilled`, `notifyPlayerIsDead`) y los traduce a notificaciones para la View (explosiones, game over).
- Gestiona el **ciclo de vida del motor**: `activate()`, `enginePause()`, `engineResume()`, `engineStop()`.
- Controla el acumulador de daño al boss (`pendingBossDamage`) con un `AtomicInteger` thread-safe.
- Tiene el callback de reinicio (`setRestartCallback`) que `Main` registra para reiniciar la partida.

---

## 7. Paquete `engine/events`

Sistema de eventos que el Model genera cuando algo ocurre en la simulación.

### Tipos de eventos (`DomainEventType`)
| Evento | Cuándo se genera |
|---|---|
| `COLLISION` | Dos cuerpos se solapan |
| `REACHED_*_LIMIT` | Un cuerpo toca el borde del mundo |
| `LIFE_OVER` | Un cuerpo agotó su tiempo de vida máximo |
| `EMIT_REQUESTED` | Un emisor (arma/trail) quiere generar un proyectil |

### `CollisionEvent.java`
Evento de colisión. Lleva dos `BodyRefDTO` (el cuerpo primario y el secundario) y un `CollisionPayload` que incluye si hay inmunidad y el daño del proyectil (1 para bala, 2 para misil).

El flujo es:
```
Model detecta solapamiento → crea CollisionEvent → lo pasa al Controller
→ Controller lo pasa a AsteroidSurvivor.provideActions()
→ AsteroidSurvivor genera ActionDTO(DIE) para los cuerpos afectados
→ Controller devuelve las acciones al Model → Model mata los cuerpos
```

---

## 8. Paquete `engine/generators`

### `AbstractIAGenerator.java`
Clase base para generadores de IA que corren en un hilo propio. Implementa `Runnable`. El bucle principal es:

```java
while (!stopped && engineState != STOPPED) {
    if (engineState == ALIVE) {
        onTick();  // lógica de la subclase
    }
    Thread.sleep(maxCreationDelay); // pequeña pausa entre ticks
}
```

Cuando el motor está `PAUSED`, el generador simplemente no llama a `onTick()` — espera.  
El método `stop()` activa el flag `stopped` para terminar el hilo limpiamente (usado al conseguir la victoria).

### `AbstractLevelGenerator.java`
Clase base para definir el nivel inicial. Al construirse llama a `createWorld()` que a su vez llama en orden a `createStatics()`, `createDecorators()`, `createPlayers()` y `createDynamics()`. Las subclases implementan cada uno de esos métodos para poblar el mundo.

---

## 9. Paquete `engine/model`

El corazón de la simulación. Gestiona toda la física y el ciclo de vida de los cuerpos. Corre en múltiples hilos.

### 9.1 `impl/Model.java`
La clase central del modelo. Responsabilidades:
- **Registra cuerpos** (`addDynamic`, `addPlayer`, `addStatic`, `addDecorator`)
- **Ejecuta las reglas** del juego por cada evento que ocurre
- **Detecta colisiones** usando el `SpatialGrid`
- **Gestiona el ciclo de vida** de cada cuerpo (STARTING → ALIVE → DEAD)
- **Pausa/reanuda** la física (`pause()` / `resume()`) congelando todos los runners y reseteando los timestamps de física para evitar saltos de dt al reanudar

### 9.2 `impl/BodyBatchManager.java`
Gestiona cómo se asignan los cuerpos a hilos. En lugar de un hilo por cuerpo (que con 100 enemigos sería 100 hilos), agrupa los cuerpos en **lotes** (_batches_):
- El **jugador** siempre va solo en su propio hilo (batch size = 1) para máxima prioridad
- El **resto de cuerpos** se agrupan de 20 en 20 por hilo (batch size = 20)

Esto reduce el número de hilos de O(N) a O(N/20), mejorando el rendimiento.

### 9.3 `impl/MultiBodyRunner.java`
Un `Runnable` que ejecuta varios cuerpos secuencialmente en un solo hilo. Cada 12ms llama a `body.onTick()` para cada cuerpo vivo de su lista. Tiene un flag `paused` que detiene la ejecución sin matar el hilo, y `shouldStop` para terminarlo limpiamente.

### 9.4 `bodies/core/AbstractBody.java`
La clase base de toda entidad del juego. Cada cuerpo tiene:
- Un **ID único** (UUID)
- Un **tipo** (`BodyType`: PLAYER, DYNAMIC, PROJECTILE, GRAVITY, DECORATOR)
- Un **estado** (`BodyState`: STARTING, ALIVE, DEAD, HANDS_OFF)
- Un `PhysicsEngine` propio que calcula su movimiento
- Un `SpatialGrid` compartido donde se registra para las detecciones de colisión
- **Scratch buffers** preasignados para evitar crear objetos en cada tick (reduce la presión del Garbage Collector)
- Un mapa de **emisores** (`BasicEmitter`) para armas y trails

### 9.5 `bodies/impl/`

| Clase | Descripción |
|---|---|
| `PlayerBody` | El jugador. Añade lógica de armas (selección, disparo, burst, misiles), boost, freno gradual con fricción, velocidad máxima de giro con aceleración, y sistema de HP (3 puntos de vida). |
| `DynamicBody` | Cuerpo dinámico genérico (naves enemigas, proyectiles). Solo aplica física pura cada tick. |
| `StaticBody` | Cuerpo estático que no se mueve (planetas, lunas). No tiene hilo propio — el Renderer los dibuja directamente desde snapshots. |

### 9.6 `physics/`

| Clase | Descripción |
|---|---|
| `BasicPhysicsEngine` | Calcula la nueva posición y velocidad de un cuerpo aplicando las ecuaciones MRUA (Movimiento Rectilíneo Uniformemente Acelerado) con delta time real. Si el dt es mayor de 50ms (pausa larga, lag) descarta el tick para evitar saltos. |
| `PhysicsValuesDTO` | El "estado físico" de un cuerpo: posX, posY, speedX, speedY, accX, accY, angle, angularSpeed, size, timestamp. |

### 9.7 `emitter/impl/BasicEmitter.java`
Gestiona el disparo de proyectiles y los trails de la nave. Controla:
- La **cadencia de disparo** (fire rate)
- El **modo burst** (ráfaga de N proyectiles)
- La **munición** (limitada o infinita)
- El **cooldown** entre disparos

Cuando el jugador pulsa disparo, el `PlayerBody` llama a `emitter.registerRequest()`. En el siguiente tick de física, si se cumplen las condiciones, el emitter genera un `EmitEvent` que el Model convierte en un nuevo proyectil en el mundo.

---

## 10. Paquete `engine/utils`

### `spatial/SpatialGrid.java`
Optimización crítica para la detección de colisiones. Divide el mundo en una **rejilla de celdas**. Cada cuerpo se registra en las celdas que ocupa según su posición y tamaño. Para detectar colisiones, en lugar de comparar cada cuerpo contra todos los demás (O(N²)), solo se comparan los cuerpos que están en la **misma celda** (O(N·k) donde k es el número promedio de cuerpos por celda, típicamente pequeño).

### `images/ImageCache.java`
Caché de imágenes ya rotadas y escaladas. La primera vez que el renderer necesita dibujar una nave a un ángulo determinado, la rota y la guarda en el caché. Las veces siguientes simplemente la recupera. Sin este caché, rotar imágenes en cada frame haría el juego muy lento. Si una imagen no está en el catálogo, dibuja un **círculo rojo** como fallback visual.

### `threading/ThreadPoolManager.java`
Pool de hilos que gestiona el `BodyBatchManager`. Reutiliza hilos en lugar de crear uno nuevo por cada cuerpo, lo que es mucho más eficiente.

### `pooling/`
Sistema de **object pooling** para los `DynamicRenderDTO`. En lugar de crear y destruir objetos en cada frame (lo que presiona al Garbage Collector), los DTOs se reciclan: cuando se usan, se extraen del pool; cuando ya no se necesitan, se devuelven al pool.

### `profiling/`
Herramientas de monitorización del rendimiento: mide tiempos de física, de render, FPS, etc. Los datos se muestran en el `SystemHUD` en pantalla.

---

## 11. Paquete `engine/view`

### 11.1 `core/View.java`
La ventana del juego (`JFrame`). Responsabilidades:
- Contiene el `Renderer` (el canvas donde se pinta todo)
- Captura el teclado (`KeyListener`) y traduce las teclas en llamadas al Controller:
  - Flechas / WASD → thrust, giro, freno
  - SPACE → disparar
  - SHIFT → boost
  - TAB → cambiar arma
  - ESC → pausa
- Enruta el input al HUD correcto según cuál esté activo (Intro, Pausa, GameOver, Victoria)
- Gestiona el estado de `pressedKeys` (teclas actualmente pulsadas) y en cada frame envía el estado al Controller

### 11.2 `core/Renderer.java`
El motor de renderizado. Corre en su propio hilo (`Renderer` thread). Usa **triple buffering** (BufferStrategy con 3 buffers) para que el dibujo y la pantalla nunca interfieran.

El bucle principal:
1. Si hay HUD activo (Intro, GameOver, Victoria, Pausa) → solo dibuja ese HUD, no actualiza física
2. Si el motor está `ALIVE` → obtiene snapshot de todos los cuerpos dinámicos, actualiza los renderables, mueve la cámara, dibuja todo
3. Espera ~16ms para intentar 60 FPS

La **cámara** sigue al jugador centrándose en él. Cuando el jugador muere, se congela (`cameraFrozen = true`) para que el GameOver aparezca donde murió.

El mecanismo `protectBossRenderable(oldId, newId)` evita el parpadeo del boss al recibir daño: cuando el boss "muere" y re-spawna con nuevo ID, el renderable viejo se mantiene visible durante 80ms mientras el nuevo se inicializa.

### 11.3 `renderables/impl/`

| Clase | Descripción |
|---|---|
| `Renderable` | Clase base. Tiene un `assetId` y una imagen cacheada. En `paint()` dibuja la imagen centrada en la posición del cuerpo. |
| `DynamicRenderable` | Para cuerpos que se mueven. Actualiza posición y ángulo cada frame desde el DTO del snapshot. |
| `ExplosionRenderable` | Reproduce un spritesheet de explosión (4×4 frames) frame a frame. Cuando termina la animación, el Renderer lo elimina. |

### 11.4 `hud/impl/` — Los HUDs

Los HUDs son overlays que se dibujan encima del juego. Todos usan `Graphics2D` directamente con fuentes monoespaciadas y colores de paleta espacial.

| HUD | Qué muestra | Cuándo |
|---|---|---|
| `IntroHUD` | Menú de inicio: título, controles, selector de dificultad | Al arrancar el juego |
| `PauseHUD` | Menú de pausa con opciones Reanudar/Reiniciar/Salir | Al pulsar ESC |
| `GameOverHUD` | Puntuación, oleada alcanzada, botones Reintentar/Salir, fade-in | Al morir el jugador |
| `VictoryHUD` | "VICTORIA", puntuación final, botones, fade-in dorado | Al matar al boss final |
| `PlayerHUD` | HP del jugador (corazones), barra de boost, arma activa | Siempre durante el juego |
| `WaveHUD` | Oleada actual, enemigos restantes, anuncios de oleada | Siempre durante el juego |
| `BossHUD` | Barra de vida del boss/miniboss | Cuando hay boss vivo |
| `ScoreHUD` | Puntuación actual y récord | Siempre durante el juego |
| `SystemHUD` | FPS, tiempo de draw, entidades vivas | Siempre (esquina) |
| `SpatialGridHUD` | Estadísticas del grid de colisiones | Debug |

---

## 12. Paquete `engine/world`

Define cómo se construye el mundo al inicio de la partida.

### `ports/WorldDefinition.java`
El "plano" del mundo. Un objeto inmutable que contiene listas de:
- `gravityBodies` — planetas, lunas, estrellas (cuerpos estáticos)
- `spaceDecorators` — objetos decorativos sin colisión
- `spaceships` — la nave del jugador
- `weapons` — las armas equipadas
- `trailEmitters` — los emisores de propulsión de la nave
- `gameAssets` — el catálogo de imágenes a cargar
- `background` — imagen de fondo

### `core/AbstractWorldDefinitionProvider.java`
Clase base para construir un `WorldDefinition`. Tiene métodos auxiliares como `addGravityBody()`, `addSpaceship()`, `addWeaponPresetBulletRandomAsset()`, `registerAsset()`. Las subclases solo implementan `define()` para describir su mundo concreto.

### `core/WeaponDefFactory.java`
Factoría de armas. Proporciona métodos estáticos para crear definiciones de armas preconfiguradas:
- `createPresetedBulletBasic()` — bala rápida, 4 disparos/segundo, daño 1
- `createPresetedMissileLauncher()` — misil lento pero propulsado, 1 disparo/segundo, daño 2

---

## 13. Paquetes del juego concreto

### `gameworld/ProjectAssets.java`
Registra **todos** los assets disponibles en el catálogo usando IDs lógicos. Por ejemplo:
```java
catalog.register("spaceship_01", "spaceship-01.png", AssetType.SPACESHIP, HIGH);
catalog.register("spaceship_06", "spaceship-06.png", AssetType.SPACESHIP, HIGH); // boss final
```
Si un asset se usa en el juego pero no está registrado aquí, el `ImageCache` dibuja un círculo rojo.

### `gameworld/RandomWorldDefinitionProvider.java`
Define el mundo concreto del juego. Hereda de `AbstractWorldDefinitionProvider` e implementa `define()`:
- Fondo estático `back_19`
- 5 cuerpos de gravedad: un sol, un planeta grande, una luna y 2+2 aleatorios
- Registra los assets usados por la IA: `spaceship_03` (enemigo), `spaceship_07` (miniboss), `spaceship_06` (boss), `explosion_sheet`
- Spawna al jugador en el centro del mundo (2250, 2250)
- Equipa bala básica y lanzamisiles

### `gamelevel/LevelBasic.java`
Implementación concreta del nivel. Al construirse, llama a los métodos heredados de `AbstractLevelGenerator` para poblar el mundo con lo definido en el `WorldDefinition`:
- `createStatics()` — crea los planetas y lunas
- `createDecorators()` — crea los decoradores estéticos
- `createPlayers()` — crea la nave del jugador y le equipa armas y trails
- `createDynamics()` — vacío (los enemigos los crea la IA, no el nivel)

### `gamerules/AsteroidSurvivor.java`
Las **reglas de colisión** del juego. Implementa `ActionsGenerator`. Define qué ocurre cuando dos cuerpos colisionan:
- Proyectil + límite → proyectil muere
- Resto + límite → rebota
- Cualquier cosa + obstáculo estático → muere (excepto los bosses, que son inmunes)
- Jugador + enemigo/proyectil → jugador pierde 1 HP, el enemigo muere
- Proyectil + enemigo → ambos mueren, se acumula el daño en el boss si aplica

Mantiene un `Set<String> bossIds` con los IDs de los bosses registrados como inmunes a obstáculos. El `AIBasicSpawner` registra y desregistra estos IDs al spawnear/matar al boss.

### `gameai/AIBasicSpawner.java`
El **cerebro de la IA**. Hereda de `AbstractIAGenerator` y corre en su propio hilo. Gestiona:

**Sistema de oleadas:**
```
Oleada normal → spawna N naves desde los bordes, una cada 900ms
Oleada 5      → spawna el miniboss (spaceship_07, 15 HP)
Oleada 10     → spawna el boss final (spaceship_06, 30 HP)
N aumenta según dificultad: fácil +1/oleada, normal +2/oleada, difícil +3/oleada
```

**Comportamiento de las naves enemigas (cada 16ms):**
- Si van más rápido de su velocidad máxima → aplicar freno
- Si no → aplicar thrust en dirección al jugador (con `steerBodyToward`)
- También usan freno ocasionalmente, lo que hace el gameplay más dinámico

**Sistema de HP del boss:**
Como el motor no tiene HP parcial (un cuerpo o está vivo o muerto), el boss se simula así:
1. Boss recibe impacto → el motor lo "mata"
2. `pruneDeadEnemies()` detecta que el boss murió → consume el daño del `pendingBossDamage`
3. Si le quedan HP → re-spawna el boss en la misma posición, llama `protectBossRenderable` para evitar parpadeo
4. Si HP = 0 → explosión grande, suma puntuación, llama `showVictory()`, detiene el spawner

---

## 14. Flujo completo de un tick de juego

Para entender cómo interactúan todas las piezas, aquí el flujo de un solo frame:

```
1. View.syncInputState()
   └─ Lee teclas pulsadas → llama a controller.playerThrustOn(), playerFireWeapon(), etc.

2. Model (hilos de física, cada 12ms)
   └─ Por cada cuerpo vivo:
       a. PhysicsEngine.calcNewPhysicsValues() → nueva posición/velocidad (MRUA + dt real)
       b. SpatialGrid.update() → actualiza en qué celdas está el cuerpo
       c. SpatialGrid.queryCandidates() → lista de cuerpos cercanos
       d. Para cada par cercano: detectar solapamiento → crear CollisionEvent
       e. Comprobar límites del mundo → crear LimitEvent si aplica
       f. Comprobar emisores → crear EmitEvent si hay disparo pendiente
       g. Controller.provideActions(events) → AsteroidSurvivor devuelve ActionDTOs
       h. Ejecutar acciones: DIE, SPAWN_PROJECTILE, MOVE_REBOUND, PLAYER_HIT...

3. AIBasicSpawner (hilo propio, cada ~4ms)
   └─ sendEnemyPositions() → actualiza flechas de dirección en el renderer
   └─ pruneDeadEnemies()   → detecta muertes, gestiona HP del boss, suma puntos
   └─ steerEnemies()       → aplica thrust/freno a cada nave enemiga

4. Renderer (hilo propio, cada ~16ms)
   └─ Controller.snapshotRenderData() → snapshot thread-safe del estado del modelo
   └─ updateDynamicRenderables() → actualiza posición/ángulo de cada renderable
   └─ updateCamera() → centra la cámara en el jugador
   └─ drawScene() → dibuja fondo, cuerpos estáticos, cuerpos dinámicos, HUDs
```

---

## 15. Conceptos clave para explicar al profesor

### ¿Por qué MVC?
Permite cambiar las reglas del juego (`gamerules/`) sin tocar el motor de física, o cambiar el renderizado sin tocar la simulación. El motor es **reutilizable** — en teoría se podría hacer otro juego distinto solo cambiando los paquetes `game*`.

### ¿Por qué múltiples hilos?
La física de cada cuerpo es independiente. Correr 50 cuerpos en un solo hilo secuencialmente a 60Hz sería lento. Con el `BodyBatchManager`, los cuerpos se distribuyen en lotes y cada lote corre en paralelo. El `SpatialGrid` hace que la detección de colisiones no sea O(N²).

### ¿Por qué DTOs?
Si el Renderer dibujara directamente los objetos del Model, un hilo de física podría estar modificando una posición mientras el renderer la está leyendo → datos corruptos o crashes. Los DTOs son **snapshots inmutables**: el renderer trabaja con una copia del estado en un momento dado.

### ¿Qué es el delta time (dt)?
En lugar de mover los cuerpos siempre la misma cantidad de píxeles por tick, se multiplica la velocidad por el **tiempo real transcurrido** desde el último tick. Así el juego va igual de rápido independientemente de los FPS. Si el dt es demasiado grande (pausa, lag), se descarta ese tick.

### ¿Cómo funciona el SpatialGrid?
Imagina el mundo dividido en una cuadrícula. Cada celda sabe qué cuerpos están en ella. Para detectar colisiones, solo se comparan cuerpos de la misma celda. Con 200 cuerpos en un mundo grande, la mayoría de las celdas están vacías — solo se hacen unas pocas comparaciones reales.

### ¿Cómo se gestiona el HP del boss?
El motor solo sabe de cuerpos vivos o muertos — no tiene HP. El HP del boss se simula en la capa de juego (`AIBasicSpawner`): cuando el motor mata al boss (por impacto de proyectil), el spawner lo detecta, resta HP, y si le quedan puntos de vida, lo re-spawna inmediatamente en la misma posición con un nuevo ID.

