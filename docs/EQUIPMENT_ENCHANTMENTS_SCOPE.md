# Equipo y encantamiento de minería (alpha.28)

La primera entrega reúne tres sets completos: Captura, Explorador y Campeón. Cada uno tiene casco, pechera, grebas y botas. Los doce objetos usan la defensa, dureza, resistencia al retroceso, encantabilidad y durabilidad de la netherita; son resistentes al fuego y se reparan con lingotes de netherita. Sus texturas se incluyen en el JAR, sin paquete de recursos adicional.

No hay recetas de fabricación. Un administrador puede entregarlos con `/give <jugador> zianutilities:captura_helmet 1` (sustituir `captura` por `explorador` o `campeon`, y `helmet` por `chestplate`, `leggings` o `boots`). También aparecen en la pestaña de combate del inventario creativo. La interfaz de gachas existente puede aceptar estos objetos desde la mano del administrador.

`zianutilities:mineria_3x3` es un encantamiento de nivel único para picos. Puede salir en mesa de encantamientos y aplicarse mediante yunque. Después de romper un bloque, intenta romper los ocho vecinos del plano perpendicular a la cara golpeada. La rotura inicial debe haberse completado; por ello, un evento cancelado por un sistema de protección no inicia la rotura de área. Cada bloque adicional pasa por la ruta normal de rotura del jugador, que permite a las protecciones cancelar individualmente y aplica desgaste, botín y encantamientos del pico.

Para limitar sorpresas, el efecto no se activa en creativo ni espectador, no carga chunks, no rompe contenedores ni otros bloques con entidad, exige un pico adecuado y omite bloques más duros que el original. Si el jugador cambia el pico antes de procesar los vecinos, no se ejecuta. No hay recursión: una rotura adicional no inicia otro 3×3.

## Prueba recomendada en Youer

1. Instalar el mismo JAR alpha.28 en servidor y cliente; retirar la alpha anterior.
2. Verificar los tres sets en creativo o con `/give` y comprobar estadísticas y texturas al equiparlos.
3. Encantar un pico con `/enchant <jugador> zianutilities:mineria_3x3 1`. En supervivencia, romper piedra de frente, desde arriba y desde un lado; confirmar un solo plano de hasta nueve bloques y el desgaste del pico.
4. Repetir dentro de un claim/protección y con un cofre junto al área. El bloque protegido y el cofre deben permanecer.

La prueba local automatizada comprueba la geometría de las seis caras y el empaquetado. La comprobación funcional con Youer y plugins de protección requiere el servidor de prueba.
