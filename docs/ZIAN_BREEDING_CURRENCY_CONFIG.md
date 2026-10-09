# Zian Breeding: configurable AVECOINS acceleration

Status: **core-only implementation**. Not wired to production config loading,
commands, wallet, LuckPerms or GUI. Rates below are illustrative only.

The core model `CurrencyAccelerationRules` supports arbitrary currency IDs
using a map. AVECOINS denominations must be discovered from its runtime
managed-results registry; never limit the usable items to a hardcoded three-
or eight-entry list. Unknown currency IDs must be rejected server-side.

## Proposed server JSON format (not loaded yet)

```json
{
  "breeding": {
    "baseDurationMinutes": 1440,
    "minimumDurationMinutes": 120,
    "defaultSlots": 1,
    "vipSlots": 2,
    "currencyReductionsMinutes": {
      "coppercoin": 2,
      "diamondcoin": 20,
      "netheriteticket": 220
    }
  }
}
```

The three entries are **illustrative defaults**, not a verified complete
AVECOINS currency list. Administrators can add ANY additional AVECOINS
managed currency ID and positive reduction minutes without changing code.
The future loader must validate IDs against real AVECOINS configuration,
reject duplicates and bad values, and report unknown IDs clearly.
An omitted currency is disabled for breeding acceleration, not usable at zero cost.
Rates should be reloadable for **new purchases**, but previously completed
payments and their durations must not be retroactively recalculated.

## Rules

- Starting a breeding request is free; default countdown = 24h.
- Each currency defines an independent reduction in minutes.
- No count cap per currency; reductions clamp to a 2h floor from start.
- Never debit additional currency when it has no effect on the finish time.
- Do not refund or spend unused theoretical acceleration without a specified policy.
- In-flight wallet mutations need durable journal, stable operation IDs, and
  fail-closed recovery. Existing references are not production wiring.
- No Cobbreeding/Incubator JAR dependency is intended.
- Runtime support for configurable currencies and commands is **pending**.
