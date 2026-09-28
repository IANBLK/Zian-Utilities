# Beta 1: validación disponible

La candidata `0.1.0-beta.1` contiene la misma lógica de juego que
`0.1.0-alpha.29`; cambia la versión y la documentación. El operador probó
alpha.29 en Youer 1.21.1 con dos cuentas y AVECOINS 2.4. La publicación beta
debe distinguir esa prueba de una instalación independiente del JAR beta.

## Confirmaciones del operador

- Tras un reinicio del servidor se conservaron el progreso de las misiones y
  de las campañas por generación.
- El lunes se abrió una semana nueva con `0/25` capturas y `0/50` victorias,
  y los objetivos volvieron a estar disponibles para aceptar.
- Las misiones globales pagaron a las dos cuentas. El log muestra dos
  `global_quest_credit ... result=CLAIMED` a las 07:12 y 07:15.
- Una etapa de campaña Gen 1 pagó: `progression_quest_credit`,
  `key=campaign:gen1:0`, `result=CLAIMED` a las 07:14.
- Cada cuenta completó una tirada del gacha 1. El log muestra
  `gacha_roll ... ticket=avecoins:goldticket cost=1 result=READY` y el
  `gacha_claim ... result=DELIVERED` correspondiente a las 07:12 y 07:15.
- Una tirada adicional sin tickets se rechazó con
  `result=insufficient_tickets` a las 07:15. El operador confirmó los
  cambios de wallet y la entrega de premios.
- El operador confirmó que las armaduras y herramientas aceptan los
  encantamientos adecuados, que minería 3×3 funciona y que las texturas
  actuales son aceptables como provisionales.

## Límites de la evidencia

Los extractos de consola prueban las transiciones registradas, pero no
incluyen el balance numérico anterior y posterior de cada wallet. La
confirmación de esos balances proviene de la comprobación del operador en
`/avc wallet`. No hubo una prueba de carga con muchos jugadores ni una
validación de AVECOINS posterior a 2.4. El JAR beta conserva exactamente la
lógica probada en alpha.29; el operador aún no ha informado una instalación
separada del archivo beta.
