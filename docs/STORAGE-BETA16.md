# Almacenamiento de crianza y GUI — beta.16

## Rendimiento

- Los padres ocupados, compras y trabajos de cada usuario tienen índices propios.
- El tick y la GUI de trabajos consultan pendientes, sin recorrer el historial
  completado del servidor cada segundo.
- En memoria se conservan completos únicamente los trabajos que ocupan una plaza.
  Los cerrados se consultan desde una posición indexada del WAL, con verificación
  de longitud y hash. Sus hijos y pagos continúan disponibles para inspección.
- Se omiten escrituras de snapshots idénticos y checkpoints de reloj si no hay
  pendientes. El máximo de reloj persistido se conserva.
- Una aceleración interrumpida pasa a UNCERTAIN al reiniciar mediante escritura
  durable y actualización de los índices; un fallo de confirmación mantiene una
  barrera en memoria y bloquea nuevas operaciones hasta revisión/reinicio.

## Compactación offline

Con el servidor detenido y sin reinicio automático, Java 21 puede ejecutar estas
herramientas desde el JAR del mod junto con Gson:

```text
java -cp <utilities.jar>:<gson.jar> com.zianblk.zianutilities.neoforge.breeding.BreedingStorageMaintenance inspect <mundo>/data/zianutilities/breeding_v1/journal.wal
java -cp <utilities.jar>:<gson.jar> com.zianblk.zianutilities.neoforge.breeding.BreedingStorageMaintenance compact-offline <mundo>/data/zianutilities/breeding_v1/journal.wal
```

En Windows el separador es `;`; entrecomillar rutas con espacios. El mantenimiento
no debe coincidir con ningún arranque, incluido el de una versión antigua.

La compactación guarda una copia gzip del WAL completo y compara sus bytes
descomprimidos por SHA-256. Después escribe un snapshot por trabajo y verifica
que hijos, recibos, estados y reloj coincidan. Reemplaza el activo únicamente con
un movimiento atómico. Conserva todos los IDs de compra; si el formato contiene
campos desconocidos, se niega a compactar. No cambia el formato de WAL esquema 1.

No borra la auditoría antigua: la comprime. Los backups crecen y deben incluirse
en el plan de capacidad. No compactar un diario corrupto ni quitar a ciegas el
marcador `.maintenance` que puede quedar tras un corte abrupto.

Pruebas: Windows con pagos inciertos, snapshots antiguos, corrupción, escritor
activo e interrupción antes/después del reemplazo; Linux con un diario sintético
de siete trabajos en todos los estados, sin datos de jugadores. No se ha probado
la exportación/compactación de un WAL real del servidor en el equipo local.

## Interfaz

La crianza adapta el patrón del documento suministrado de AVECOINS a Minecraft
1.21.1: una transformación uniforme y conversión inversa del ratón, incluyendo
clic, liberación, arrastre, movimiento y scroll. Usa tamaños lógicos y recalcula
al reconstruir widgets/redimensionar. Los tooltips nativos se dibujan fuera de la
transformación, en el viewport real, para mantener sus límites y legibilidad.
El texto de estado queda centrado bajo la posible cría. La validación visual de
escala GUI 2/3/4 sigue requiriendo entrar con el cliente actualizado.

No cambia el protocolo `breeding-ui-2`; la crianza sigue independiente de las
generaciones del spawn salvo la opción administrativa ya existente.

## Misiones y conservación

Las misiones globales de tres horas ya conservan solo la actual y la anterior por
jugador. Al salir una misión anterior del historial, el runtime elimina únicamente
sus recibos CLAIMED cuando su periodo es anterior al entrante. Los recibos inciertos
o incompletos no se eliminan por ese mecanismo. Esta actualización conserva esa
política existente.

Las semanales usan un archivo por jugador y semana, con objetivos acotados a 25
capturas y 50 combates. La recuperación de semanas antiguas se ejecuta una vez por
jugador y sesión del servidor para completar pagos pendientes. No hay un borrado
automático seguro de esos directorios: deben conservarse junto con reward_claims.
La campaña guarda progreso por jugador y generación, separado de las rotaciones.

Para dimensionar: con 2.000 participantes en cada una de 44 semanas habría hasta
88.000 archivos de progreso semanal, además de ofertas y recibos; es un escenario
conservador, no una medición ni un límite. Vigilar número de archivos, tamaño de
backups y duración de las recuperaciones; no borrar una semana solo por antigüedad.
La auditoría agregada de Manager puede contar el tamaño de Utilities sin mostrar
UUID ni contenido de jugadores.
