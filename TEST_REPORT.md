# Validation report — Nodes-Military 1.1.0

**Compiled locally** with Java 21, Paper API 1.21 and the supplied 21 Nov 2026 MilzzyNodes core JAR.

Offline regression tests:

1. v1.0.0-shaped JSON loads without migration.
2. Army muster and divisions remain intact.
3. Enlisted member UUIDs and ranks remain intact.
4. Backup created before overwrite.
5. Save/load round-trip retains enlistment.
6. Malformed JSON fails closed and original input is unchanged.
7. Bounded supply route succeeds at hop limit.
8. Bounded supply route excludes beyond the hop limit and rejects absent start.

**Not verified:** two-player live Paper GUI interaction, role-based inventory actions, chat input under other chat plugins, client UI appearance, actual Nodes-Extra deposits on a running server, and `/nodes` command owner conflicts. Test these on staging before production deployment.
