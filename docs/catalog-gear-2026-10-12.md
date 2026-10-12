# Beta.20: catálogo y equipo importado

Gachas abre un catálogo paginado de tarjetas. Cada tarjeta muestra nombre, icono, descripción y costo; al pulsarla se abre el detalle y el giro existente. En Editar, los administradores pueden copiar como icono el objeto sostenido (sin consumirlo) y guardar una descripción de hasta 160 caracteres. Las ediciones cosméticas conservan publicación, premios y costo. Los datos anteriores siguen siendo válidos. El protocolo gacha-ui-3 requiere actualizar cliente y servidor juntos.

Se registran 42 objetos: 32 piezas de armadura, nueve armas/herramientas y un grimorio decorativo. Reforged leather/chainmail/copper/golden/iron/diamond usan estadísticas de diamante; Reforged netherite añade un punto de defensa por pieza a netherita. Dark Lightning usa netherita. Cosmic Sword, Molten Mace y Reaper's Scythe añaden un punto de daño a netherita; Frost Axe, Poison Dagger y Storm Katana usan diamante. El grimorio no aporta habilidades. Se mantienen los objetos previos.

Los modelos Reforged se convierten desde su geometría Blockbench original en mallas por parte y ranura. Se conservan los PNG originales y se omiten shaders/configuraciones de plugins. Dark Lightning y las seis armas planas usan modelos nativos compatibles con 1.21.1. No se añaden dependencias de renderizado. Los nombres y el lore mantienen el estilo fantástico existente.

Validación: compilación y 160 pruebas; dos arranques nativos con SQL y AVECoins real verifican metadatos, codec, publicación, 32 armaduras, nueve herramientas, encantamientos y extracción de obsidiana. Se verificaron UV, geometría y texturas; queda la comprobación visual en un cliente real (equipado, inventario y recarga de recursos).
