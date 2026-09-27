# Gacha alpha.19 test protocol

Install the same alpha.19 JAR on the Youer 1.21.1 server and every required NeoForge client. Remove older Zian Utilities JARs. This first slice is behind two JVM properties, both placed before `-jar server.jar`:

- `-Dzianutilities.gachaTestEnabled=true`: enables the Gachas card, `/zian gacha`, pool editing, and prize preview. No ticket is deducted and no roll is allowed.
- `-Dzianutilities.gachaPaymentTestEnabled=true`: additionally permits real AVECOINS ticket debits and item awards. Enable only on a backed-up test world after verifying preview, odds, and published pool contents.

An OP opens Gachas, clicks **Editar**, creates a pool, selects a ticket type, adjusts cost, holds a prize stack in the main hand and clicks **Añadir objeto en mano**. This copies the stack; it does not remove the original. The OP can change weights, rename the pool, and publish it. Editing a published pool returns it to draft until republished. The first version permits at most eight pools with twelve prizes each, and each roll awards exactly one item. The player sees the configured prize odds before confirming a roll.

The server owns the result and operation journal at `world/data/zianutilities/gacha_v1/`. Pools are stored in `pools.nbt`; each player's operations are under `players/<uuid>.nbt`. The operation is written before the ticket debit. If the complete prize fits in the 36-slot main inventory, it is delivered immediately. Otherwise it enters the pending list, and the player claims it from the Gachas screen after making space. If an external debit or inventory delivery is uncertain, the operation is blocked for manual review and is never automatically charged or delivered again. Terminal records are limited to the latest 100 per player; pending and uncertain records are retained.

Suggested live test:

1. Start with only `gachaTestEnabled=true`. Create a pool with two ordinary test items; check that the source stacks remain in the admin inventory and percentages update when weights change. Reconnect and restart the server: the pool must remain.
2. Confirm that non-OP players cannot edit and that the player preview shows the same published ticket type, cost, prizes, and odds.
3. Back up the world, enable `gachaPaymentTestEnabled=true`, and use a test account with one matching AVECOINS ticket. Check that a roll deducts the configured ticket amount exactly once and creates one pending prize.
4. Fill the inventory before claiming. The prize must stay pending; clear space and claim it once. Reopen the screen and restart: no duplicate prize or second debit should appear.
5. If any operation reports uncertain payment or delivery, keep the world files and logs for manual reconciliation; do not retry the roll.

This alpha has no pity, Pokémon prizes, or automatic recovery of uncertain external mutations. Live Youer testing remains required before enabling paid rolls in production.
