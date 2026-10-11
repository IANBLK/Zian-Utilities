# Crianza beta.10 — validación y límites

Fecha: 2026-10-10 (America/Guayaquil). Rama `feat/zian-breeding-core`.
[PR 70](https://github.com/IANBLK/Zian-Utilities/pull/70), mantenido como draft.
La rama integra la producción beta.9 antes de añadir la crianza.

## Ejecutado

- `./gradlew build --no-daemon`: 80 pruebas core y 63 NeoForge, sin fallos.
- Pruebas de piso de tiempo, cantidades enormes, unidad parcial y no cobrar
  tiempo transcurrido; nonce repetido y reconciliación sin aplicar dos veces.
- Registro WAL: repetición exacta, corrupción, escritura cortada, fallo después
  de force, bloqueo del escritor y dos aperturas simultáneas.
- Recarga de tarifas sin cambiar compras previas, JSON duplicado rechazado y
  checkpoint que no retrocede ante un reloj anterior.
- Arranque real NeoForge/Cobblemon 1.8.1 sin Cobbreeding/Incubator.
- Motor real: Piedra Eterna, Lazo Destino, Ditto/genderless, formas regionales,
  familias especiales, especies incompatibles, repetición de atributos/UUID,
  objetos conservados y movimientos huevo.
- Entrega real: generación bloqueada, padre ausente, PC lleno conserva el
  mismo hijo, guardado/readback del PC y cierre sin repetir entrega.
- AVECOINS 2.4 real: moneda gestionada con namespace, cobro exacto, rechazo por
  saldo insuficiente y nonce repetido sin cobrar otra vez. Interrupción simulada
  después del débito y antes del recibo; reinicio del módulo conserva UNCERTAIN,
  impide entregar y permite reconciliar mediante el comando administrativo.
- Dos arranques y cierres normales del servidor local, conservando el registro.

## Reproducir

`python tools/breeding-smoke.py` ejecuta dos servidores consecutivos sobre
`neoforge/build/breeding-smoke`, solo localhost (25986/25987). Copia únicamente
una instancia local de AVECOINS si se establece
`ZIANUTILITIES_SMOKE_AVECOINS_JAR=/ruta/avecoins-2.4.jar`.
Los saldos son de propietarios ficticios en ese mundo. Nunca copia datos de
jugadores ni modifica Rassvet. Evidencias locales: `build/breeding-smoke-evidence`.
El fixture se excluye del JAR publicado; no existe comando para saltar el mínimo.

Los mensajes de Cobblemon sobre archivos .old inexistentes para cuentas recién
creadas y el mixin opcional de Adorn ausente se observaron también en el entorno
mínimo; no se consideran pruebas de pérdida de datos existentes.

## Pendiente antes de habilitar en producción

- Prueba visual de los menús con cliente beta.10, incluidas teclas rápidas,
  arrastre, expulsión de objetos, escalado y dobles clics.
- Youer 1.21.1 real con LuckPerms: rango normal/VIP, denies, degradación de rango
  y varios jugadores. Las pruebas locales usan el fallback de permisos NeoForge.
- Hard-kill del proceso y cortes de almacenamiento después de cada frontera
  WAL/AVECOINS/PC. La inyección de fallos y reinicio del módulo no equivalen a
  cortar la alimentación ni demuestran atomicidad entre tres almacenes.
- Size Variation instalado, datapacks del servidor y otros mods que intercepten
  inserciones/intercambios del PC.
- Reconexión con entregas pendientes y consecuencias de reloj adelantado.

La función viene `enabled=false`. No se ha desplegado esta candidata en Rassvet,
no se ha marcado el PR listo para fusionar ni se ha publicado una release.
