# Nodes-Military v1.1.0

**Nodes-Military** is a Java 21 / Paper 1.21.x addon for **MilzzyNodes**, focused on nation armies, enlistment, commanders, division ranks, strategic orders and an in-game inventory GUI. It is independently maintained under **Auroria-Productions**.

> **New in v1.1.0:** GUI menus with permission-gated controls, optional Nodes-Extra supply reporting, safer saving with backups, and a complete source-based replacement build. The original ZIP supplied a v1.0.0 compiled JAR but not its Java sources. This version is a compatible-source **reimplementation** of the documented systems, not a recovered copy of that unavailable original source.

## Requirements

- Paper 1.21.x, **Java 21**.
- Enabled **`milzzynodes`** core plugin. The adapter is compiled and checked against the 21 November 2026 core build, but other builds need compatibility testing.
- **Nodes-Extra** is **optional**. With it installed, the plugin reads governments, supply depots and infrastructure from `plugins/Nodes-Extra/state.json` (read-only). Without it, armies, ranks, enlistment, GUI and orders still work.

## Installation

1. Stop Paper and back up `plugins/Nodes-Military/`, plus your core and Nodes-Extra folders.
2. Delete or replace the previous `Nodes-Military.jar` from `plugins/`. **Do not run both versions simultaneously.**
3. Upload the new **`Nodes-Military.jar`**, keeping your `milzzynodes.jar` in place.
4. Restart Paper; check `/plugins` and the console for the enabled message.
5. Use **`/nmilitary gui`** in-game. You can also use **`/nodes military gui`** when the shared `/nodes` command bridge is available.
6. If using Nodes-Extra, run `/nextra save` once so supply reports can read its current snapshot.

The first load reads existing `plugins/Nodes-Military/state.json` using the v1.0.0 schema. On each save it preserves a **`state.json.bak`** backup before atomically replacing the file. If the state is malformed or uses an unsupported schema, Nodes-Military stops rather than deleting it. Existing army/division names, commander UUIDs, enlistment, ranks, muster locations, orders and history remain representable.

## In-game GUI

The six-row inventory GUI lets citizens browse their nation's army and divisions and **enlist** (or leave) by clicking. All actions are processed as the real player through the authoritative command handler; an item click cannot bypass military permissions.

**Every nation citizen** can view army and division information, see current ranks, enlist in one division at a time, leave it, and request supply reports. **Authorised commanders and leaders** additionally see:

- Create armies and divisions (choose a type, enter the name privately in chat).
- Set an army muster territory at their current location.
- Appoint army or division commanders from eligible citizens.
- Change enlisted player ranks through a click-to-select menu.
- Issue HOLD, MOVE or RETREAT strategic orders by territory ID.
- Disband armies/divisions, with a separate confirmation screen.

The GUI cancels inventory moves and drags, rechecks the nation when clicked, and accepts only 2–24-character safe names or numeric territory IDs as private chat inputs. Inputs expire after two minutes or on logout. **No chat text is broadcast while an input prompt is active.**

## Commands

| Command | Purpose | Access |
|---|---|---|
| `/nmilitary gui` | Open military GUI | Nation citizen |
| `/nmilitary overview` | View your nation's armies | Citizen |
| `/nmilitary army create <name>` | Create army | National leader / Marshal / admin |
| `/nmilitary army info <army>` | Inspect army | Citizen |
| `/nmilitary army commander <army> <citizen>` | Appoint army commander | National leadership |
| `/nmilitary army disband <army>` | Remove army | National leadership / army commander |
| `/nmilitary division create <army> <division> <type>` | Add formation | National leadership / army commander |
| `/nmilitary division commander <army> <division> <citizen>` | Appoint enlisted commander | National leadership / army commander |
| `/nmilitary division roster <army> <division>` | View names/ranks in chat | Citizen |
| `/nmilitary division rank <army> <division> <citizen> <rank>` | Promote/demote enlisted member | National, army or division commander |
| `/nmilitary division disband <army> <division>` | Remove division | National leadership / army commander |
| `/nmilitary enlist <army> <division>` | Join a division | Nation citizen |
| `/nmilitary leave` | Leave current division | Enlisted citizen |
| `/nmilitary muster <army>` | Set own-territory muster | National leadership / army commander |
| `/nmilitary order <army> <division> <hold\|move\|retreat> <territory-id>` | Log strategic order on owned territory | National, army or division commander |
| `/nmilitary report <army>` | Strength and supply readiness | Citizen |
| `/nmilitary history` | Audit history | Admin |
| `/nmilitary save` / `/nmilitary reload` | Persist state / reload settings | Admin |

Use **`/nodes military ...`** for the same commands if your core `/nodes` executor is available. The original `/nodes` subcommands and Nodes-Extra routing are passed through untouched. Allowed formation types: `infantry`, `cavalry`, `artillery`, `engineers`, `naval`, `reconnaissance`. Ranks: Private, Corporal, Sergeant, Lieutenant, Captain, Major, Colonel, General.

## Authority and safety

- `nodesmilitary.use`: default **everyone**; nation and role checks apply separately.
- `nodesmilitary.admin`: default **OP**; may administer global state.
- `nodesextra.admin`: also recognised for leadership authority.
- Nation **capital sovereign, town officers/generals/leaders, Nodes-Extra Marshal and government head** can manage national military state; army and division commanders manage their assigned formations.
- Command permissions are checked again on every GUI mutation. Other nations' armies cannot be edited by guessing names.
- Division membership is one division per player across all armies. A division must have capacity for enlistment.
- Muster/strategic order target territories must belong to the issuing nation's territory network.
- A failed/unparseable save is not overwritten. A timestamped audit trail is maintained in the private state.

## Nodes-Extra supply integration

The integration is **read-only**. If present, Nodes-Military checks for stocked and unblocked depots connected through owned territories and Nodes-Extra sea links backed by shared native Nodes port groups. Ordinary road/rail links reuse native territory adjacency. Reports include SUPPLIED / LIMITED / BLOCKADED / EMPTY / UNSUPPLIED. No depot stock is consumed and no combat benefits are applied. With Nodes-Extra absent, the report says **Nodes-Extra not installed**.

## Configuration and files

In `plugins/Nodes-Military/config.yml`: `military.requireMarshalAuthority`, max armies (6), divisions (8), members (50), supply hops (12), `gui.enabled`, `map.exportPublicData`, and save interval (120 seconds).

- `plugins/Nodes-Military/state.json`: **private** enlistments, UUIDs, military state and audit history. Never publish this file.
- `plugins/Nodes-Military/state.json.bak`: previous state backup.
- `plugins/Nodes-Military/public.json`: public army/division overview without member UUIDs, member names or private government data. The MilzzyNodesMap viewer needs its own overlay code to render this feed.

## Source and building

This repository includes the Java 21 source, local `build.sh`, tests, and **CI-only Nodes API signature stubs** used in GitHub Actions to build without publishing the core plugin. Stub classes are excluded from the production JAR. For a stronger local compatibility build, compile against **your actual core**:

```bash
PAPER_API=/path/to/paper-api-1.21.jar \
NODES_JAR=/path/to/milzzynodes.jar \
DEPS_DIR=/path/to/Paper/dependencies \
bash build.sh
```

GitHub Actions builds `Nodes-Military.jar` and attaches it and `Nodes-Military-v1.1.0-Source.zip` to the release. **The original v1.0.0 compiled JAR has no matching original Java source in the supplied ZIP.** This v1.1.0 source is a functional reimplementation that retains compatible file field names.

## Validation and limitations

Verified offline with the 21 November 2026 MilzzyNodes core signature JAR: Java 21 compilation, save/round-trip tests, malformed state fail-closed handling, and graph routing limits. A live server with two players has **not** been tested here: confirm GUI clicks, permissions, enlistment, and `/nodes` command chaining on a staging Paper server before replacing production. Strategic orders are **record-only**: they do not teleport armies, capture land, grant buffs or start wars. MilzzyNodesMap overlays are not bundled. Folia is not supported.

**License:** GNU GPLv3; retain original attribution and license notices.
