# AVECOINS integration reference

These files are preserved from the Zian GTS economy integration as a technical reference for the future Zian Utilities economy module.

They are **not production sources of Zian Utilities** and intentionally retain their original Zian GTS package names.

Reference flow:

```text
TradeEngine
   ↓
EconomyPort
   ↓
AvecoinsEconomyPort
   ↓
AvecoinsWallet
   ↓
AVECOINS 2.3
```

Important semantics:

- `Applied`: the mutation is confirmed.
- `Rejected`: the mutation was rejected for a known reason.
- `Uncertain`: the result cannot be determined safely and must not be retried blindly.

The current AVECOINS 2.3 integration is reflective and validates the inspected wallet contract at runtime.

For Zian Utilities, this pattern is expected to inform a future generic economy boundary used by Rewards, Quests and Gacha.
