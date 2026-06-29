# OpenRune 239 Worklog

## Baseline: OpenRune 239.1 (Commit `bcb2de9c`)

- **Repo:** `/root/Runescape/open_rune/OpenRune-Server/`
- **Fork:** `joshhmann/OpenRune-Server` (github.com)
- **Baseline tag:** `baseline/openrune-239.1`
- **Revision:** 239, Cache build 2606
- **Server:** openclaw (CT 187, 192.168.0.187:43594)
- **Build:** `./gradlew :server:app:run` or `installDist`

**Git workflow:** Direct pushes to `joshhmann/main`. No PRs to upstream. Sync from upstream `origin/main` only when explicitly desired. Our `main` is canon.

---

## State: June 26, 2026 — Platform Migration Complete

### ✅ What OpenRune Already Has (skips porting from 238)

| System | Details | Files |
|--------|---------|-------|
| Interfaces | 14 modules: bank, combat-tab, emotes, equipment, fade-overlay, gameframe, journal-tab, logout-tab, menu, prayer-tab, settings, skill-guides, spellbook, worldmap | 61+ .kt |
| Skills | 10 modules: cooking, firemaking, fishing, herblore, magic, prayer, runecrafting, slayer, smithing, woodcutting | 10 modules |
| Drops | TOML-based weighted drop tables | 1,100 .toml |
| Generic LOC handlers | Doors, ladders, staircases, gates, bank booths, bookcases, signposts, chicken coops, pickables, search | 18 .kt |
| Generic NPC handlers | Banker, cows, ducks, sheep, persons | 10 .kt |
| Quest system | QuestScript framework + Cook's Assistant implemented | 15 .kt |
| Combat system | Full combat: accuracy formulas, max hit, weapon system, special attacks, spells, autocast | 50+ files |
| Death/respawn | NpcDeath, PlayerDeath with drops, skull, UIM | 21 files |
| Shops | Buy/sell/restock, standard GP operations | 12 files |
| Login/gameframe/mapclock | Complete login flow, gameframe, map clock | 6 files |
| NPC aggression | Partial — combat-commons has some, no dedicated module | Existing |
| Poison/venom/disease | Dedicated system at `api/mechanics/toxins/` | 5 files |
| Ranged ammunition | `api/combat/combat-commons/ranged/RangedAmmunition.kt` | Existing |
| Static zones | Full OSRS map from cache | 170,666 zones, 4.7M locs |
| Travel | Canoe system | 1 module |

---

## System Architecture

### OpenRune Ecosystem — What We Actually Run

The OpenRune project comes with ~7 source repositories, but **only OpenRune-Server needs to run:**

```
┌──────────────────────────────────────────────────────────────────┐
│                    RUNNING — OpenRune-Server                     │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │                    game.jar (pid)                         │    │
│  │  ┌──────────┐  ┌──────────────┐  ┌──────────────────┐   │    │
│  │  │ or-cache │  │ api/parsers/ │  │ Embedded Central  │   │    │
│  │  │ (decoder)│  │   toml/      │  │ (same-instance)   │   │    │
│  │  └──────────┘  └──────────────┘  │ + PostgreSQL      │   │    │
│  │                                  └──────────────────┘   │    │
│  │  Depends on Maven artifacts:                              │    │
│  │  • or2.definition  ← source = OpenRune-FileStore         │    │
│  │  • or2.filestore   ← source = OpenRune-FileStore         │    │
│  │  • toml-rsconfig   ← source = openrune-toml-parser       │    │
│  └──────────────────────────────────────────────────────────┘    │
│                  │                                               │
│        Port 43594 │ Port 8080 (Central HTTP)                     │
│                  │ Port 9091 (World link)                        │
└──────────────────┼───────────────────────────────────────────────┘
                   │
       ┌───────────┴──────────────┐
       │                          │
       ▼                          ▼
  RuneLite + RSProx          AgentBridge (future)
  (players)                  (LLM agents)
```

### What Each Repo Actually Is

| Repo | Location | What It Is | Needed? |
|:-----|:---------|:-----------|:-------:|
| **OpenRune-Server** | `/root/Runescape/open_rune/OpenRune-Server/` | The game server — has cache decoder, TOML parser, embedded central, everything | **✅ YES — running** |
| **OpenRune-FileStore** | `/root/Runescape/open_rune/OpenRune-FileStore/` | Source code for `or2.definition`, `or2.filestore` Maven libs | ❌ Reference only — server pulls from Maven |
| **OpenRune-FileStore-Server** | `/root/Runescape/open_rune/OpenRune-FileStore-Server/` | HTTP server for cache/wiki data. Has `wiki` module for dumping OSRS wiki | ❌ Not needed running — corpus data moved to our tools |
| **openrune-toml-parser** | `/root/Runescape/open_rune/openrune-toml-parser/` | Source for `toml-rsconfig:1.0` Maven artifact. `ConstantReplacement.kt` resolves string refs at parse time | ❌ Reference only — server has `api/parsers/toml/` |
| **OpenRune-Central-Server** | `/root/Runescape/open_rune/OpenRune-Central-Server/` | Standalone account server for multi-world setups | ❌ Not needed — server runs embedded via `same-instance: true` |
| **Launcher** | `/root/Runescape/open_rune/Launcher/` | Desktop client launcher for players | ❌ Not needed |
| **hosting** | `/root/Runescape/open_rune/hosting/` | Maven repo hosting files | ❌ Not needed |

### Server Startup Sequence

1. `or-cache` module loads cache from `.data/` (build 2606) — reads all NPC/item/obj/loc/etc definitions
2. Embedded PostgreSQL auto-starts (accounts, player saves)
3. Central Server auto-starts on port 8080 (HTTP) / 9091 (world-link) — handles account auth
4. Game engine boots — 33 plugin modules, 202 scripts, 170K static zones
5. Port 43594 bound and listening for RSProx connections

### Tooling Stack

Our content toolchain for OpenRune:

```
┌─────────────────────────────────────────────────────────────────┐
│                    TOOLING STACK                                │
│                                                                 │
│  ┌─────────────────┐  ┌────────────────┐  ┌─────────────────┐  │
│  │   osrs-mcp       │  │   Corpus        │  │   TOML-RSConfig │  │
│  │   (MCP server)   │  │   (batch data)  │  │   (parser)      │  │
│  │                  │  │                │  │                  │  │
│  │ • CacheTool      │  │ • 11,872 items │  │ • ConstantRepl. │  │
│  │ • GameValTool    │  │ • 3,906 NPCs   │  │ • TokenRepl.    │  │
│  │ • WikiTool       │  │ • 217 quests   │  │ • RsConfig DSL  │  │
│  │                  │  │ • 2,061 drops  │  │                  │  │
│  │ ./gradlew runMcp │  │  tools/data/   │  │  Built into OR   │  │
│  └─────────────────┘  └────────────────┘  └─────────────────┘  │
│                                                                 │
│  Use case: Need a single NPC     → osrs-mcp CacheTool           │
│  Use case: Need all item names   → Corpus batch load            │
│  Use case: Writing a content pack → toml-rsconfig string refs  │
└─────────────────────────────────────────────────────────────────┘
```

**Corpus** (356MB, moved to our tools directory) contains:
- `buckets/` — wiki bucket data (NPCs, items, objects grouped by category)
- `quests/` — 217 quests with dependencies, rewards, descriptions
- `skills/` — skill training data
- `parsed-data/` — structured NPC/item/object data
- `ironman-guide/` — optimal quest order progression phases
- `osrs_npc_list.json` / `osrs_npc_names.json` — NPC name→ID mappings
- `osrs_data.py` — Python loader class (`from osrs_data import OSRS`)

---

## OpenRune Development Patterns

### Module Discovery
Any directory with a `build.gradle.kts` under `content/`, `api/`, or `engine/` is auto-included as a Gradle subproject. Uses `base-conventions` plugin.

```kotlin
// build.gradle.kts for a content module
plugins { id("base-conventions") }
dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
}
```

### String-Based Entity References (key difference from rsmod)
OpenRune resolves references by string constant throughout:

```
"npc.cow"              → NPC type lookup
"obj.cake"             → Item type lookup
"stat.cooking"         → Skill constant
"component.bank:title" → UI component
"interface.bank"       → Interface constant
"varp.cookquest"       → Varbit lookup (quest progress)
"npc.cow_drop_table"   → Drop table reference
```

No more `val npc = find("name")` — just use the string directly in `onOpNpc1("npc.cook")`, TOML files (`npcs = ["npc.cow"]`), and quest attributes. The `ConstantProvider` resolves at runtime.

### Zone NPC Handler Pattern
```
content/areas/city/<zone>/
├── build.gradle.kts
└── src/main/kotlin/org/rsmod/content/areas/city/<zone>/
    ├── <Zone>Script.kt          # Root script — registers NPCs/locs/interactions
    └── npcs/
        ├── <NpcName>.kt          # Dialogue handler per NPC
        └── ...
```

```kotlin
// NPC handler example (Bob.kt in Lumbridge)
class Bob : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.bob") { startDialogue(it.npc) { bobDialogue(it.npc) } }
    }
}
```

### Quest Pattern (OpenRune's QuestScript)
```kotlin
class CooksAssistant : QuestScript("quest_cooksassistant", "varp.cookquest", rewards {
    xp("stat.cooking", 300.0)
}, ItemRewardDisplay("obj.cake")) {

    private val GIVEN_EGG = quest.attribute(name = "GIVEN_EGG", default = false)

    override fun ScriptContext.init() {
        onOpNpc1("npc.cook") { startCookDialogue(it.npc) }
    }

    override fun subTitle(): String =
        "talking to the <col=800000>Cook</col> in <col=800000>Lumbridge Castle</col>."

    override fun questLog(player: ProtectedAccess) = questJournal(player) {
        description("Description here...")
        objective("Find an <red>egg</red>.") {
            attribute(GIVEN_EGG, "I gave the cook an egg.").strike()
            hasItem("egg", "I found an egg.")
        }
    }

    override fun completedLog(player: ProtectedAccess): String = completionJournal(player) {
        line("Quest complete text...")
    }
}
```

Key things from Cook's Assistant:
- Quest attributes for fine-grained state (`GIVEN_EGG`, `GIVEN_MILK`, `GIVEN_FLOUR`)
- `choice2/choice4` for dialogue branching
- `chatPlayer(happy, "text")` / `chatNpc(worried, "text")` — mood annotations
- `quest.advanceQuestStage(access)` to progress stages
- `quest.isQuestCompleted(player)` / `quest.questState(player)` for state queries
- `deliverItem("obj.egg", ...)` helper pattern for item delivery

### Drop TOML Format
```toml
id = "Cow Drops"
npcs = ["npc.cow", "npc.cow2", "npc.cow3"]

[[guaranteed]]
obj = "obj.cow_hide"
count = 1

[[main]]
numerator = 1
denominator = 5
obj = "obj.bones"
count = 1

[[tertiary]]
numerator = 1
denominator = 128
obj = "obj.trail_clue_beginner"
count = 1
```

Drop table types: `[[guaranteed]]`, `[[main]]`, `[[tertiary]]`, `[[rare]]`. Complex logic uses Kotlin files in `content/drops/tables/monsters/`.

### Player Bot System (from 233 source, needs porting)
The 233 source has a complete in-server bot system:
- `content/other/progressive-bots/` — BotManager, BotConfig (135 bots), BotPersonality (6 archetypes), BotPlanner
- `content/other/agent-bridge/` — PlayerBotService (spawnBot/despawnBot/findBot), AgentBridgeServer (WebSocket), banking/shops/pathfinding/prayer/ground items porcelain

Bots are real `Player` objects with `NoopClient` — visible to all online players. The tick-based decision loop evaluates state every 25 ticks and picks actions per personality archetype.

### PluginScript Lifecycle
```kotlin
class MyModule : PluginScript() {
    override fun ScriptContext.startup() {
        // Register event handlers here
        onOpNpc1("npc.name") { ... }
        onPlayerLogin { ... }
        eventBus.subscribeUnbound(GameLifecycle.LateCycle::class.java) { onTick() }
    }
}
```

---

## 238→239 Carry-Over Inventory (What Needs Porting)

### What Already Exists (Skip Porting — 60%+ of 238 work)

| System | Details |
|:-------|:--------|
| **Interfaces** | 14 modules — bank, gameframe, combat, equipment, prayer, spellbook, settings, emotes, skill-guides, worldmap, journal, logout, menu, fade-overlay |
| **Skills** | 10 — cooking, firemaking, fishing, herblore, magic, prayer, runecrafting, slayer, smithing, woodcutting |
| **Drops** | 1,100 TOML drop tables covering most monsters |
| **Combat** | Formulas, scripts (PvN/NvP/PvP), weapon system, special attacks, spells, autocast |
| **Death/respawn** | NpcDeath, PlayerDeath with drops, skull, UIM handling |
| **Shops** | Buy/sell/restock with standard GP operations |
| **Generic LOC handlers** | Doors, ladders, staircases, gates, bank booths, bookcases, signposts, chicken coops, pickables, search |
| **Generic NPC handlers** | Banker, cows, ducks, sheep, persons |
| **Quest framework** | QuestScript base class, QuestJournal, QuestLineRegistry, attribute system |
| **Quest: Cook's Assistant** | Fully implemented |
| **Poison/venom/disease** | `api/mechanics/toxins/` |
| **Ranged ammo** | `api/combat/combat-commons/ranged/` |
| **Login/gameframe/mapclock** | Complete |
| **Travel** | Canoe system |
| **Windmill** | Working |

### What Needs Porting from 233 Source

**Priority P0 — Must Have (Server Infrastructure)**

| # | Module | Source Path | Approach | Dependencies |
|:-:|:-------|:-----------|:---------|:-------------|
| 1 | **AgentBridge** | `233/content/other/agent-bridge/` (26 files) | Port as `content/other/agent-bridge/` module | PlayerBotService, WebSocket server, banking/shops/pathfinding/prayer/ground items porcelain |

**Priority P1 — Must Have (Gameplay Systems)**

| # | Module | Source Path | Approach | Dependencies |
|:-:|:-------|:-----------|:---------|:-------------|
| 2 | **PlayerBotService** | `233/content/other/agent-bridge/PlayerBotService.kt` | Core bot spawning infrastructure — `spawnBot()`, `despawnBot()`, `findBot()`, `botCount()` | PlayerList, NoopClient, InvMapInit |
| 3 | **Progressive Bots** | `233/content/other/progressive-bots/` (4 files) | BotManager, BotConfig (135 bots), BotPersonality (6 archetypes), BotPlanner | PlayerBotService |
| 4 | **Trade (player-to-player)** | `233/content/mechanics/trade/` | Port as `content/mechanics/trade/` | Standard inventory API |
| 5 | **Aggression (NPC)** | `233/content/mechanics/aggression/` | Port as `content/mechanics/aggression/` | Combat extensions |
| 6 | **Consumables (food/healing)** | `233/content/other/consumables/` | Port as `content/other/consumables/` | Item type checks, healing |

**Priority P2 — Content Implementation**

| # | Module | Source Path | Approach | Dependencies |
|:-:|:-------|:-----------|:---------|:-------------|
| 7 | **Lumbridge NPCs** | OpenRune has partial (Bob, Hans, Bartender, etc.) | Fill in missing: Cook, Duke, tutors, Fred, Doomsayer, Perdu, etc. | Zone NPC pattern |
| 8 | **Draynor Village content** | 233 source + old 238 worklog reference | Re-implement as OpenRune TOML zone packs | Zone NPC pattern |
| 9 | **Quest: Restless Ghost** | 233 source (`content/quests/restless-ghost/`) | Re-implement using OpenRune's QuestScript | QuestScript base |
| 10 | **Quest: Vampyre Slayer** | 233 source (`content/quests/vampyre-slayer/`) | Re-implement using OpenRune's QuestScript | QuestScript base |
| 11 | **G2-G7 pipeline** | — (never materialized as executables) | **Retired** — replaced by osrs-mcp + corpus | N/A |

### What Does NOT Carry From 238
- Cache decoder patches (OpenRune's OR-Cache pipeline is fundamentally different)
- Identity hash batch patches (different cache, different hashes)
- Hand-written Kotlin spawn content (OpenRune uses TOML packs from cache)
- Old server configs (fresh game.yml)
- EventBus collision fixes (OpenRune EventBus architecture differs)

---

## Task Priority Matrix

```
P0 ┌─────────────────────────────────────────────────────────────────┐
   │                         AgentBridge                              │
   │  (WebSocket bridge for LLM agent control — unlocks player bots)  │
   ├─────────────────────────────────────────────────────────────────┤
P1 │  PlayerBotService  │  Progressive Bots  │  Trade  │  Aggression  │
   │  (bot spawning)    │  (135 bots)        │  (P2P)  │  (NPC aggro) │
   │                    │                    │         │              │
   ├─────────────────────────────────────────────────────────────────┤
P2 │  Consumables  │  Lumbridge NPCs  │  Draynor  │  Restless Ghost  │
   │  (food/heal)  │  (fill gaps)     │  (zone)   │  + Vampyre       │
   └─────────────────────────────────────────────────────────────────┘
```

### Dependencies
```
AgentBridge (P0)
  └── PlayerBotService (P1) → spawnBot infrastructure
        └── Progressive Bots (P1) → autonomous bot AI

Trade (P1)
Aggression (P1)
Consumables (P2)

Lumbridge NPCs (P2)
Draynor zone (P2)

Quest: Restless Ghost (P2) → depends on QuestScript framework (already in OR)
Quest: Vampyre Slayer (P2) → depends on QuestScript framework (already in OR)
```

### Server Boot State
- 33 plugin modules loaded
- 202 scripts loaded
- 170,666 static zones
- `CentralServer` auto-started on port 8080
- Embedded PostgreSQL auto-started
- Port 43594 bound and listening

### RSProx Config
- Proxy target: `OpenRune 239 Dev` (RuneLite mode only)
- `jav_config_url`: `http://192.168.0.187/jav_config-239.ws`
- `worldlist`: served from Caddy at `/worldlist-239.ws`
- `revision: "239"`, `varp_count: 15000`

---

## Repository House Rules

The repo root must stay clean at all times:

| What | Where |
|:-----|:------|
| ✅ **Canonical docs** | Repo root (`worklog-239.md` only) |
| ✅ **Scripts & tools** | `tools/<name>/` or `scripts/` |
| ✅ **Temp scripts** | `/tmp/` — never commit |
| ✅ **Draft docs** | `docs/` subdirectory |
| ❌ **Random .py/.sh** | Not repo root — belongs in tools/ or /tmp |
| ❌ **Scratch output** | Not repo root — use /tmp or build/ |

**Rule:** Temp → `/tmp/`. Reusable → `tools/`. Canonical → root. Nothing else.

---

## Commit Convention

### Workflow

```bash
git add <specific files>   # Never `git add -A` — be surgical
git commit -m "type(scope): description"
git push joshhmann main
```

**Direct pushes to `joshhmann/main`.** No PRs. No branches. Our `main` is the truth.

### Commit Message Format

```
<type>(<scope>): <description>
```

| Type | When |
|:-----|:-----|
| `feat` | New content (NPC, quest, zone, skill, mechanic) |
| `fix` | Bug fix, behavior correction |
| `docs` | Worklog updates, documentation |
| `refactor` | Code restructuring, no behavior change |
| `chore` | Build config, dependencies, tooling |

Examples:
```
feat(lumbridge): add Cook NPC handler with dialogue
feat(quest): implement Restless Ghost with attribute-based state
fix(combat): correct ranged accuracy formula for bronze arrows
docs(worklog): checkpoint after AgentBridge port
chore(tools): move corpus to tools/data/corpus/
```

### Commit Discipline

| Do | Don't |
|:---|:------|
| ✅ One logical change per commit | ❌ `git add -A` dumping everything |
| ✅ Small, reversible commits | ❌ 50-file mega-commits |
| ✅ Commit + push immediately after verifying | ❌ Sitting on uncommitted changes |
| ✅ `feat` for player-visible changes | ❌ Mixing `feat` and `fix` in one commit |
| ✅ `docs` for worklog updates | ❌ Leaving worklog stale after work |
| ✅ `chore` for tooling/config | ❌ Committing temp/scratch files |

### When to Commit

```
1. Implement → verify → git add <specific files>
2. Commit with proper message
3. git push joshhmann main
4. Update worklog-239.md if significant progress
```

**Small units preferred.** A new NPC handler = one commit. A quest = a few commits (setup, dialogue, completion). A mechanic port = one commit per module.

---

## Useful Commands
```bash
# Build + boot
cd /root/Runescape/open_rune/OpenRune-Server
./gradlew :server:app:run

# Quick build (no tests)
./gradlew assemble -x test

# Fresh cache install (one-time, downloads from OpenRS2)
./gradlew :or-cache:freshCache

# Run MCP server for cache lookups
./gradlew :tools:osrs-mcp:runMcp

# Compile a specific module
./gradlew :content:areas:city:<zone>:compileKotlin

# Kill old server
kill -9 $(lsof -ti :43594)

# Check server live
lsof -i :43594 | grep LISTEN

# Git push
git push joshhmann main
```

---

## Corpus Usage Guide

The corpus at `tools/data/corpus/` is the definitive source of truth for OSRS content data.

### Quick Start

```python
# Load from command line or scripts
cd /root/Runescape/open_rune/OpenRune-Server/tools/data/corpus
python3 -c "
from osrs_data import OSRS, db

# Query items
print(db.item(name='Abyssal whip'))        # by name
print(db.item(id=4151))                     # by ID

# Query NPCs/monsters
print(db.npc(name='Cow'))                   # NPC definition
print(db.monster(name='Abyssal demon'))     # monster combat stats

# Quest data
print(db.quest("Cook's Assistant"))         # quest details
print(db.quest_deps["Dragon Slayer II"])    # prerequisites

# Drops
print(db.drops(source='Abyssal demon'))     # full drop table
print(db.drops_for_item(item=4151))         # which monsters drop this item

# Progression / ironman guide
print(db.progression_phase("Dragon Slayer II"))  # optimal quest order phase

# Skill requirements
print(db.skill_reqs(level=60, skill='mining'))   # quests gated on 60 mining

# Reverse dependencies
print(db.reverse_deps["Cook's Assistant"])       # what unlocks after this quest
]
```

### What's in Each Directory

| Directory | Contents | When to Use |
|:----------|:---------|:------------|
| `quests/` | 217 quests with text, NPCs, items, rewards, stages | Quest implementation, dialogue writing |
| `items/` | 11,872 items with stats, examine, trade info | Drop tables, shops, equipment stats |
| `npcs/` or `parsed-data/` | 3,906 NPC definitions | NPC dialogue, combat stats, spawn locations |
| `skills/` | Skill training data (XP rates, resources) | Skill implementation, training methods |
| `drops/` or `buckets/` | 2,061 drop sources with weighted tables | Drop table generation |
| `ironman-guide/` | Optimal quest order, progression phases | Content roadmap ordering |
| `cache-diffs/` | Cache revision changes | Understanding what changed between revisions |
| `dependencies/` | Quest prerequisites, skill requirements | Quest gating, requirements |

### When to Use Corpus vs osrs-mcp

| Need This | Use |
|:----------|:----|
| A single NPC's exact name for a `find()` call | **osrs-mcp** CacheTool |
| All 217 quests with their full text | **corpus** batch load |
| What drops a specific item | **corpus** `drops_for_item()` |
| A monster's combat stats | **corpus** `monster()` |
| The exact cache ID for an item | **osrs-mcp** CacheTool |
| The optimal quest order | **corpus** `progression_phase()` |
| Quest prerequisites | **corpus** `quest_deps` |
| A single cache lookup during dev | **osrs-mcp** (no Python needed) |

---

## osrs-mcp Usage Guide

The MCP server provides cache/gameval/wiki lookups via stdio protocol. It connects to IDEs or CLI tools.

### Start the Server

```bash
cd /root/Runescape/open_rune/OpenRune-Server
./gradlew :tools:osrs-mcp:runMcp
```

This starts a stdio MCP server. Connect it to Cursor/VS Code/Claude for interactive lookups.

### Available Tools

The server registers these MCP tools:

- **CacheTool** — Look up cache definitions by name or ID
  - Query NPC types, item definitions, object types, animation IDs, etc.
- **GameValTool** — Look up game value constants
  - Find the right string reference for any entity (`npc.xxx`, `obj.xxx`, `component.xxx`)
- **WikiTool** — Scrape OSRS wiki pages on demand
  - Fetch quest guides, monster pages, item details from the live wiki

### Quick Cache Lookup Pattern

Look up the correct string reference for any entity you're implementing:

```kotlin
// Need to know "npc.xxx" for an NPC?
// → Use CacheTool to find the name in the cache

// Need to know "obj.xxx" for an item?
// → Use CacheTool to find the item definition
```

---

## Entity Reference Naming Conventions

OpenRune uses consistent naming patterns across string references. Following these conventions helps find the right name quickly.

### NPC Names

General pattern: `npc.<name_in_snake_case>`

```
npc.cook              — The Lumbridge Castle cook
npc.cow               — Cow
npc.bob               — Bob (axe shop in Lumbridge)
npc.hans              — Hans (castle gardener)
npc.bartender         — Lumbridge bartender
npc.barfy_bill        — Canoe station NPC
npc.donie_and_gee     — Quest advice NPCs
npc.arthur_the_clue_hunter — Clue scroll tutor
npc.hatius_cosaintus  — Achievement diary NPC
```

**Finding the name:** Use corpus or osrs-mcp to search by partial name. OSRS wiki names often differ from cache names (e.g. "King Black Dragon" might be `npc.king_dragon` without "black").

### Item Names

General pattern: `obj.<name_in_snake_case>`

```
obj.cake              — Cake
obj.bucket_of_milk    — Bucket of milk
obj.pot_of_flour      — Pot of flour
obj.coins             — Coins
obj.bronze_sword      — Bronze sword
obj.cow_hide          — Cowhide
obj.raw_beef          — Raw beef
```

### Skill Constants

```
stat.cooking          — Cooking skill
stat.fishing          — Fishing
stat.woodcutting      — Woodcutting
stat.mining           — Mining
stat.smithing         — Smithing
stat.firemaking       — Firemaking
stat.herblore         — Herblore
stat.magic            — Magic
stat.prayer           — Prayer
stat.runecrafting     — Runecrafting
stat.slayer           — Slayer
```

### Dialogue Mood Annotations

When writing NPC dialogue, annotate the emotional tone:

```kotlin
chatNpc(happy, "text")      — Happy/cheerful
chatNpc(sad, "text")        — Sad/depressed
chatNpc(worried, "text")    — Worried/nervous
chatNpc(angry, "text")      — Angry/frustrated
chatNpc(confused, "text")   - Confused/puzzled
chatNpc(laugh, "text")      — Laughing/amused
chatNpc(bored, "text")      — Bored/uninterested
chatNpc(short, "text")      — Short/curt
chatNpc(shifty, "text")     — Shifty/suspicious
chatNpc(verymad, "text")     — Very angry
chatNpc(quiz, "text")       — Quizzical/questioning

// Player moods follow same patterns
chatPlayer(happy, "text")
chatPlayer(sad, "text")
chatPlayer(angry, "text")
```

---

## Game Mechanics Research Workflow

When implementing a game mechanic (skill, combat formula, quest mechanic), follow this investigation chain:

### Step 1: Check What Already Exists

Look in OpenRune's source for existing implementations:

```bash
# Search for relevant files
grep -rl "heal\|food\|consume" content/ api/ --include='*.kt' | grep -v build
```

### Step 2: Research with Corpus

Load the relevant data from corpus:

```python
from osrs_data import OSRS, db
# e.g., check what food items exist and their heal amounts
db.item(name="shark")  # Check all item properties
```

### Step 3: Research the OSRS Wiki

For mechanics that need reference implementation:
- osrs-mcp WikiTool can fetch specific wiki pages
- Or manually reference: https://oldschool.runescape.wiki/

### Step 4: Check Reference Sources

| Source | What It Has | Path |
|:-------|:------------|:-----|
| **233 source** | Free-to-play quests, mechanics, NPC handlers | `/root/Runescape/sources/osrs-ps-dev-source/rsmod/content/` |
| **Kronos rev 184** | Early OSRS content (simpler implementations) | `/root/Runescape/sources/Kronos-184-Fixed/` |
| **Tarnish rev 218** | Mid-era reference | `/root/Runescape/sources/tarnish/` |
| **238 worklog** | Cache decoder fixes, porting patterns | `/root/Runescape/current_development/rsmod-238/worklog-238.md` |
| **Old bot docs** | Player bot patterns, QA scenarios | `docs/qa/` in old rsmod-238 |

### Step 5: Port/Implement Following OpenRune Patterns

Write the implementation using OpenRune's string-based reference system and module structure.

---

## Reference Sources Inventory

### Primary Source (Our 233 Base)

| What | Path | Use |
|:-----|:-----|:----|
| Full rsmod rev 233 codebase | `/root/Runescape/sources/osrs-ps-dev-source/rsmod/` | Reference for ALL content we port |
| F2P quest implementations | `.../content/quests/` (20 quests) | Port quest logic, adapt to OpenRune's QuestScript |
| Progressive bot system | `.../content/other/progressive-bots/` | Port autonomous bot AI |
| AgentBridge system | `.../content/other/agent-bridge/` (26 files) | Port WebSocket bridge for LLM agents |
| Mechanics (trade/aggression/poison/ranged/etc) | `.../content/mechanics/` | Port gameplay systems |
| Additional skills (agility, crafting, construction, etc.) | `.../content/skills/` | Reference for future skill implementations |
| Bot docs | `.../docs/bot-patterns/` | TypeScript bot interaction patterns |
| QA scenarios | `.../docs/qa/playerbot-scenarios/` | Bot testing scripts |

### Secondary Sources

| Source | Path | Best For |
|:-------|:-----|:---------|
| Kronos rev 184 | `/root/Runescape/sources/Kronos-184-Fixed/` | Early content patterns, simpler code |
| Old 238 worklog | `/root/Runescape/current_development/rsmod-238/worklog-238.md` | Cache decoder patterns (NOT portable) |
| Old 238 source | `/root/Runescape/current_development/rsmod-238/` | Carry-over content patterns |
| OSRS Corpus | `tools/data/corpus/` | All content data |
| OSRS Wiki | `https://oldschool.runescape.wiki/` | Ground truth for game mechanics |
| Optimal Quest Guide | `https://oldschool.runescape.wiki/w/Optimal_quest_guide` | Content ordering |

---

## Content Creation Quick Reference

### Adding a New NPC Handler

1. Create `content/areas/city/<zone>/npcs/<NpcName>.kt`
2. Register interaction in the zone script or directly in the NPC handler
3. Use string references: `"npc.<name>"`, `"obj.<item>"`, `"component.<interface>:<widget>"`
4. Compile: `./gradlew :content:areas:city:<zone>:compileKotlin`

```kotlin
class MyNpc : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.my_npc") {
            startDialogue(it.npc) { mainDialogue(it.npc) }
        }
    }

    private suspend fun Dialogue.mainDialogue(npc: Npc) {
        chatNpc(happy, "Hello there!")
        when (choice2("Who are you?", 1, "Goodbye.", 2)) {
            1 -> {
                chatPlayer(quiz, "Who are you?")
                chatNpc(laugh, "I'm just a humble merchant.")
            }
            2 -> chatPlayer(happy, "Goodbye.")
        }
    }
}
```

### Adding Drops

Add a TOML file to `content/drops/src/main/resources/drops/tables/monsters/`:

```toml
id = "My Monster Drops"
npcs = ["npc.my_monster"]

[[guaranteed]]
obj = "obj.bones"
count = 1

[[main]]
numerator = 1
denominator = 4
obj = "obj.coins"
count = 10

[[tertiary]]
numerator = 1
denominator = 128
obj = "obj.trail_clue_beginner"
count = 1
```

### Creating a Quest

```kotlin
class MyQuest : QuestScript("quest_myquest", "varp.myquest", rewards {
    xp("stat.cooking", 500.0)
    item("obj.coins", 100)
}, ItemRewardDisplay("obj.some_item")) {

    private val ITEM_GIVEN = quest.attribute(name = "ITEM_GIVEN", default = false)

    override fun ScriptContext.init() {
        onOpNpc1("npc.some_npc") { startDialogue(it.npc) { ... } }
    }

    override fun subTitle() = "helping the <col=800000>Some NPC</col> in <col=800000>Lumbridge</col>."
    override fun questLog(player: ProtectedAccess) = questJournal(player) { ... }
    override fun completedLog(player: ProtectedAccess) = completionJournal(player) { ... }
}
```

---

## Optimal Quest Order (F2P Priority)

Follow the OSRS optimal quest guide for content ordering priority:

https://oldschool.runescape.wiki/w/Optimal_quest_guide

**F2P quests in recommended order:** (these are our implementation priority)

1. Cook's Assistant ✅ (already in OpenRune)
2. Sheep Shearer
3. X Marks the Spot
4. Misthalin Mystery
5. The Corsair Curse
6. Romeo & Juliet
7. Ernest the Chicken
8. Vampyre Slayer (P2 carry-over)
9. Doric's Quest
10. Witch's Potion
11. Imp Catcher
12. The Restless Ghost (P2 carry-over)
13. Knight's Sword
14. Goblin Diplomacy
15. Prince Ali Rescue
16. Demon Slayer
17. Shield of Arrav
18. Rune Mysteries
19. Pirate's Treasure
20. Dragon Slayer
21. Black Knights' Fortress

### Known Patterns from 233 Source

The 233 source has F2P quest implementations that can be used as reference for porting to OpenRune's QuestScript framework:

```bash
# List all quest modules in 233 source
ls /root/Runescape/sources/osrs-ps-dev-source/rsmod/content/quests/
```

Each quest follows the rsmod pattern (extends PluginScript, uses onOpNpc1, dialogue functions). These patterns are directly portable to OpenRune's QuestScript by adapting the class to extend QuestScript instead.

---

## Key Development Workflows

### Adding a New Content Area Module

```bash
# Create the module directory
mkdir -p content/areas/city/<zone>/src/main/kotlin/org/rsmod/content/areas/city/<zone>/npcs

# Create build.gradle.kts
cat > content/areas/city/<zone>/build.gradle.kts << 'EOF'
plugins { id("base-conventions") }
dependencies {
    implementation(projects.api.pluginCommons)
}
EOF
```

The module auto-discovers via settings.gradle.kts — no manual registration needed.

### Porting a Quest from 233 to OpenRune

1. Read the 233 source implementation at `/root/Runescape/sources/osrs-ps-dev-source/rsmod/content/quests/<quest-name>/`
2. Understand the quest flow: NPC interactions → item delivery → state progression → completion
3. Create an OpenRune QuestScript following the Cook's Assistant pattern
4. Map entity references from `find("name")` to `"npc.name"` / `"obj.name"` strings
5. Replace state management with `quest.attribute()` for fine-grained progress
6. Replace dialogue with OpenRune's `chatPlayer()`/`chatNpc()`/`choice2()`/`choice4()` pattern
7. Use `quest.advanceQuestStage(access)` instead of manual varbit manipulation
8. Compile: `./gradlew :content:quest:compileKotlin`

### Verifying String References

When writing OpenRune code with string references, verify the reference exists:

```bash
# Check if an NPC name exists in cache
grep "npc\.cook" .data/raw-cache/npc/*.toml    # Check TOML dumps
# Or use osrs-mcp for a proper cache lookup
./gradlew :tools:osrs-mcp:runMcp               # Interactively query
```

If a string reference doesn't resolve at runtime, the server will log a warning during cache load without crashing — check boot logs.

---

## Complete Content Development Workflow

### The Four-Layer Architecture

Every piece of content follows a 4-layer stack. Understanding which layer to touch is critical:

```
Layer 4: Content Modules (content/)
  NPC handlers, zone scripts, dialogue, quests, skills, drops
  → What you write when adding game content

Layer 3: Plugin System (api/script/)
  PluginScript, onOpNpc1(), onOpLocU(), onPlayerQueueWithArgs
  GameLifecycle events (Startup, LateCycle, EndCycle)
  → How code responds to player actions

Layer 2: Infrastructure (api/*.kt)
  Combat formulas, death mechanics, NPC AI hunt/wander modes,
  toxins (poison/venom), shops, player state
  → Engine-level systems that content builds on

Layer 1: Cache Data (or-cache → api/generated/)
  309 auto-generated files from OSRS cache DB tables
  CookingFoodsRow, FishingDataRow, FiremakingLogRow, etc.
  → NEVER edit manually — regenerated by freshCache
```

### NPC Workflow

```
1. FIND NPC NAME → osrs-mcp CacheTool or corpus db.npc(name="Name")
2. CREATE HANDLER → content/areas/city/<zone>/npcs/<NpcName>.kt
3. REGISTER → onOpNpc1("npc.name") { } in ScriptContext.startup()
4. DIALOGUE → chatNpc(happy, "text") | chatPlayer(sad, "text")
5. BRANCH → choice2("Option", 1, "Option", 2) | choice4(...)
6. COMPILE → ./gradlew :content:areas:city:<zone>:compileKotlin
```

### Quest Workflow

```
1. CHECK EXISTING → Only Cook's Assistant implemented in OpenRune
2. FIND SOURCE → 20 F2P quests at 233 source: content/quests/<name>/
3. CREATE CLASS → QuestScript(key, varp, rewards { }, ItemRewardDisplay)
4. DEFINE STATE → quest.attribute(name, default) for fine-grained flags
5. WRITE JOURNAL → questLog() + completedLog() using questJournal {} DSL
6. REGISTER NPCs → onOpNpc1("npc.name") in ScriptContext.init()
7. PROGRESS → quest.advanceQuestStage(access) — auto-increments varps
8. COMPILE → ./gradlew :content:quest:compileKotlin
```

### Skill Workflow

```
1. CHECK DATA → Does the cache have a DB table? (check api/generated/ for *_Row.kt)
2. IF EXISTS → Data is auto-generated, work with Row classes (CookingFoodsRow.all())
3. CREATE MODULE → content/skills/<name>/ with build.gradle.kts
4. DEFINITIONS → Thin accessor file (CookingDefinitions.kt adds .raw, .cooked, .level)
5. EVENTS → PluginScript subscribing to onOpLocU (item on object), onPlayerQueueWithArgs (queued action)
6. UI → openSkillMulti() for multi-item selection
7. COMPILE → ./gradlew :content:skills:<name>:compileKotlin
```

*Skills already in OpenRune: cooking, firemaking, fishing, herblore, magic, prayer, runecrafting, slayer, smithing, woodcutting*

### Drops Workflow

```
1. SIMPLE DROPS → Add TOML file to content/drops/src/main/resources/drops/tables/monsters/
2. COMPLEX DROPS → Create Kotlin table using dropTable.register("npc.name") { }
3. REFERENCE → npcs = ["npc.cow"], obj = "obj.cow_hide" (string refs always)
4. SECTIONS → [[guaranteed]], [[main]], [[tertiary]] for different roll types
```

### Mechanics Workflow (Trade, Aggression, Consumables)

```
1. CHECK EXISTS → Poison ✅, Ranged ✅, Aggression partial — rest needs porting
2. FIND SOURCE → 233 source at content/mechanics/<name>/ or content/other/<name>/
3. CREATE MODULE → content/mechanics/<name>/ with build.gradle.kts
4. PORT → Copy .kt files, replace find("name") → "name" string literal
5. COMPILE → ./gradlew :content:mechanics:<name>:compileKotlin
```

### Status Effects & Timers

```
- Use GameLifecycle.LateCycle subscription for tick-based effects
- PlayerTimerProcessor / NpcTimerProcessor handle timed events
- Existing: toxins (poison/venom/disease) at api/mechanics/toxins/
- New effects: subscribe to LateCycle, check state, apply per-tick
```

### Porting Mapping (Old rsmod → OpenRune)

| Old Pattern | New Pattern |
|-------------|-------------|
| `find("name")` | `"name"` (bare string literal) |
| `onOpNpc1(find("x"))` | `onOpNpc1("npc.x")` |
| `player.inv.count(id)` | `player.inv.count("obj.name")` |
| `access.invDel(inv, id)` | `access.invDel(access.inv, "obj.name")` |
| `PluginScript`, `Player`, `Npc` | **Same types** |
| `GameLifecycle.LateCycle` | **Same** |
| Manual varbit incr | `quest.advanceQuestStage(access)` |

---

## Session: June 29, 2026 — Milestone 0 Stabilization (Quest Module Fix)

### What was done

**Compile blocker fixed:** `RuneMysteries.kt` had a `startup()` override that:
1. Tried to access `player.questState` which is `private` in `QuestScript` — compile error
2. Overrode `QuestScript.startup()` entirely, preventing journal UI handlers from registering

**Deeper fix — Shared handler registration refactor:**
The `QuestScript` base class registered `onIfOverlayButton("component.questlist:list")` and all journal modal button handlers inside every subclass's `startup()` — which worked with 1 quest but crashed the server at boot with >1 quest (duplicate event registration).

Refactored `QuestScript`:
- Added companion object `instances` registry (questKey → QuestScript)
- Added `ACTIVE_JOURNAL_QUEST` player attribute to track which quest journal is open
- Extracted all UI button registrations into `registerSharedUIHandlers()` — called exactly once
- Dispatches overlay/modal clicks to the correct QuestScript instance via companion lookup

**Server state:**
- Quest module compiles ✅
- Full server compiles ✅  
- Server boots cleanly: 55 progressive bots, 0 errors ✅
- Pushed to `joshhmann/main` as `267405c4`

### Files changed
| File | Change |
|------|--------|
| `content/quest/.../RuneMysteries.kt` | Removed `startup()` override — parent handles login sync |
| `content/quest/manager/QuestScript.kt` | Single registration for shared UI handlers + per-player attribute dispatch |
|