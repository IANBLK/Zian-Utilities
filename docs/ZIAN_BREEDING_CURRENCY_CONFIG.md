# Configuración de crianza y aceleración

Archivo real: `config/zianutilities-breeding.json`, creado al arrancar.
Recarga: `/ZianUtilities breeding-admin reload`. Una configuración inválida
conserva la última válida en memoria. JSON duplicado, tarifas nulas/negativas,
identificadores inválidos o un mínimo inferior a 120 minutos se rechazan.

```json
{
  "enabled": false,
  "baseMinutes": 1440,
  "minimumMinutes": 120,
  "defaultSlots": 1,
  "vipSlots": 2,
  "maxSlots": 8,
  "alphaChance": 0.05,
  "currencyReductionsMinutes": {
    "avecoins:coppercoin": 2,
    "avecoins:diamondcoin": 20,
    "avecoins:netheriteticket": 220
  }
}
```

Las tres tarifas son ejemplos iniciales de balanceo, no una lista fija. Añade
cualquier ID real de `AVECOINS MANAGED_RESULTS` con minutos positivos. Solo
aparecen monedas configuradas que existan en la versión AVECOINS compatible
(2.3/2.4). Omitir una moneda deshabilita su uso. Sin AVECOINS la crianza gratuita
funciona; la compra de aceleraciones queda deshabilitada.

El cobro sale de la billetera persistente, no de stacks del inventario. La
confirmación registra unidades y tarifa; no cobra nada si la reducción es cero
ni permite comprar más unidades de las útiles. La última unidad puede cubrir
solo una fracción de su tarifa: se avisa en la GUI, no hay devolución parcial.
Una tarifa modificada entre preparar y confirmar invalida esa confirmación.
Los pagos previos conservan su reducción; los cambios de base/mínimo afectan
solo a nuevas crianzas. No se limita arbitrariamente la cantidad de monedas,
pero se rechazan cantidades que exceden el tiempo útil o desbordan el cálculo.

Revisa las tarifas frente a los cambios/crafteos de moneda de tu economía:
la configuración acepta valores independientes y no prueba que sus precios
sean equivalentes entre denominaciones.
