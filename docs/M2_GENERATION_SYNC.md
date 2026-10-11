# Generation-aware capture planning for Youer 1.21.1

Alpha.6 adds a read-only `/zian quest plan` operator diagnostic. It previews
the first safe capture objective: capture one Pokémon from **any currently
enabled generation**. It does not assign a mission, deliver a reward, or
change Generation Control. The command remains available with the trial flag
off so operators can inspect the current policy on a normal server.

The policy derives candidates from the current Generation Control state. With
Gen1 and Gen2 active, the candidate set is Gen1 and Gen2; Gen8 cannot be
offered. With no generations active, planning pauses. The objective does not
lock a particular species or type. Type-specific missions remain disabled
until a future planner can verify eligible species and actual spawn
availability under the server's Cobblemon configuration.

For unfinished alpha.5 trial assignments, alpha.6 preserves the assignment ID
and updates its target generations when the operator next checks status or a
capture arrives. A completed assignment stays unchanged as historical
evidence. A zero-generation state pauses an unfinished trial until a
generation becomes active. The trial remains gated by
`-Dzianutilities.questTrialEnabled=true`; this release does not turn it on.
It does not change the existing file format or delete the alpha.5 trial data.

## Youer smoke test

1. Back up the test server. Stop normally and replace alpha.5 with the
   alpha.6 JAR; keep only one Zian Utilities JAR. Do not add any quest or
   reward test flags.
2. Start Youer. Run `/zian generation active` and `/zian quest plan` as an
   operator. With only Gen7 active, the preview must mention **gen7 only**
   and say `sin asignar`. It must not start a new trial or change the
   existing completed Komala trial file.
3. On a test server where a temporary generation change is acceptable,
   enable Gen1 and Gen2, then disable Gen7. Run both commands again. The
   preview must list **gen1,gen2** and must not mention Gen8 or Gen7.
4. Restore the original state: enable Gen7, then disable Gen1 and Gen2.
   Confirm `/zian generation active` again shows only gen7. A normal
   restart should retain this original state.

No capture is required for this read-only preview. Stop and report if the
preview offers a disabled generation, the old completed trial changes, or
Generation Control fails to restore. Passing the preview validates
generation filtering on Youer, not actual mission rotation or spawn-pool
availability.

## Alpha.6 Youer runtime preview — PASS

The operator supplied an in-game capture from the Youer 1.21.1 test server:

- With only gen7 active, `/zian quest plan` showed an unassigned
  capture-any-active-generation preview for `gen7`; type objectives were
  disabled.
- After disabling gen7 and enabling gen1 and gen2, `/zian generation
  active` showed `gen1, gen2`. The preview showed `gen1,gen2` and did not
  offer gen7 or gen8.
- The operator disabled gen1 and gen2, re-enabled gen7, and confirmed both
  `/zian generation active` and the preview returned to gen7 only.

This proves the read-only preview follows generation changes on the intended
Youer server and that the original active-generation state was restored. No
quest assignment, capture, reward, or type-filtered objective was exercised
in this test.
