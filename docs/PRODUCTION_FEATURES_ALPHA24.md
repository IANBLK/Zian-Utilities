# Funciones normales sin parámetros de prueba (beta.1)

Instala el mismo JAR beta.1 en el servidor y en todos los clientes. Retira los JARs antiguos de Zian Utilities y conserva una copia de seguridad del mundo antes de actualizar. AVECOINS 2.3 o 2.4 debe estar disponible para los pagos.

Al primer arranque, el mod crea `config/zianutilities-features.properties` con:

```properties
quests.enabled=true
quests.rewards.enabled=true
gachas.enabled=true
gachas.payments.enabled=true
```

Estas opciones se leen al arrancar. Edita el archivo con el servidor detenido y reinicia para aplicar cambios. Las recompensas de misiones se fijan al crear cada ventana de tres horas; cambiar `quests.rewards.enabled` no convierte una ventana de prueba ya creada en una ventana pagada. Gachas y pagos no estarán disponibles si sus interruptores están desactivados. Una configuración inválida o ilegible desactiva las cuatro funciones y deja el motivo en el log.

Los antiguos parámetros `globalQuestRewardTestEnabled`, `gachaTestEnabled` y `gachaPaymentTestEnabled` ya no controlan las funciones normales. `globalQuestTestEnabled` solo conserva el comando administrativo `/ZianUtilities quest test rotate`, que no paga recompensas. No incluyas este parámetro en un servidor normal.

AVECOINS se comprueba en cada operación económica; una versión o contrato incompatible no autoriza créditos ni descuentos. Los premios de gacha pendientes y el progreso de misiones siguen en sus archivos de mundo existentes.
