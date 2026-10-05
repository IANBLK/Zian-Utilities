# Beta.9 command migration

The command root is now `/ZianUtilities`, with this exact capitalization. `/zian` is no longer registered. Existing subcommands keep their names and access checks.

Examples:

```text
/ZianUtilities
/ZianUtilities generation active
/ZianUtilities generation enable gen1
/ZianUtilities gacha
/ZianUtilities gacha review IANBLK
/ZianUtilities quest
/ZianUtilities reward status
```

Update command blocks, scripts and menu configurations that execute the old command. Console commands omit the leading slash. Do not rename permission nodes, configuration files or saved data. Historical test transcripts and older changelogs retain the command used by that version.

Install beta.9 on both server and clients, then restart. Verify with a non-operator that the hub, quest and gacha screens open and `generation active` works. Administrative generation mutations and gacha recovery commands must remain restricted; repeat the existing LuckPerms/Youer access checks. This release does not introduce a new permission provider or modify existing authorization predicates.

Check that saved quests, pending prizes and generations remain intact. Command-tree regression tests cover merging all nine registrations into one root and removing the old root; real Youer and LuckPerms behavior still requires in-game verification.
