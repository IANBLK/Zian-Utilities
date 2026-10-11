# Equipo y encantamiento de minería (beta.5)

El equipo actual consiste en un solo set Prismático: casco, pechera, grebas, botas, espada, hacha, pico, pala y azada. Todas las piezas están en la pestaña creativa **Zian Utilities | Equipamiento** y no tienen recetas.

Cada pieza de armadura prismática tiene 1 punto de defensa más que la pieza de netherita correspondiente. La dureza es 4 (netherita: 3) y la resistencia al retroceso por pieza es 0,15 (netherita: 0,10). Durabilidad, encantabilidad, resistencia al fuego y reparación siguen la base de netherita.

La espada y las herramientas usan la categoría de minería de netherita, 2032 usos, velocidad de minería 10, bono de daño del material 5 y encantabilidad 16. Las etiquetas vanilla correspondientes permiten sus encantamientos normales.

El encantamiento `zianutilities:mineria_3x3` se aplica **solo por comando** a un pico: `/enchant <jugador> zianutilities:mineria_3x3 1`. No está en las etiquetas de mesa de encantamientos, botín ni comercio de aldeanos. Rompe hasta ocho bloques vecinos del plano perpendicular a la cara golpeada, usando la ruta normal de rotura para aplicar protecciones, botín y desgaste. No actúa si la rotura inicial se cancela, no carga chunks, omite contenedores y bloques más duros, y no se activa recursivamente.

Para probar una pieza: `/give <jugador> zianutilities:prismatic_helmet 1` o `/give <jugador> zianutilities:prismatic_sword 1`. Instala el mismo JAR en cliente y servidor; no hacen falta parámetros de startup.

Los objetos antiguos `captura_*`, `explorador_*` y `campeon_*` se retiraron por petición del propietario. Antes de actualizar un mundo donde existan esos objetos, haz una copia de seguridad y decide cómo sustituirlos.
