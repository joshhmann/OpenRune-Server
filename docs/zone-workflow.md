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
| **8** | **Quests** | 🟡 INCOMPLETE | Only Cook's Assistant |
| **9** | **Template** | 🟡 IN PROGRESS | This doc |
| **10** | **Verify** | 🔴 NOT DONE | Need compile+restart+walkthrough |

## Layer Workflow (Ordered)

Each zone follows these layers in order. Do not skip ahead or move to the next zone until the current zone's layers are complete.

### Layer 1 — Audit
**Goal:** Know exactly what exists vs what OSRS says should be there.

1. Query corpus for all NPCs in zone: `tools/data/corpus/parsed-data/npc_by_name.json` filtered by location
2. Check NPC spawn files: `.data/raw-cache/map/npcs/<zone>.toml`
3. Check ground item spawns: `.data/raw-cache/map/objs/<zone>.toml`
4. Check monster spawns: `tools/data/corpus/parsed-data/npc_spawns.json`
5. Cross-reference existing handlers: `find content/areas/city/<zone>/ -name "*.kt"`
6. Check generic handlers: `content/generic/generic-npcs/`
7. Classify each NPC by tier (Major/Minor/Generic)
8. Output: Gap list with NPCs, shops, items, monsters, interactables

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
- **Corpus:** `tools/data/corpus/parsed-data/npc_by_name.json` — filter by `location` containing zone name
- **Corpus:** `tools/data/corpus/parsed-data/npc_spawns.json` — spawn coordinates
- **Corpus:** `tools/data/corpus/parsed-data/shops.json` — shop definitions
- **Corpus:** `tools/data/corpus/parsed-data/item_by_name.json` — item IDs
- **osrs-mcp:** `./gradlew :tools:osrs-mcp:runMcp` — cache symbol lookups

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
  handler_exists: true/false
  notes: "dialogue complexity, quest gating, etc."
```

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
