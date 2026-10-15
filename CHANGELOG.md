# Changelog

## 0.1.1

- Accept the inspected AVECOINS 2.5 wallet contract.
- Keep method and capacity validation; later version numbers require all reviewed class fingerprints to match.
- Preserve balances, currencies, durable receipts and interrupted-operation recovery. No automatic data migration.
- Refresh installation requirements and current documentation for Cobblemon 1.8.2.

## Español

- Compatibilidad revisada con AVECOINS 2.5, conservando los controles de contrato y recuperación.
- Las versiones posteriores solo se admiten si mantienen las clases económicas verificadas sin cambios.
- Conserva saldos y datos. Instala la misma versión en cliente y servidor.


## 0.1.0-beta.16 — candidata de almacenamiento

- Índices por jugador, padre y compra; solo las crianzas pendientes se procesan en cada tick.
- Las crianzas cerradas se leen desde posiciones verificadas del WAL, reduciendo objetos completos en memoria.
- Compactación offline opcional con archivo gzip del original y verificación de hijos, pagos y reloj. No hay migración automática.
- Interfaz responsive de crianza, texto centrado bajo la cría y textos UTF-8 corregidos. Protocolo breeding-ui-2 conservado.
- 157 pruebas aprobadas y dos arranques dedicados aislados con recuperación tras reinicio. Escalas GUI 2/3/4 pendientes de prueba visual.

## 0.1.0-beta.12 — compatibilidad de crianza

- Resuelve Lazo Destino y Bola Luminosa por su ID de registro; sus tipos concretos cambiaron en Cobblemon 1.8.2 y rompían los JAR anteriores.
- Conserva la interfaz beta.11, la finalización administrativa y todos los datos existentes.
- Prueba la crianza funcionalmente tanto con Cobblemon 1.8.1 como con 1.8.2.
- La actualización del servidor completo a 1.8.2 sigue limitada por la referencia incompatible de RCT API a Bola Férrea.

## 0.1.0-beta.11 — interfaz de crianza

- Sustituye el inventario básico por una pantalla con el estilo de Misiones/Gachas: tarjetas oscuras, bordes dorados y botones uniformes.
- Muestra los iconos reales de los Pokémon, nivel, sexo, estadísticas, IV, naturaleza, habilidad y grupos huevo; explica incompatibilidades dentro del panel sin revelar la cría.
- Añade sesiones y confirmaciones controladas por el servidor, lista de crianzas, temporizador y compra de aceleraciones con saldo visible.
- Añade `/ZianUtilities breeding-admin complete <id>` para pruebas instantáneas, exclusivo de administración, persistente y sin saltarse controles del PC, padres ni cobros inciertos.
- Añade autocompletado a todos los IDs de crianza, limitado por propietario, permisos y tipo de operación.
- Conserva los registros beta.10; los plazos administrativos se guardan aparte de la fecha original. Requiere beta.11 en cliente y servidor.

## 0.1.0-beta.10 — candidata de crianza

- Añade crianza desde el equipo, gratuita, con base de 24 h y mínimo de 2 h configurable desde ese piso.
- Añade aceleraciones opcionales con denominaciones reales de AVECOINS, confirmación, tarifa y cantidad persistidas.
- Integra genética adaptada de Cobbreeding 2.4.0 MIT; no requiere su JAR ni registra huevos/incubadoras.
- Reserva padres e hijo por UUID, respeta generaciones y confirma el PC guardado antes de completar.
- Incluye registro durable, bloqueo de cobros/entregas inciertos, reconciliación administrativa y permisos LuckPerms.
- Añade botón al panel, conservando misiones, gachas, equipo y encantamientos de beta.9. Requiere el mismo JAR en ambos lados.
- Desactivada por defecto hasta las pruebas de cliente/Youer; no desplegada en producción.

## 0.1.0-beta.9

- Rename the command root to /ZianUtilities, including every existing subcommand. The old /zian root is removed.
- Preserve command permissions, operator checks, feature switches, configuration, and saved progress.
- Update current help and recovery documentation. Existing scripts or GUI buttons using the old root must be updated.

## 0.1.0-beta.8

- Registra los eventos visuales de los arcos directamente en el cliente, eliminando las dos advertencias de `EventBusSubscriber.Bus` sin depender de la selección automática del bus de versiones posteriores.
- Muestra la información estándar del objeto al pasar el mouse por el icono o nombre de los premios del gacha, tanto en el panel de jugador como en el editor.
- Incluye encantamientos, atributos, nombre y descripción del objeto configurado mediante los tooltips nativos de Minecraft y NeoForge; también permite inspeccionar los premios pendientes y el icono del ticket.
- Conserva los componentes completos que ya enviaba el servidor. No cambia probabilidades, cobros, entrega, animación ni el protocolo del gacha.

## 0.1.0-beta.7

- Añade nueve piezas Dark Reaper con las mismas estadísticas mejoradas de netherita que el set Prismático, conservando el equipo existente.
- Añade cuatro arcos encantables (Curvo, Clásico, Largo y Arpa), con 2032 de durabilidad, +1 daño base de flecha y tres estados visuales de tensado.
- Integra las texturas de MonGen's Cave y Chiaki Haruma con autorización escrita confirmada por el propietario del proyecto; no requiere ItemsAdder, Oraxen ni paquetes externos.
- Las piezas nuevas aparecen en la pestaña de equipamiento, sin recetas. El pico Dark Reaper admite Minería 3×3 exclusivamente mediante comando.
- Requiere beta.7 en servidor y clientes. Apariencia y uso en Youer pendientes de prueba del operador.

## 0.1.0-beta.6

- Entrega el premio del gacha un segundo después de finalizar su vista animada: espera de 6,3 segundos gestionada por el servidor, con premio persistido desde el cobro.
- Impide reclamar durante la espera. Al desconectarse o reiniciar, el premio reservado queda disponible para reclamación; si el inventario está lleno no se pierde.

- Separa cobros inciertos y entregas inciertas del gacha, conservando la revisión de los registros antiguos.
- Añade resolución administrativa del cobro y confirmación de compensaciones ya entregadas; no vuelve a cobrar ni entrega automáticamente un premio incierto.
- Permite reintentar recompensas rechazadas exclusivamente por billetera llena, conservando el identificador de operación y los componentes ya pagados.
- Registra y muestra las misiones completadas sin pago cuando las recompensas están desactivadas. Conserva el avance de campaña sin generar pagos retroactivos automáticos.
- Limita a 8 MiB la lectura NBT de los archivos del gacha y corrige el diagnóstico de misiones y pagos.
- Amplía las pruebas de recuperación, reintentos, estados sin pago y lectura NBT.
- Requiere actualizar tanto el servidor como los clientes por el cambio del protocolo de misiones.

## 0.1.0-beta.5

- La animación del gacha ocupa una vista modal para evitar que los textos del panel se superpongan en escalado de interfaz ×2.
- Retira las cinco espadas elementales y sus recursos; el equipo creativo queda reducido al set Prismático.
- Añade `/zian gacha review <jugador>` para inspeccionar tiradas bloqueadas y `/zian gacha confirm-delivered <jugador> <operationId>` para cerrar una entrega interrumpida solo tras verificar el premio.
- Las tiradas interrumpidas antes del cobro se descartan de forma segura y los bloqueos de revisión se informan sin una traza de error extensa.

## 0.1.0-beta.4

- Sustituye las tres familias de equipo anteriores por un solo set Prismático de armadura, espada y herramientas, con las estadísticas de netherita mejoradas y sin recetas.
- Añade cinco espadas funcionales con texturas animadas: agua, fuego, espacio, viento y tierra. Las piezas aparecen en una pestaña creativa propia.
- Retira los identificadores, modelos y texturas del equipo anterior. Los objetos antiguos guardados requieren atención antes de actualizar un mundo existente.
- La animación de gacha muestra «Girando...» durante la tirada y deja de revelar cómo se decide el premio.
- Compilación y pruebas automatizadas completadas; apariencia y uso en Youer pendientes de prueba del operador.

## 0.1.0-beta.1

- Primera candidata beta. Reúne control de generaciones, misiones globales de tres horas, semanales y campañas, gachas y equipamiento temático.
- Verificado por el operador en Youer 1.21.1: el progreso persiste al reiniciar; se pagan misiones globales y una etapa de campaña en dos cuentas; una tirada descuenta un ticket y entrega un premio; sin tickets la tirada se rechaza.
- El reinicio semanal del lunes presenta objetivos nuevos con progreso 0/25 y 0/50; el operador confirmó por separado los encantamientos y el equipamiento.
- No cambia la lógica de juego de alpha.29. Se actualizan la versión y la documentación para la prueba final de publicación.

## 0.1.0-alpha.29

- Corrige los encantamientos de los sets de armadura añadiendo las etiquetas de equipo de Minecraft 1.21.1.
- Rediseña los sets existentes con paletas y motivos de Pikachu, Dragonite y Lucario; conserva sus identificadores para no perder piezas guardadas.
- Mejora cada armadura con +1 defensa por pieza, +1 dureza y +0,05 resistencia al retroceso por pieza.
- Añade a cada familia espada, hacha, pico, pala, azada, arco y escudo sin recetas; los picos y demás herramientas tienen estadísticas de netherita mejoradas.
- El arco suma 1 punto de daño base a cada flecha y el escudo aporta defensa, dureza y resistencia al retroceso en la mano secundaria.
- Minería 3×3 queda exclusiva del comando `/enchant`; no aparece en mesa, aldeanos ni botín.

## 0.1.0-alpha.28

- Añade tres sets de armadura de cuatro piezas (Captura, Explorador y Campeón), con estadísticas y durabilidad de netherita, texturas integradas y sin recetas.
- Añade el encantamiento `mineria_3x3` para picos. Rompe hasta ocho bloques adicionales en el plano golpeado, usando la rotura normal del jugador para aplicar protecciones, botín y desgaste.
- Evita la minería de área cuando la rotura inicial se cancela, en creativo/espectador, sobre bloques con entidad o de mayor dureza.

## 0.1.0-alpha.27

- Las campañas ahora piden capturar la mitad de las especies de aparición natural de cada generación. La meta se fija al aceptar y las tres etapas comparten un registro acumulado de especies distintas.
- Añade una vista paginada de especies capturadas por generación. El progreso anterior de alpha.26 se conserva como crédito; las especies de etapas ya cerradas no pueden reconstruirse y aparecen como capturas previas sin nombre.
- Mantiene los pagos únicos de cada etapa y evita repetir el premio final al migrar una campaña ya completada.

## 0.1.0-alpha.26

- Añade misiones semanales de 25 capturas elegibles y 50 victorias contra Pokémon salvajes, con progreso individual, aceptación, reinicio semanal y pago único por objetivo.
- Añade campañas persistentes por generación: tres etapas consecutivas de 3, 8 y 15 especies distintas de aparición natural. Se desbloquean con cada generación activa y conservan el avance al habilitar más generaciones.
- Añade pantallas para semanales y campañas dentro de Misiones y un archivo del mundo para configurar moneda e importe de cada objetivo y etapa.
- Incluye la corrección del cambio de generación de alpha.25.

## 0.1.0-alpha.25

- Corrige el cierre de la interfaz de misiones al cambiar las generaciones activas: la primera selección de una nueva lista de especies ya no intenta comparar un objetivo anterior inexistente.
- Registra el plan de misiones semanales y campañas por generación acumulativa.

## 0.1.0-alpha.24

- Misiones y Gachas, incluidos sus pagos, se activan sin parámetros especiales de Java. Al iniciar por primera vez se crea `config/zianutilities-features.properties` con cuatro interruptores editables.
- Los interruptores de pagos solo funcionan si su módulo está habilitado; una configuración incompleta o inválida desactiva las funciones en lugar de activar pagos inesperadamente.
- `/zian quest test rotate` continúa siendo una acción de prueba y no queda disponible sin su antiguo parámetro de prueba.
- Los parámetros antiguos de prueba ya no son necesarios para las funciones normales. La selección de objetivos y las recompensas de una ventana de misiones ya iniciada conservan sus reglas hasta el siguiente reinicio programado.

All notable changes to Zian Utilities will be documented here.

## Unreleased

### Added

- Initial two-module Gradle bootstrap: `core` + `neoforge`.
- Java 21 / Minecraft 1.21.1 / NeoForge 21.1.251 baseline.
- Cobblemon 1.8.1 compile-time integration dependency.
- CI build and pure-core boundary check.
- Cobblemon Species → Generation resolver with canonical Gen 1-9 labels, `gen7b`/`gen8a` product mappings, caching, unknown-label handling and manual override precedence.
- Persistent global generation state via NeoForge SavedData, schema versioning, explicit legacy migration and forward-preserved unknown generation IDs.
- `/zian generation` administration commands with autocomplete, Game Master permission fallback, idempotent persistent mutations and structured audit logging.
- Natural/player spawn guard for Cobblemon `PlayerSpawner`, backed by live persisted generation state and conservative source classification.
- Fishing spawn guard on `BOBBER_SPAWN_POKEMON_PRE`, using planned species generation and current persisted state without broad `BasicSpawner` classification.
