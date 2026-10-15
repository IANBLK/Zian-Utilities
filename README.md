# Zian Utilities

**0.1.1 · Minecraft 1.21.1 · Cobblemon 1.8.2 · NeoForge 21.1.x · Java 21**

Control de generaciones, misiones, Gachas, crianza, equipo y ajustes de monturas desde una interfaz dentro del juego. Instala el mismo JAR en cliente y servidor, con Kotlin for Forge 5.10 o una versión compatible de 5.x.

## Funciones actuales

- **Generaciones:** habilitación acumulativa y control de apariciones naturales, pesca y Poké Snack; conserva los Pokémon ya existentes. La elegibilidad de crianza es independiente de este filtro.
- **Misiones:** objetivos compartidos con aceptación y progreso individual, misiones semanales y campañas persistentes.
- **Gachas:** catálogo con nombres, iconos, descripciones y probabilidades visibles; tickets, premios y entregas pendientes se validan en el servidor.
- **Crianza:** selección de padres, compatibilidad y estimación de la cría, inicio gratuito de 24 horas, aceleración económica opcional y entrega al PC. Trabajos y entregas quedan registrados para recuperación.
- **Interfaz:** tarjetas de Misiones, Gachas y Crianza, con confirmaciones y distribución adaptadas a distintas escalas.
- **Monturas voladoras:** límites administrativos de velocidad y estamina máxima. No modifica el permiso `/fly`. Desactivar la política restaura el comportamiento normal de las monturas; no prohíbe volar.
- **Equipo:** sets Prismático y Dark Reaper con nombres y lore, armas y cuatro arcos con bonificaciones de daño diferentes y encantamientos de durabilidad. Reforged se retiró; las piezas antiguas se convierten en equivalentes vanilla conservando sus componentes. Las armas permanecen.
- **Minería 3×3:** encantamiento `zianutilities:mineria_3x3` para picos, obtenido por comando, con permisos de rotura, desgaste y drops. No se añade a la mesa de encantamientos ni al loot normal.

## Economía y compatibilidad

AVECOINS es opcional para las funciones económicas; se instala por separado y no se incluye en el JAR. AVECOINS 2.3, 2.4 y 2.5 están inspeccionados. Una versión posterior solo se admite si las tres clases económicas verificadas conservan exactamente sus huellas revisadas y pasan las comprobaciones de métodos y capacidad. Un cambio real de contrato requiere revisión; no se promete compatibilidad automática con cualquier actualización.

La versión 0.1.1 corrige el rechazo de AVECOINS 2.5 por su número de versión, sin cambiar saldos, monedas, recibos ni trabajos de crianza. Consulta [el contrato revisado](docs/AVECOINS25_COMPATIBILITY.md).

## Uso y administración

- `/ZianUtilities`: abre el panel principal.
- `/ZianUtilities crianza`: abre crianza.
- `/ZianUtilities generation active`: consulta las generaciones activas.
- `/ZianUtilities generation enable gen1`: habilita una generación.
- `/ZianUtilities vuelo ver`: muestra la política de monturas.
- `/ZianUtilities vuelo velocidad 65`: ajusta la velocidad al 65%.
- `/ZianUtilities vuelo estamina 60`: limita la estamina máxima al 60%; no representa el porcentaje de gasto.

Consulta la [guía de crianza](docs/ZIAN_BREEDING_DESIGN.md), [su configuración económica](docs/ZIAN_BREEDING_CURRENCY_CONFIG.md), [recuperación](docs/BETA6_RECOVERY.md) y [cambios](CHANGELOG.md). Los documentos con nombres alpha/beta describen etapas históricas; esta página y [las notas de 0.1.1](docs/releases/0.1.1/changelog.md) describen la versión actual.

## Instalación y datos

Descarga el JAR de [Releases](https://github.com/IANBLK/Zian-Utilities/releases/latest), retira el JAR anterior y usa la misma versión en cliente y servidor. Conserva una copia del mundo y de la configuración antes de actualizar.

Las operaciones económicas inciertas requieren revisión y no se repiten a ciegas. La migración opcional a SQLite y las tareas de mantenimiento son explícitas; actualizar el mod no implica borrar ni migrar automáticamente los datos.

El código y el arte incluido tienen condiciones separadas, indicadas en los avisos de licencia del proyecto y del JAR.

## Desarrollo

Java 21. Compila con `gradlew.bat build` en Windows o `./gradlew build` en Linux. El JAR NeoForge se genera en `neoforge/build/libs/`.
