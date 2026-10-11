# Zian Breeding — implementación candidata beta.11

Minecraft 1.21.1 · NeoForge 21.1.x · Cobblemon 1.8.1 · Java 21.
La rama conserva todas las funciones de beta.9. La crianza viene desactivada
(`enabled=false`) hasta completar la validación del operador en Youer.

## Uso

`/ZianUtilities crianza` o el botón «Abrir crianza Pokémon» del panel principal.
La pantalla usa las tarjetas oscuras, acentos dorados y botones de Misiones/Gachas.
Muestra los seis Pokémon del equipo con sus iconos nativos de Cobblemon, nivel y
sexo. Estadísticas, IV, naturaleza, habilidad y grupos huevo se consultan al pasar
el mouse. Las tarjetas explican los impedimentos (grupo, sexo, Ditto, padres
reservados y generación) sin sortear una cría. Selecciona dos
padres del equipo y confirma el inicio gratuito. El resultado no se revela antes
de entregarse. El contador se actualiza cada segundo.

Base: 24 horas. Mínimo: 2 horas desde el inicio original, incluso al comprar
aceleraciones. El tiempo transcurre también desconectado o con el servidor
apagado, según las fechas persistidas; un reloj atrasado no retrocede por debajo
del último punto de control. Un salto del reloj hacia delante sí puede completar
la crianza: depende del reloj del servidor, no se promete un reloj monotónico
entre apagados.

Los padres permanecen en el equipo/PC, sin consumir sus objetos. Sus UUID quedan
reservados globalmente para otra crianza hasta completar o cancelar la actual.
Se comprueba de nuevo la pertenencia al iniciar y entregar. No se impide que
otros mods intercambien un padre: si deja de pertenecer al dueño, se retiene la
entrega hasta recuperar los padres o cancelar.

Una ranura normal y dos VIP por defecto. Una entrega pendiente, PC lleno o una
operación incierta siguen ocupando ranura. Perder VIP no cancela operaciones;
solo impide nuevos inicios por encima del límite actual.

## Genética

Adaptación de Cobbreeding 2.4.0, MIT de Ludichat/Fuzuki; licencia completa incluida
en el JAR y procedencia en `THIRD_PARTY_LICENSES.md`. No incluye sus assets ni
requiere instalar Cobbreeding, pastos, incubadoras o huevos.

Mantiene grupos huevo, sexos, Ditto, no descubiertos/neutered, crías y excepciones
de formas, IV, objetos recios/Lazo Destino, Piedra Eterna, naturaleza, habilidad,
bola, movimientos huevo, shiny y características heredables. Ditto × Ditto
aleatorio y habilidades forzadas están desactivados. Mirror Herb no modifica
los padres. La probabilidad alpha base es configurable (5% inicial), con una
comparación exacta de probabilidad; no se hereda alpha obligatoriamente.
La compatibilidad con Size Variation es opcional y aún necesita prueba con ese
mod instalado. Datapacks que eliminen especies/formas pueden bloquear la
operación de forma explícita.

El hijo se sortea al inicio, antes de cualquier entrega, y se guarda con UUID,
propiedades y atributos concretos. No vuelve a sortearse. Se filtran las posibles
crías según Generation Control y se revisa otra vez la generación al entregar.
Si se deshabilita su generación, queda bloqueado sin perder el hijo.

## Persistencia y recuperación

Registro encadenado SHA-256, exclusivo para un escritor, en:
`<mundo>/data/zianutilities/breeding_v1/journal.wal`.
Cada registro fuerza el archivo a disco antes de la mutación externa. En Linux
se fuerza también el directorio del archivo. No usa SavedData como registro
crítico, no trunca registros dañados y no intenta reconstruir resultados.
La garantía depende de que el sistema de archivos/hardware respete fsync;
no constituye una transacción atómica entre AVECOINS, PC y el registro.

Estados: ACTIVE, ACCELERATING, DELIVERING, DELIVERED, BLOCKED, UNCERTAIN,
CANCELLED. El estado listo se deriva del tiempo, sin duplicar plazos.

- Antes del cobro se registra moneda, cantidad, tarifa, reducción y nonce.
- AVECOINS no ofrece claves idempotentes: un cobro interrumpido pasa a revisión;
  nunca se vuelve a debitar automáticamente.
- Antes de insertar el hijo se registra DELIVERING. Se solicita guardar el PC
  y se lee de nuevo el almacenamiento persistido, no la caché en memoria.
- Se verifica UUID, especie/forma, IV, naturaleza, habilidad, sexo, bola,
  shiny, alpha y tamaño antes de marcar DELIVERED.
- Un propietario desconectado espera hasta conectarse. PC lleno queda BLOCKED
  y se reclama desde la interfaz o `claim` al liberar espacio.
- Tras un reinicio, DELIVERING solo se cierra si el almacenamiento prueba la
  entrega. Ausencia o incongruencia implican UNCERTAIN; no se insertará otro hijo.
- Cancelar libera padres/ranura, sin reembolsar aceleraciones; no se permite
  cancelar una mutación incierta. No hay devolución automática de dinero.

## Comandos y permisos

- `/ZianUtilities crianza status`
- `/ZianUtilities crianza start <ranura 1–6> <ranura 1–6>`
- `/ZianUtilities crianza claim <id>` y `cancel <id>` (autocompletado propio).
- `/ZianUtilities crianza accelerate <id> <moneda> <unidades>`: orden explícita
  de compra; la GUI añade una confirmación con coste y reducción visibles.
- `/ZianUtilities breeding-admin reload`
- `/ZianUtilities breeding-admin inspect <id>`
- `/ZianUtilities breeding-admin complete <id>`: exclusiva de administración,
  adelanta la crianza para pruebas sin modificar su fecha original, genética ni
  pagos. Registra al operador y persiste un plazo especial; no admite cobros o
  entregas inciertos. PC lleno, generación bloqueada o padres ausentes siguen
  reteniendo la entrega. Si el dueño está desconectado, espera su conexión.
- Todos los argumentos ID tienen autocompletado: propios para acciones de jugador
  y estados correspondientes para reconciliación administrativa.
- `/ZianUtilities breeding-admin resolve-debit <id> <charged true/false> <evidencia>`
- `/ZianUtilities breeding-admin resolve-delivery <id> <delivered true/false> <evidencia>`

`resolve-*` es una reconciliación administrativa, no un cobro/reembolso. La
identidad del operador y evidencia quedan registradas. Para reabrir una entrega
se exige dueño conectado y ausencia del UUID en PC/equipo vivo y guardado; aun
así el operador debe revisar intercambios externos y copias antes de autorizar.

Permisos LuckPerms: `zianutilities.breeding.use`, `.slots.vip`, `.slots.1` …
`.slots.8`, `.admin`. Sin proveedor, uso público y administración solo OP.
Un deny explícito se respeta; fallo del proveedor deniega acceso. Se toma la
mayor capacidad concedida y se limita al máximo configurado.

La candidata requiere el mismo JAR en cliente y servidor: el panel usa `zian-hub-3` y la crianza `breeding-ui-1`. La beta.10 ya está
instalada y habilitada en Rassvet; la beta.11 requiere actualizar ambos lados.
