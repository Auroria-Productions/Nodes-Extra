# Changelog

## v1.1.0 — GUI and optional Nodes-Extra integration

- Reimplemented the v1.0.0 documented military commands against MilzzyNodes; preserved old army/division/roster JSON fields.
- Added 6-row Bukkit inventory GUI for nation overviews, divisions, enlist/leave, ranks, orders, appointments and confirmation-protected disbanding.
- GUI calls the normal permission-checked command handler; private text input never broadcasts and expires after two minutes.
- Made Nodes-Extra optional; the government/depot file remains read-only.
- Added save backups, atomic writes, fail-closed invalid state loading and private-versus-public JSON exports.
- Kept `/nmilitary` aliases and forwarded `/nodes military` without replacing other `/nodes` handlers.
- Added supply-distance BFS and regression tests.

## v1.0.0 — Supplied binary baseline

- Original JAR supplied by user. Original Java source was not included in source ZIP; new version uses a documented compatible data schema.
