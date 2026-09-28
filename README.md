# Zian Utilities

Zian Utilities es un mod para **Minecraft 1.21.1**, **NeoForge 21.1.x** y **Cobblemon 1.8.1**. Se instala con el mismo JAR en el servidor y en cada cliente. La integración económica se ha probado con **AVECOINS 2.3 y 2.4** en Youer 1.21.1.

## Funciones

- **Generaciones:** el administrador habilita las generaciones acumulativamente; el control filtra apariciones naturales, pesca y Poké Snack sin retirar Pokémon ya existentes.
- **Misiones:** dos objetivos compartidos por todo el servidor cada tres horas, con aceptación y progreso individual; misiones semanales de 25 capturas y 50 victorias salvajes; campañas persistentes para capturar especies distintas de cada generación. Las recompensas usan AVECOINS.
- **Gachas:** el administrador crea y publica grupos de premios con probabilidades visibles y costo en tickets de AVECOINS. Las tiradas se validan en el servidor; los premios que no caben en el inventario quedan pendientes para reclamar.
- **Equipo:** tres familias inspiradas en Pikachu, Dragonite y Lucario. Cada una incluye armadura completa, espada, hacha, pico, pala, azada, arco y escudo. No tienen recetas de fabricación.
- **Encantamiento:** `zianutilities:mineria_3x3` para picos, disponible solo por comando. No aparece en mesa de encantamientos, comercio de aldeanos ni botín.

## Instalación y configuración

1. Instala Java 21, Minecraft 1.21.1, una versión de NeoForge 21.1.x, Cobblemon 1.8.1 y las dependencias que requiera Cobblemon.
2. Instala el mismo JAR de Zian Utilities en `mods` del servidor y de los clientes; retira versiones anteriores del mod.
3. Instala AVECOINS 2.3 o 2.4 si usarás los pagos de misiones y las tiradas de gacha. La compatibilidad se comprueba en cada operación económica; versiones futuras no se aceptan automáticamente.
4. Al iniciar se crea `config/zianutilities-features.properties`. Sus cuatro interruptores (`quests.enabled`, `quests.rewards.enabled`, `gachas.enabled`, `gachas.payments.enabled`) vienen activados. Cámbialos con el servidor detenido y reinicia para aplicarlos.

No hacen falta parámetros especiales en el startup. La interfaz principal se abre con `/zian`. Los administradores pueden consultar las generaciones activas con `/zian generation active` y habilitar otra con `/zian generation enable gen1` (sustituye `gen1` según corresponda).

## Estado de la beta

La lógica de la primera beta se probó como alpha.29 en un servidor **Youer 1.21.1** con dos cuentas: persistencia tras reinicio, pagos de misiones, campaña, tiradas de gacha y rechazo por tickets insuficientes. El equipamiento y sus encantamientos se probaron por separado. Las texturas actuales son provisionales y se podrán revisar sin cambiar los identificadores de los objetos existentes. Consulta [la evidencia y sus límites](docs/BETA1_VALIDATION.md).

Consulta [el registro de cambios](CHANGELOG.md), [las funciones normales y sus interruptores](docs/PRODUCTION_FEATURES_ALPHA24.md) y [los criterios de publicación](docs/RELEASE_CHECKLIST.md). Antes de usar el mod en un mundo importante, conserva una copia de seguridad del mundo.

## Desarrollo

El proyecto tiene un módulo `core` independiente de Minecraft y un módulo `neoforge` para la integración de juego. Las pruebas se ejecutan con `./gradlew clean build` en Java 21 y también se compilan en GitHub Actions.
