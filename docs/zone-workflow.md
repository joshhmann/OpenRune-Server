# Zone Content Workflow — Template

**Purpose:** Repeatable process for making an OSRS zone feel feature-complete.
**Derived from:** Lumbridge (Phase 1 — template run)
**Target:** OpenRune 239.1

---

## 3-Tier NPC Handling Methodology

| Tier | Approach | When | Example |
|:----:|----------|:----:|:--------|
| **1 — Major** | Dedicated `.kt` file per NPC | Quest givers, shopkeepers, unique characters with branching dialogue | Duke Horacio, Father Aereck, Hans |
| **2 — Minor** | Batch `.kt` file grouping 5-15 NPCs | Simple talk-to NPCs that share a pattern (farmers, guards, assistants) | `LumbridgeMinorNpcs.kt` covers 15 NPCs |
| **3 — Generic** | Content-group-based handler | Ambient NPCs that share behavior across zones (cows, sheep, ducks, men, women) | `GenericPerson.kt` handles `content.person` group |

### Decision Flow
```
Is NPC needed for a quest?
  ├─ Yes → Tier 1 (dedicated file, quest-gated dialogue)
  └─ No → Does NPC have unique dialogue (shop, lore, hints)?
      ├─ Yes → Tier 1
      └─ No → Can NPC share dialogue with others of same type?
          ├─ Yes → Does NPC already have a content group?
          │   ├─ Yes → Tier 3 (generic handler already exists)
          │   └─ No → Tier 2 (batch handler)
          └─ No → Tier 1
```

---

## Layer Status — Lumbridge (Current Sprint)

| Layer | What | Status | Notes |
|:-----:|------|:------:|:------|
| **1** | **Audit** | 🟢 DONE | Full catalog via corpus data |
| **2** | **NPCs** | 🟢 DONE | 3-tier: 26 major + 15 batch + generic groups |
| **3** | **Shops** | 🟢 GOOD | General store, Bob's, bar — functional |
| **4** | **Interactables** | 🟡 NEEDS WORK | Generic locs OK; castle doors broken |
| **5** | **Monsters** | 🟡 NEEDS VERIFY | Cache-loaded, not manually tested |
| **6** | **Items** | 🟢 GOOD | 55 ground spawns from cache |
| **7** | **Skills** | 🟢 GOOD | Cooking range, trees, fish active |
| **8** | **Quests** | 🟡 PARTIAL | Only Cook's Assistant + Rune Mysteries started |
| **9** | **Template** | 🟡 IN PROGRESS | This doc |
| **10** | **Verify** | 🔴 NOT DONE | Need compile+restart+walkthrough |

## Evidence States

Use these states instead of vague done/not done claims:

| State | Meaning |
|-------|---------|
| **PASS** | Verified by compile/build/runtime evidence listed in the layer notes. |
| **PASS-COMPILE** | Code compiles, but player-facing runtime behavior still needs walkthrough. |
| **RUNTIME-REQUIRED** | Cannot be closed without in-game/client verification. |
| **DEFERRED** | Accepted known gap with an entry in `docs/deferred-items.md`. |
| **FAIL** | Tested and broken; blocks current milestone unless explicitly deferred. |
| **ASSUMED** | Source data suggests it should work, but no direct verification yet. Avoid leaving this state at milestone close. |

## Layer 0 — Evidence Packet (Mandatory)

Before Layer 1, create a compact evidence packet for the zone. This prevents us from trusting stale docs or old roadmap claims.

Required inputs:

1. **Corpus** — `tools/data/corpus/parsed-data/`
   - `npc_directory.json`: named NPCs, locations, quest ties, membership flags.
   - `shops.json`: shop names, owners, specialty, members flag.
   - `quest_lookup.json`: start points, requirements, required items, enemies, guide page.
   - `npc_spawns.json`: monster spawn `LocLine` coordinates from OSRS Wiki where available.
   - `item_by_name.json` / `monster_by_name.json`: item and combat stats details.
2. **Raw cache map data** — `.data/raw-cache/map/`
   - `npcs/<zone>.toml`: actual spawn symbols and coordinates.
   - `objs/<zone>.toml`: ground item symbols and coordinates.
   - `area/<zone>.toml` when present.
3. **Gamevals / cache lookup** — via `osrs-mcp` when symbol details are unclear:
   - `gameval_search`: resolve `npc.*`, `obj.*`, `loc.*`, `varp.*`, `varbit.*` names/ids.
   - `cache_search`: inspect decoded cache definitions for NPC stats, actions, item properties, varps/varbits.
   - `wiki_npc_spawns`: fetch wiki spawn coordinates and resolve infobox NPC ids to gamevals.
4. **Code coverage** — existing `.kt` handlers in the zone module and generic modules.
5. **Runtime evidence** — client walkthrough, bot/AgentBridge script, or explicit `RUNTIME-REQUIRED` flag.

Evidence packet format:

```yaml
zone: lumbridge
source_snapshot:
  corpus:
    npc_directory_matches: 142
    shops_matches: 7
    quest_lookup_entries: ["Cook's Assistant", "Rune Mysteries", "The Restless Ghost"]
  raw_cache:
    npc_files: [".data/raw-cache/map/npcs/lumbridge.toml", "..."]
    unique_npc_symbols: 112
    npc_spawns: 377
    ground_obj_spawns: 55
  code:
    zone_kt_files: 27
    direct_handler_symbols: 41
  build:
    module_compile: PASS
    full_compile: NOT_RUN
  runtime:
    walkthrough: RUNTIME-REQUIRED
```

## Layer Workflow (Ordered)

Each zone follows these layers in order. Do not skip ahead or move to the next zone until the current zone's layers are complete or explicitly deferred with evidence.

### Layer 1 — Audit
**Goal:** Know exactly what exists vs what OSRS says should be there.

1. Build Layer 0 evidence packet from corpus, raw-cache, gamevals/cache lookup, code coverage, and runtime status.
2. Query corpus for zone NPCs: start with `npc_directory.json`, then drill into `npc_by_name.json` for details.
3. Check actual NPC spawn files: `.data/raw-cache/map/npcs/<zone>.toml` and companion files such as `<zone>_underground.toml` / `<zone>_manor_forest.toml`.
4. Check ground item spawns: `.data/raw-cache/map/objs/<zone>.toml`.
5. Check OSRS monster spawn coordinates: `npc_spawns.json` or `osrs-mcp wiki_npc_spawns`.
6. Check shops: `shops.json` plus `.data/raw-cache/server/shops/*.toml`.
7. Check quest requirements: `quest_lookup.json` plus quest guide page if needed.
8. Cross-reference existing handlers: `content/areas/city/<zone>/src/main/kotlin/...`.
9. Check generic handlers: `content/generic/generic-npcs/` and `content/generic/generic-locs/`.
10. Classify each candidate by Tier 1 / Tier 2 / Tier 3 / combat-only / event-member-only.
11. Output: evidence packet + gap list with NPCs, shops, items, monsters, interactables, quests, and runtime requirements.

### Layer 2 — NPCs
**Goal:** Every interactive NPC in the zone has a dialogue handler.

**Tier 1 — Major:** Dedicated `.kt` file per NPC
- Quest givers, shopkeepers, unique characters
- Full dialogue trees with authentic OSRS text
- Include conversation branches (greet → options → responses)

**Tier 2 — Minor:** Batch `.kt` file for 5-15 similar NPCs
- Farmers, guards, assistants with simple dialogue
- Single `PluginScript` class with multiple `onOpNpc1` registrations
- One dialogue function per NPC type

**Tier 3 — Generic:** Content-group-based handler
- Already covered by handlers in `content/generic/generic-npcs/`
- Man/Woman → `content.person`, Cow → `content.cow`, etc.

**Tools:**
- Find symbol names: `grep <npc_name> .data/raw-cache/map/npcs/<zone>.toml`
- Check content groups: `grep -B1 "<npc_id>" .data/raw-cache/server/npcs.toml | grep contentGroup`
- Compile: `./gradlew :content:areas:city:<zone>:compileKotlin`

**Completion:** Every spawn entry in the zone's npcs TOML that represents a talkable NPC has a handler (direct or via content group). Combat-only NPCs (goblins, rats, etc.) excluded.

### Layer 3 — Shops
**Goal:** Every shop in the zone opens and has appropriate inventory.

- Verify shop TOML files at `.data/raw-cache/server/shops/`
- Check shop NPCs open trade dialogue
- Verify buy/sell/restock works
- Cross-reference shop inventory with OSRS wiki

### Layer 4 — Interactables
**Goal:** All clickable objects (locs) in the zone respond correctly.

**Generic (handled upstream):**
- Doors (single): `content.closed_single_door` / `content.opened_single_door`
- Gates: `content.closed_left_picketgate` / `content.closed_right_picketgate`
- Ladders: `content.ladder_up` / `content.ladder_down`
- Staircases: `content.spiralstaircase_*`
- Banks: `content.bank_booth`
- Bookcases: `content.bookcase`
- Signposts: `SignpostScript`
- Chicken coops: `content.chicken_coop`
- Pickables: `Pickables`
- Search: `SearchPlugin`

**Zone-specific:**
- Check each special loc in the zone (e.g. winch, log with axe in Lumbridge)
- Create `onOpLoc1` handlers for special zone locs
- Verify castle/unique building locs (doors, trapdoors, etc.)

**If a loc type is unknown:**
1. Check `loc.toml` for the loc's definition
2. Try `onOpLoc1("<loc_symbol>")` directly
3. If content group exists, verify it matches generic handlers

### Layer 5 — Monsters
**Goal:** Combat NPCs are present, attackable, and have appropriate stats.

- Most combat NPCs loaded from cache — verify they appear in world
- Check cache definitions have attack/defence/hitpoints params
- Verify aggression works for aggressive NPCs
- Verify drops work (handled separately by drop tables)

**Note:** Monster spawns are encoded in the cache landscape data. As long as the cache decoder works, spawns are correct. Manual verification requires in-game testing.

### Layer 6 — Items
**Goal:** Ground item spawns are visible and pickable.

- Item spawns defined in `.data/raw-cache/map/objs/<zone>.toml`
- Loaded at server boot from cache landscape decoder
- Verify pick-up works and items appear on ground
- Verify respawn timers work

**Note:** Like monsters, items are encoded in cache. Manual verification only.

### Layer 7 — Skills
**Goal:** All skills that can be used in the zone work correctly.

- Trees → woodcutting
- Fish spots → fishing
- Ranges/fires → cooking
- Rocks → mining
- Furnaces/anvils → smithing
- Altars → prayer

**Verification:** Skill modules are loaded at server boot. Test in-game.

### Layer 8 — Quests
**Goal:** Every quest that starts in or passes through the zone is implementable.

- Implement quest script using `QuestScript` pattern
- Wire NPC dialogue to check quest state:
  ```kotlin
  when {
      quest.isQuestCompleted(player) -> afterQuest(npc)
      quest.questState(player) == QuestProgressState.IN_PROGRESS -> duringQuest(npc)
      else -> preQuest(npc)
  }
  ```
- Register quest varp to track progress
- Verify quest journal updates

### Layer 9 — Template
**Goal:** The zone's workflow is documented for reuse.

- Fill in layer status table
- Note any zone-specific quirks or patterns
- Document tools/commands used
- Update this document with lessons learned

### Layer 10 — Verify
**Goal:** Every interactable element has been tested in-game.

1. Compile zone module: `./gradlew :content:areas:city:<zone>:compileKotlin`
2. Restart server: `fuser -k 43594/tcp` then `./gradlew :server:app:run --console=plain`
3. Walk through the zone and click every:
   - NPC (talk-to, trade if applicable)
   - Interactable loc (doors, gates, ladders, etc.)
   - Monster (attack confirmation)
   - Ground item (pick up)
4. Verify quest progression if applicable
5. Log any failures
6. Fix failures → recompile → restart → retest

### Legend
🟢 GOOD = no action needed | 🟡 PARTIAL = some work done, more needed | 🟡 IN PROGRESS = actively being worked | 🔴 NOT STARTED = hasn't been touched

---

## Audit

### Tools
- **Corpus:** `tools/data/corpus/parsed-data/npc_directory.json` — named NPCs by location, quest ties, membership flags.
- **Corpus:** `tools/data/corpus/parsed-data/npc_by_name.json` — detailed NPC lookup by name.
- **Corpus:** `tools/data/corpus/parsed-data/npc_spawns.json` — OSRS Wiki monster LocLine coordinates.
- **Corpus:** `tools/data/corpus/parsed-data/shops.json` — shop owner/specialty/membership metadata.
- **Corpus:** `tools/data/corpus/parsed-data/quest_lookup.json` — quest requirements, start points, items, enemies, guide page.
- **Corpus:** `tools/data/corpus/parsed-data/item_by_name.json` — item requirements and IDs.
- **Raw cache:** `.data/raw-cache/map/npcs/<zone>.toml` — actual NPC spawn symbols/coordinates.
- **Raw cache:** `.data/raw-cache/map/objs/<zone>.toml` — actual ground item spawn symbols/coordinates.
- **Raw cache:** `.data/raw-cache/server/{npcs,loc,obj,varp,varbit}.toml` — server-side type data.
- **osrs-mcp:** `wiki_npc_spawns` — wiki spawn coords + infobox NPC ids resolved through gamevals.
- **osrs-mcp:** `gameval_search` — exact `npc.*`, `obj.*`, `loc.*`, `varp.*`, `varbit.*` symbol resolution.
- **osrs-mcp:** `cache_search` — decoded LIVE/SERVER cache details such as NPC actions/combat/hp and item properties.

### What to capture
- NPCs in zone (name, location, F2P/P2P, quest involvement)
- Existing handlers vs missing handlers
- Shops needed
- Interactable locs (anvils, ranges, doors, etc.)
- Monster spawns
- Ground item spawns

### Gap format
```yaml
npc_name:
  tier: 1/2/3          # 1 = immediate player encounter
  location: "description"
  f2p: true/false
  quest: "quest_name or null"
  raw_spawn_symbol: "npc.<symbol> or null"
  raw_spawn_count: 0
  corpus_ids: ["815"]
  handler_exists: true/false
  generic_content_group: "content.<group> or null"
  runtime_test: required/not_required/done
  status: PASS/PASS-COMPILE/RUNTIME-REQUIRED/DEFERRED/FAIL/ASSUMED
  notes: "dialogue complexity, quest gating, shop, combat-only, event/member-only, etc."
```

### Classification rules

- **Tier 1:** Quest NPCs, shopkeepers, tutorial/service NPCs, unique named NPCs with player-facing dialogue or actions.
- **Tier 2:** Minor named NPCs sharing simple dialogue or repeated behavior.
- **Tier 3:** Generic content-group NPCs/locs covered by shared modules (`content.person`, `content.cow`, doors, gates, ladders, etc.).
- **Combat-only:** Monsters/animals that need spawn/combat/drop verification, not dialogue handlers.
- **Event/member-only:** Present in corpus but out of current F2P zone scope unless the raw cache spawn exists in our map file.

### Coverage rules

- Corpus `location contains zone` is broad. It finds quest/cutscene/member variants too. Filter by:
  1. `is_members == false` unless deliberately doing members content.
  2. raw cache spawn symbol exists in `.data/raw-cache/map/npcs/<zone>.toml` OR the NPC is quest-required for the zone.
  3. current milestone scope.
- Raw cache spawn exists but no direct handler is not automatically a gap. First check generic content groups and combat-only status.
- Shop coverage requires both NPC interaction and shop stock verification.
- Quest coverage requires quest lookup data, varp/varbit mapping, item requirements, NPC/loc/object symbols, and runtime stage walkthrough.

### Example research outputs from 2026-06-28

Lumbridge:
- Corpus `npc_directory` broad match: 142 entries.
- Corpus shops broad match: 7 entries; F2P practical shops include Bob's Brilliant Axes, Lumbridge General Store, and The Sheared Ram.
- Raw cache `npcs/lumbridge.toml`: 377 spawns, 112 unique NPC symbols.
- Raw cache `objs/lumbridge.toml`: 55 ground item spawns.
- Zone code: 27 Kotlin files; 41 direct registered NPC/LOC symbols found in Lumbridge handlers.

Draynor seed for next template run:
- Corpus `npc_directory` broad match: 53 entries.
- Corpus shops broad match: 8 entries; F2P practical shops include Diango's Toy Store, Wine Shop, Ned's rope shop, and Forestry Shop.
- Raw cache `npcs/draynor.toml`: 88 spawns, 58 unique NPC symbols.
- Raw cache `npcs/draynor_manor_forest.toml`: 41 spawns, 19 unique NPC symbols.
- Raw cache `objs/draynor.toml`: 7 ground item spawns, 6 unique object symbols.
- Raw cache `objs/draynor_manor_forest.toml`: 8 ground item spawns, 8 unique object symbols.
- Quest-critical F2P NPCs from corpus: Morgan (Vampyre Slayer), Veronica/Ernest/Professor Oddenstein (Ernest the Chicken), Aggie/Ned/Leela/Lady Keli/Joe/Prince Ali (Prince Ali Rescue), Diango, Fortunato, Wise Old Man, bankers.
- Quest lookup confirms: Vampyre Slayer needs hammer/beer/stake and Count Draynor combat; Ernest needs spade/fish food/poison; Prince Ali Rescue needs multi-item disguise/jail workflow.

---

## NPC Handler Creation

### Pattern
```kotlin
// content/areas/city/<zone>/npcs/<NpcName>.kt
class NpcName : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1("npc.<symbol_name>") { startDialogueWith(it.npc) }
    }

    private suspend fun ProtectedAccess.startDialogueWith(npc: Npc) {
        startDialogue(npc) { mainDialogue(npc) }
    }

    private suspend fun Dialogue.mainDialogue(npc: Npc) {
        // Simple: greet → goodbye
        // With quest: branch on quest state
        // With shop: trade option
    }
}
```

### How to find symbol names
1. Check the NPC's cache symbol in `.data/raw-cache/server/npcs.toml` or map spawn TOMLs like `.data/raw-cache/map/npcs/<zone>.toml`
2. Try the NPC's wiki name lowercased with underscores: `"npc.duke_horacio"`
3. Search the spawn files for known NPC names

### Worked Example — Duke Horacio

**Step 1: Find symbol name**
```bash
grep -i "horacio\|duke" .data/raw-cache/map/npcs/lumbridge.toml
# Result: npc = "npc.duke_of_lumbridge"
```

**Step 2: Create handler** — Write a `PluginScript` subclass with `onOpNpc1("npc.duke_of_lumbridge")`

**Step 3: Compile**
```bash
./gradlew :content:areas:city:lumbridge:compileKotlin
```

**Key observations:**
- Symbol name may differ from wiki name (`npc.duke_of_lumbridge` not `npc.duke_horacio`)
- Some NPCs have multiple cache IDs for different states/variants
- Handlers auto-register via `PluginScript` — no manual wiring needed
- Use `onOpNpc1` for Talk-to, `onOpNpc3` for other options

### Quest-gated dialogue pattern
```kotlin
when {
    quest.isQuestCompleted(player) -> afterQuest(npc)
    quest.questState(player) == QuestProgressState.IN_PROGRESS -> duringQuest(npc)
    else -> preQuest(npc)
}
```

---

## Registration

All NPC handlers are their own `PluginScript` class. They auto-register — just create the file and add `onOpNpc1()` in that class's `startup()`. No manual wiring needed.

---

## Compile & Verify

```bash
# Compile just the zone module (fast)
./gradlew :content:areas:city:<zone>:compileKotlin

# Full server build (slow — only after zone passes)
./gradlew :server:app:compileKotlin
```

### Runtime verification
- Boot server
- Walk to NPC location
- Click Talk-to → confirm dialogue opens
- Test all dialogue branches
- For quest NPCs: test pre-quest, in-progress, and post-completion states

---

## Commit

```bash
git add content/areas/city/<zone>/
git commit -m "feat(<zone>): add <npc> handler with dialogue"
git push joshhmann main
```
