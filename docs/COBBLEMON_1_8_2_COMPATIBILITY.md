# Cobblemon 1.8.2 — compatibilidad y límites de la prueba

Publicación oficial NeoForge: https://modrinth.com/mod/cobblemon/version/9IXh9584
JAR: Cobblemon-neoforge-1.8.2+1.21.1.jar, SHA512 comprobado contra Modrinth.
La comprobación usa los JAR reales de Rassvet, no solo los builds locales.

## Hallazgos

- GTS beta.2.4: ninguna firma Cobblemon rota entre 48 referencias; 103 pruebas
  pasan compilando contra 1.8.2 y llega a runtime ready en Youer.
- RCT beta.1: ninguna firma rota entre 96 referencias; 124 pruebas pasan.
  Su dependencia RCT API 0.16.1-beta sí referencia IRON_BALL como CobblemonItem,
  mientras 1.8.2 lo declara con otro tipo. La función affected es
  BattleEffects.Pokemon.State.raised(BattlePokemon). No se considera validada la
  batalla de entrenadores aunque el servidor llegue a Done.
- Utilities beta.10 y beta.11: DESTINY_KNOT y LIGHT_BALL tienen la misma ruptura
  binaria. Recompilar no corrige el JAR que ya tenía el usuario.
- Utilities beta.12 usa los IDs del registro de objetos para ambos casos y evita
  depender de la clase concreta del objeto decorativo. Pruebas funcionales de
  genética, objetos, UI codec, UUID, padres, PC lleno/guardado, aceleración y
  reconciliación pasan con 1.8.2 y con 1.8.1. Mantiene la interfaz beta.11.
- Manager alpha.22 y GUI beta.7 instalados no tienen referencias directas a la
  API Cobblemon; sus builds y suites (78 y 37 pruebas) pasan y cargan en Youer.

Se compararon firmas de clases, campos y métodos con los dos JAR oficiales.
Las jerarquías externas y los accesos por reflexión no se certifican mediante
ese análisis; se complementan con el arranque y pruebas funcionales.

## Entorno aislado

Contenedor independiente, misma imagen Java 21/Youer 650daf46, 53 mods copiados,
plugins copiados como JAR, red deshabilitada y mundo nuevo. No monta el volumen
principal ni copia bases de jugadores. Sus errores iniciales de LuckPerms/LPC
se debieron a dependencias ausentes en la prueba sin Internet. La copia de su
caché permitió habilitar LuckPerms. EternalCore presentó el mismo tipo de falta
 de caché y Multiverse generó conflictos de dimensiones con su configuración
vacía de prueba. Estos errores no se atribuyen a Cobblemon, pero tampoco se
presenta ese arranque como validación completa de todos los plugins.

El contenedor de prueba quedó detenido. El servidor principal permanece apagado,
con Cobblemon 1.8.1 y Utilities beta.10: la instalación beta.11 se pausó a petición
del usuario. La última RCT API publicada comprobada sigue siendo 0.16.1-beta;
no se modificó su JAR ni se instaló Cobblemon 1.8.2 en producción.

Pendientes: sesiones gráficas reales y batallas completas en Youer, además de
cualquier cambio de terceros posterior a esta comprobación.
