# M2 read-only capture event probe on Youer

The alpha.4 build adds an opt-in diagnostic for Cobblemon 1.8.1 capture events.
It does not assign quests, save progress, change Generation Control, or deliver
rewards. The probe is disabled unless the Java startup property
`-Dzianutilities.questCaptureProbeEnabled=true` appears before `-jar`.

1. Back up the Youer 1.21.1 test server. Stop it normally and replace only
   the Zian Utilities alpha.3 JAR with the alpha.4 JAR. Keep exactly one Zian
   Utilities JAR in `mods/` and leave the other mods unchanged.
2. Start with the capture probe flag enabled. Keep
   `-Dzianutilities.rewardTestCreditEnabled=true` **absent**. The console should
   show `[ZIAN-QUEST-PROBE] capture=enabled rewards=disabled` once at startup.
3. Run `/zian generation active` and record the enabled generations. Capture
   one naturally encountered Pokémon whose generation is known. Do not change
   generation settings just for this probe.
4. Look for exactly one console line with
   `[ZIAN-QUEST-PROBE] event=capture playerUuid=... species=...
   generations=...`. Check that the player, species, and generation match the
   captured Pokémon. Send that line and the corresponding in-game capture
   evidence. A species with unresolved generation is reported as `unknown`.
5. Stop normally, remove the capture probe flag, and restart. Subsequent
   captures should produce no `[ZIAN-QUEST-PROBE] event=capture` lines.

Stop the test if startup fails or a capture logs the wrong player, species, or
generation. This probe does not prove quest progress persistence or reward
delivery. Those remain disabled until separate tests cover them.

